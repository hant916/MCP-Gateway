package com.mcpgateway.service.ailuros.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.dto.ailuros.CallEventV1DTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.mcpgateway.filter.AilurosTraceIdFilter.TRACE_ID_ATTRIBUTE;
import static com.mcpgateway.filter.AilurosTraceIdFilter.TRACE_ID_HEADER;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallEventBuilderService {

    public static final String EVENT_VERSION = "call_event.v1";

    private final ObjectMapper objectMapper;

    public GatewayCallContext startCall(String defaultRoute,
                                        String provider,
                                        String model,
                                        boolean streaming,
                                        Map<String, Object> metadata) {
        HttpServletRequest request = currentRequest();

        String traceId = firstNonBlank(
            header(request, TRACE_ID_HEADER),
            requestAttr(request, TRACE_ID_ATTRIBUTE),
            randomId());

        String spanId = randomId();
        String appId = firstNonBlank(header(request, "X-Ailuros-App"), "unknown");
        String env = firstNonBlank(header(request, "X-Ailuros-Env"), "unknown");
        String route = firstNonBlank(
            header(request, "X-Ailuros-Route"),
            defaultRoute,
            request != null ? request.getRequestURI() : null,
            "unknown");

        String promptVersion = firstNonBlank(header(request, "X-Ailuros-Prompt-Version"), "unknown");
        String userTier = firstNonBlank(header(request, "X-Ailuros-User-Tier"), "unknown");
        String promptHash = firstNonBlank(
            header(request, "X-Ailuros-Prompt-Hash"),
            hashText(promptVersion));

        Map<String, Object> metadataMap = new LinkedHashMap<>();
        if (metadata != null) {
            metadataMap.putAll(metadata);
        }

        String requestId = header(request, "X-Ailuros-Request-Id");
        if (requestId != null) {
            metadataMap.put("request_id", requestId);
        }
        String releaseCandidate = header(request, "X-Ailuros-Release-Candidate");
        if (releaseCandidate != null) {
            metadataMap.put("release_candidate", parseBoolean(releaseCandidate));
        }
        if (request != null) {
            metadataMap.putIfAbsent("method", request.getMethod());
            metadataMap.putIfAbsent("path", request.getRequestURI());
        }

        return GatewayCallContext.builder()
            .eventVersion(EVENT_VERSION)
            .traceId(traceId)
            .spanId(spanId)
            .appId(appId)
            .env(env)
            .route(route)
            .provider(firstNonBlank(provider, "unknown"))
            .model(firstNonBlank(model, "unknown"))
            .promptVersion(promptVersion)
            .userTier(userTier)
            .streaming(streaming)
            .requestTs(Instant.now())
            .promptHash(promptHash)
            .metadata(metadataMap)
            .build();
    }

    public CallEventV1DTO buildSuccessEvent(GatewayCallContext context,
                                            int httpStatus,
                                            Integer inputTokens,
                                            Integer outputTokens,
                                            BigDecimal costUsd,
                                            List<String> flags,
                                            Map<String, Object> extraMetadata) {
        Instant responseTs = Instant.now();
        int latencyMs = latency(context.getRequestTs(), responseTs);

        List<String> normalizedFlags = mergeFlags(flags, deriveStatusFlags(httpStatus, null));

        return buildEvent(context,
            responseTs,
            latencyMs,
            inputTokens,
            outputTokens,
            costUsd,
            (httpStatus >= 200 && httpStatus < 400) ? "ok" : "error",
            null,
            httpStatus,
            normalizedFlags,
            extraMetadata);
    }

    public CallEventV1DTO buildErrorEvent(GatewayCallContext context,
                                          Integer httpStatus,
                                          String errorType,
                                          String errorMessage,
                                          List<String> flags,
                                          Map<String, Object> extraMetadata) {
        Instant responseTs = Instant.now();
        int latencyMs = latency(context.getRequestTs(), responseTs);

        List<String> normalizedFlags = new ArrayList<>();
        normalizedFlags.addAll(deriveStatusFlags(httpStatus, errorType));
        normalizedFlags.addAll(flags != null ? flags : List.of());

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("error_message", truncate(errorMessage, 400));
        }

        return buildEvent(context,
            responseTs,
            latencyMs,
            0,
            0,
            BigDecimal.ZERO,
            "error",
            firstNonBlank(errorType, "unknown_error"),
            httpStatus,
            normalizedFlags,
            metadata);
    }

    public ParsedUsage parseUsage(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ParsedUsage(0, 0, BigDecimal.ZERO);
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode usage = root.path("usage");

            int inputTokens = intOrDefault(
                usage.path("prompt_tokens"),
                usage.path("input_tokens"),
                root.path("input_tokens"));

            int outputTokens = intOrDefault(
                usage.path("completion_tokens"),
                usage.path("output_tokens"),
                root.path("output_tokens"));

            BigDecimal cost = decimalOrDefault(
                root.path("cost_usd"),
                usage.path("cost_usd"),
                BigDecimal.ZERO);

            return new ParsedUsage(inputTokens, outputTokens, cost);
        } catch (Exception ex) {
            return new ParsedUsage(0, 0, BigDecimal.ZERO);
        }
    }

    private CallEventV1DTO buildEvent(GatewayCallContext context,
                                      Instant responseTs,
                                      int latencyMs,
                                      Integer inputTokens,
                                      Integer outputTokens,
                                      BigDecimal costUsd,
                                      String status,
                                      String errorType,
                                      Integer httpStatus,
                                      List<String> flags,
                                      Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(context.getMetadata());
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        List<String> deduplicatedFlags = mergeFlags(flags, List.of());

        return CallEventV1DTO.builder()
            .eventVersion(context.getEventVersion())
            .identity(CallEventV1DTO.Identity.builder()
                .traceId(context.getTraceId())
                .spanId(context.getSpanId())
                .build())
            .dims(CallEventV1DTO.Dimensions.builder()
                .appId(context.getAppId())
                .env(context.getEnv())
                .route(context.getRoute())
                .provider(context.getProvider())
                .model(context.getModel())
                .promptVersion(context.getPromptVersion())
                .streaming(context.isStreaming())
                .userTier(context.getUserTier())
                .build())
            .timing(CallEventV1DTO.Timing.builder()
                .requestTs(context.getRequestTs())
                .responseTs(responseTs)
                .latencyMs(latencyMs)
                .build())
            .usage(CallEventV1DTO.Usage.builder()
                .inputTokens(inputTokens != null ? inputTokens : 0)
                .outputTokens(outputTokens != null ? outputTokens : 0)
                .costUsd(costUsd != null ? costUsd : BigDecimal.ZERO)
                .build())
            .outcome(CallEventV1DTO.Outcome.builder()
                .status(status)
                .errorType(errorType)
                .httpStatus(httpStatus)
                .build())
            .privacy(CallEventV1DTO.Privacy.builder()
                .promptHash(context.getPromptHash())
                .build())
            .flags(deduplicatedFlags)
            .metadata(metadata)
            .build();
    }

    private List<String> deriveStatusFlags(Integer httpStatus, String errorType) {
        List<String> flags = new ArrayList<>();

        if (httpStatus != null) {
            if (httpStatus >= 500) {
                flags.add("provider_error");
            } else if (httpStatus >= 400) {
                flags.add("bad_request");
            }
        }

        if (errorType != null) {
            String normalized = errorType.toLowerCase(Locale.ROOT);
            if (normalized.contains("timeout")) {
                flags.add("timeout");
            }
            if (normalized.contains("abort") || normalized.contains("cancel") || normalized.contains("interrupt")) {
                flags.add("stream_interrupted");
            }
        }

        if (flags.isEmpty() && errorType != null) {
            flags.add(normalizeFlag(errorType));
        }

        return flags;
    }

    private List<String> mergeFlags(List<String> first, List<String> second) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(Objects::nonNull).map(this::normalizeFlag).forEach(out::add);
        }
        if (second != null) {
            second.stream().filter(Objects::nonNull).map(this::normalizeFlag).forEach(out::add);
        }
        return new ArrayList<>(out);
    }

    private String normalizeFlag(String flag) {
        return flag.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private int latency(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(start, end).toMillis());
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String header(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        String value = request.getHeader(name);
        if (value == null) {
            value = request.getHeader(name.toLowerCase(Locale.ROOT));
        }
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String requestAttr(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        return value != null ? value.toString() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String hashText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
            || "1".equals(normalized)
            || "yes".equals(normalized)
            || "y".equals(normalized)
            || "on".equals(normalized);
    }

    private int intOrDefault(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull() && node.canConvertToInt()) {
                return node.asInt();
            }
        }
        return 0;
    }

    private BigDecimal decimalOrDefault(JsonNode first, JsonNode second, BigDecimal defaultValue) {
        if (first != null && first.isNumber()) {
            return first.decimalValue();
        }
        if (second != null && second.isNumber()) {
            return second.decimalValue();
        }
        return defaultValue;
    }

    public record ParsedUsage(int inputTokens, int outputTokens, BigDecimal costUsd) {}
}
