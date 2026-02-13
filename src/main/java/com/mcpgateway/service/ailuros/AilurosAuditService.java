package com.mcpgateway.service.ailuros;

import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static com.mcpgateway.filter.AilurosTraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * Ailuros Control: Audit Service
 *
 * Core service for capturing and persisting LLM call audit records.
 * This service can be called from:
 * - Controllers (via explicit calls)
 * - Aspects (via AOP interception)
 * - Filters (via request/response wrappers)
 * - Services (via programmatic logging)
 *
 * Design principles:
 * - Async by default to minimize impact on request latency
 * - Defensive coding to prevent audit failures from breaking the main flow
 * - Configurable text truncation for PII/cost management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosAuditService {

    private final AcCallRepository callRepository;
    private final CostEstimator costEstimator;

    // Configuration - in v0.2 move to application.properties
    private static final int MAX_TEXT_LENGTH = 50_000;
    private static final boolean ASYNC_ENABLED = true;

    /**
     * Audit an LLM call
     * This is the main entry point for logging calls
     */
    @Async
    @Transactional
    public void auditCall(AuditRequest request) {
        try {
            log.debug("Auditing call: traceId={}, model={}", request.traceId, request.model);

            // Build call record
            AcCall call = AcCall.builder()
                .traceId(request.traceId)
                .projectKey(request.projectKey != null ? request.projectKey : "default")
                .env(request.env != null ? request.env : "prod")
                .status(request.status != null ? request.status : "ok")
                .provider(request.provider)
                .model(request.model)
                .temperature(request.temperature)
                .topP(request.topP)
                .promptRef(request.promptRef != null ? request.promptRef : "adhoc")
                .requestText(truncateText(request.requestText))
                .requestSha256(hashText(request.requestText))
                .responseText(truncateText(request.responseText))
                .responseSha256(hashText(request.responseText))
                .tokensPrompt(request.tokensPrompt)
                .tokensCompletion(request.tokensCompletion)
                .tokensTotal(request.tokensTotal)
                .latencyMs(request.latencyMs)
                .upstreamRequestId(request.upstreamRequestId)
                .build();

            // Calculate cost
            if (request.tokensPrompt != null && request.tokensCompletion != null) {
                BigDecimal cost = costEstimator.estimateCost(
                    request.model,
                    request.tokensPrompt,
                    request.tokensCompletion
                );
                call.setCostEstimateUsd(cost);
            }

            // Persist
            callRepository.save(call);

            log.info("Audited call: traceId={}, model={}, tokens={}, cost=${}",
                    call.getTraceId(),
                    call.getModel(),
                    call.getTokensTotal(),
                    call.getCostEstimateUsd());

        } catch (Exception e) {
            // Never fail the main request due to audit errors
            log.error("Failed to audit call: traceId={}, error={}",
                     request.traceId, e.getMessage(), e);
        }
    }

    /**
     * Begin tracking a call
     * Returns a builder for fluent API
     */
    public AuditRequestBuilder startAudit() {
        String traceId = extractTraceId();
        return new AuditRequestBuilder(this, traceId);
    }

    /**
     * Extract trace ID from current request context
     */
    private String extractTraceId() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
                if (traceId != null) {
                    return traceId.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract trace ID from request context", e);
        }

        // Fallback: generate new trace ID
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Truncate text to prevent database bloat
     */
    private String truncateText(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "... [truncated]";
    }

    /**
     * Calculate SHA-256 hash of text
     */
    private String hashText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return null;
        }
    }

    /**
     * Audit request data structure
     */
    public static class AuditRequest {
        public String traceId;
        public String projectKey;
        public String env;
        public String status;
        public String provider;
        public String model;
        public BigDecimal temperature;
        public BigDecimal topP;
        public String promptRef;
        public String requestText;
        public String responseText;
        public Integer tokensPrompt;
        public Integer tokensCompletion;
        public Integer tokensTotal;
        public Integer latencyMs;
        public String upstreamRequestId;
    }

    /**
     * Fluent builder for audit requests
     */
    public static class AuditRequestBuilder {
        private final AilurosAuditService service;
        private final AuditRequest request;
        private final Instant startTime;

        AuditRequestBuilder(AilurosAuditService service, String traceId) {
            this.service = service;
            this.request = new AuditRequest();
            this.request.traceId = traceId;
            this.startTime = Instant.now();
        }

        public AuditRequestBuilder projectKey(String projectKey) {
            request.projectKey = projectKey;
            return this;
        }

        public AuditRequestBuilder env(String env) {
            request.env = env;
            return this;
        }

        public AuditRequestBuilder status(String status) {
            request.status = status;
            return this;
        }

        public AuditRequestBuilder provider(String provider) {
            request.provider = provider;
            return this;
        }

        public AuditRequestBuilder model(String model) {
            request.model = model;
            return this;
        }

        public AuditRequestBuilder temperature(BigDecimal temperature) {
            request.temperature = temperature;
            return this;
        }

        public AuditRequestBuilder topP(BigDecimal topP) {
            request.topP = topP;
            return this;
        }

        public AuditRequestBuilder promptRef(String promptRef) {
            request.promptRef = promptRef;
            return this;
        }

        public AuditRequestBuilder requestText(String requestText) {
            request.requestText = requestText;
            return this;
        }

        public AuditRequestBuilder responseText(String responseText) {
            request.responseText = responseText;
            return this;
        }

        public AuditRequestBuilder tokens(int prompt, int completion) {
            request.tokensPrompt = prompt;
            request.tokensCompletion = completion;
            request.tokensTotal = prompt + completion;
            return this;
        }

        public AuditRequestBuilder upstreamRequestId(String requestId) {
            request.upstreamRequestId = requestId;
            return this;
        }

        /**
         * Complete the audit with automatic latency calculation
         */
        public void complete() {
            // Calculate latency
            long latencyMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            request.latencyMs = (int) latencyMs;

            // Submit audit
            service.auditCall(request);
        }

        /**
         * Complete the audit with error status
         */
        public void completeWithError(String errorMessage) {
            request.status = "error";
            request.responseText = errorMessage;
            complete();
        }
    }
}
