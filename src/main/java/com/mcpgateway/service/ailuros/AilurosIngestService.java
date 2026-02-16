package com.mcpgateway.service.ailuros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcCallFlag;
import com.mcpgateway.dto.ailuros.CallEventV1DTO;
import com.mcpgateway.repository.ailuros.AcCallFlagRepository;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosIngestService {

    private final AcCallRepository callRepository;
    private final AcCallFlagRepository flagRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AcCall ingest(CallEventV1DTO event) {
        if (event == null) {
            throw new IllegalArgumentException("event payload cannot be null");
        }

        String appId = defaultValue(event.getDims() != null ? event.getDims().getAppId() : null, "unknown");
        String env = defaultValue(event.getDims() != null ? event.getDims().getEnv() : null, "unknown");
        String provider = defaultValue(event.getDims() != null ? event.getDims().getProvider() : null, "unknown");
        String model = defaultValue(event.getDims() != null ? event.getDims().getModel() : null, "unknown");

        Instant requestTs = event.getTiming() != null ? event.getTiming().getRequestTs() : null;
        Instant responseTs = event.getTiming() != null ? event.getTiming().getResponseTs() : null;
        if (requestTs == null) {
            requestTs = Instant.now();
        }
        if (responseTs == null) {
            responseTs = requestTs;
        }

        Integer latencyMs = event.getTiming() != null ? event.getTiming().getLatencyMs() : null;
        if (latencyMs == null) {
            latencyMs = Math.max(0, (int) Duration.between(requestTs, responseTs).toMillis());
        }

        int inputTokens = event.getUsage() != null && event.getUsage().getInputTokens() != null
            ? event.getUsage().getInputTokens() : 0;
        int outputTokens = event.getUsage() != null && event.getUsage().getOutputTokens() != null
            ? event.getUsage().getOutputTokens() : 0;

        BigDecimal costUsd = event.getUsage() != null && event.getUsage().getCostUsd() != null
            ? event.getUsage().getCostUsd() : BigDecimal.ZERO;

        String traceId = defaultValue(
            event.getIdentity() != null ? event.getIdentity().getTraceId() : null,
            UUID.randomUUID().toString().replace("-", ""));

        String spanId = defaultValue(
            event.getIdentity() != null ? event.getIdentity().getSpanId() : null,
            UUID.randomUUID().toString().replace("-", ""));

        traceId = ensureUniqueTraceId(traceId, spanId);

        AcCall call = AcCall.builder()
            .traceId(traceId)
            .spanId(spanId)
            .projectKey(appId)
            .appId(appId)
            .env(env)
            .route(defaultValue(event.getDims() != null ? event.getDims().getRoute() : null, "unknown"))
            .status(normalizeStatus(event))
            .provider(provider)
            .model(model)
            .promptRef(defaultValue(event.getDims() != null ? event.getDims().getPromptVersion() : null, "unknown"))
            .promptVersion(defaultValue(event.getDims() != null ? event.getDims().getPromptVersion() : null, "unknown"))
            .tokensPrompt(inputTokens)
            .tokensCompletion(outputTokens)
            .tokensTotal(inputTokens + outputTokens)
            .costEstimateUsd(costUsd)
            .latencyMs(latencyMs)
            .streaming(event.getDims() != null ? Boolean.TRUE.equals(event.getDims().getStreaming()) : Boolean.FALSE)
            .requestTs(requestTs)
            .responseTs(responseTs)
            .errorType(event.getOutcome() != null ? event.getOutcome().getErrorType() : null)
            .httpStatus(event.getOutcome() != null ? event.getOutcome().getHttpStatus() : null)
            .eventVersion(defaultValue(event.getEventVersion(), "call_event.v1"))
            .promptHash(event.getPrivacy() != null ? event.getPrivacy().getPromptHash() : null)
            .releaseCandidate(extractReleaseCandidate(event.getMetadata()))
            .requestSha256(event.getPrivacy() != null ? event.getPrivacy().getPromptHash() : null)
            .userTier(event.getDims() != null ? event.getDims().getUserTier() : null)
            .upstreamRequestId(spanId)
            .metadataJson(toJson(event.getMetadata()))
            .createdAt(requestTs)
            .build();

        call = callRepository.save(call);

        Set<String> flags = normalizeFlags(event, call.getStatus());
        if (!flags.isEmpty()) {
            for (String flag : flags) {
                AcCallFlag callFlag = AcCallFlag.builder()
                    .call(call)
                    .flagType(flag)
                    .note("auto_ingest")
                    .createdBy("ingest/v1")
                    .build();
                flagRepository.save(callFlag);
            }
        }

        return call;
    }

    private Set<String> normalizeFlags(CallEventV1DTO event, String status) {
        Set<String> flags = new LinkedHashSet<>();
        if (event.getFlags() != null) {
            for (String flag : event.getFlags()) {
                if (flag != null && !flag.isBlank()) {
                    flags.add(flag.trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        if ("error".equalsIgnoreCase(status)) {
            flags.add("ERROR");
        }

        return flags;
    }

    private String normalizeStatus(CallEventV1DTO event) {
        String status = event.getOutcome() != null ? event.getOutcome().getStatus() : null;
        if (status != null && !status.isBlank()) {
            return status.toLowerCase(Locale.ROOT);
        }

        Integer httpStatus = event.getOutcome() != null ? event.getOutcome().getHttpStatus() : null;
        if (httpStatus != null) {
            return (httpStatus >= 200 && httpStatus < 400) ? "ok" : "error";
        }

        return "ok";
    }

    private String ensureUniqueTraceId(String traceId, String spanId) {
        if (callRepository.findByTraceId(traceId).isEmpty()) {
            return traceId;
        }

        String suffix = spanId.length() >= 8 ? spanId.substring(0, 8) : spanId;
        String candidate = traceId + "-" + suffix;
        if (callRepository.findByTraceId(candidate).isEmpty()) {
            return candidate;
        }

        return candidate + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return null;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String defaultValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private Boolean extractReleaseCandidate(Map<String, Object> metadata) {
        if (metadata == null) {
            return Boolean.FALSE;
        }
        Object value = metadata.get("release_candidate");
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return "true".equals(text)
            || "1".equals(text)
            || "yes".equals(text)
            || "y".equals(text)
            || "on".equals(text);
    }
}
