package com.mcpgateway.aspect;

import com.mcpgateway.annotation.AilurosAudit;
import com.mcpgateway.service.ailuros.AilurosAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Ailuros Control: Audit Aspect
 *
 * AOP aspect that automatically audits methods annotated with @AilurosAudit.
 * Captures request/response, calculates latency, and logs to the audit database.
 *
 * This aspect is designed to work with LLM API wrapper methods that:
 * - Accept request parameters (prompt, model, etc.)
 * - Return response objects (with text, token counts, etc.)
 *
 * For maximum flexibility, the aspect extracts data using reflection
 * and common field names (prompt, response, tokens, etc.)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AilurosAuditAspect {

    private final AilurosAuditService auditService;

    @Around("@annotation(ailurosAudit)")
    public Object auditLlmCall(ProceedingJoinPoint joinPoint, AilurosAudit ailurosAudit) throws Throwable {
        // Skip if disabled
        if (!ailurosAudit.enabled()) {
            return joinPoint.proceed();
        }

        // Start audit tracking
        AilurosAuditService.AuditRequestBuilder audit = auditService.startAudit()
            .provider(ailurosAudit.provider())
            .model(ailurosAudit.model())
            .projectKey(ailurosAudit.projectKey())
            .env(ailurosAudit.env());

        // Extract request data
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            Object request = args[0];
            extractRequestData(request, audit);
        }

        try {
            // Execute the method
            Object result = joinPoint.proceed();

            // Extract response data
            extractResponseData(result, audit);

            // Complete audit
            audit.status("ok").complete();

            return result;

        } catch (Exception e) {
            // Log error
            audit.completeWithError(e.getMessage());
            throw e;
        }
    }

    /**
     * Extract request data from the request object
     * Uses reflection to find common fields
     */
    private void extractRequestData(Object request, AilurosAuditService.AuditRequestBuilder audit) {
        try {
            if (request instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) request;
                extractFromMap(map, audit, true);
            } else {
                extractFromObject(request, audit, true);
            }
        } catch (Exception e) {
            log.warn("Failed to extract request data", e);
        }
    }

    /**
     * Extract response data from the response object
     */
    private void extractResponseData(Object response, AilurosAuditService.AuditRequestBuilder audit) {
        try {
            if (response instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) response;
                extractFromMap(map, audit, false);
            } else {
                extractFromObject(response, audit, false);
            }
        } catch (Exception e) {
            log.warn("Failed to extract response data", e);
        }
    }

    /**
     * Extract data from a Map
     */
    private void extractFromMap(Map<?, ?> map, AilurosAuditService.AuditRequestBuilder audit, boolean isRequest) {
        if (isRequest) {
            // Request fields
            extractString(map, "prompt", audit::requestText);
            extractString(map, "input", audit::requestText);
            extractString(map, "messages", value -> audit.requestText(value.toString()));
            extractString(map, "model", audit::model);
            extractBigDecimal(map, "temperature", audit::temperature);
            extractBigDecimal(map, "top_p", audit::topP);
        } else {
            // Response fields
            extractString(map, "text", audit::responseText);
            extractString(map, "content", audit::responseText);
            extractString(map, "response", audit::responseText);

            // Token counts
            Object usage = map.get("usage");
            if (usage instanceof Map) {
                Map<?, ?> usageMap = (Map<?, ?>) usage;
                Integer promptTokens = extractInteger(usageMap, "prompt_tokens");
                Integer completionTokens = extractInteger(usageMap, "completion_tokens");
                if (promptTokens != null && completionTokens != null) {
                    audit.tokens(promptTokens, completionTokens);
                }
            }

            // Request ID
            extractString(map, "id", audit::upstreamRequestId);
            extractString(map, "request_id", audit::upstreamRequestId);
        }
    }

    /**
     * Extract data from an object using reflection
     */
    private void extractFromObject(Object obj, AilurosAuditService.AuditRequestBuilder audit, boolean isRequest) {
        Class<?> clazz = obj.getClass();

        try {
            if (isRequest) {
                // Request fields
                tryGetField(clazz, obj, "prompt", String.class, audit::requestText);
                tryGetField(clazz, obj, "input", String.class, audit::requestText);
                tryGetField(clazz, obj, "model", String.class, audit::model);
                tryGetField(clazz, obj, "temperature", BigDecimal.class, audit::temperature);
                tryGetField(clazz, obj, "topP", BigDecimal.class, audit::topP);
            } else {
                // Response fields
                tryGetField(clazz, obj, "text", String.class, audit::responseText);
                tryGetField(clazz, obj, "content", String.class, audit::responseText);
                tryGetField(clazz, obj, "response", String.class, audit::responseText);

                // Token counts
                Integer promptTokens = tryGetField(clazz, obj, "promptTokens", Integer.class, null);
                Integer completionTokens = tryGetField(clazz, obj, "completionTokens", Integer.class, null);
                if (promptTokens != null && completionTokens != null) {
                    audit.tokens(promptTokens, completionTokens);
                }

                // Request ID
                tryGetField(clazz, obj, "requestId", String.class, audit::upstreamRequestId);
            }
        } catch (Exception e) {
            log.warn("Failed to extract fields from object: {}", clazz.getSimpleName(), e);
        }
    }

    // Helper methods for extraction
    private void extractString(Map<?, ?> map, String key, java.util.function.Consumer<String> setter) {
        Object value = map.get(key);
        if (value != null) {
            setter.accept(value.toString());
        }
    }

    private void extractBigDecimal(Map<?, ?> map, String key, java.util.function.Consumer<BigDecimal> setter) {
        Object value = map.get(key);
        if (value instanceof Number) {
            setter.accept(new BigDecimal(value.toString()));
        }
    }

    private Integer extractInteger(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private <T> T tryGetField(Class<?> clazz, Object obj, String fieldName, Class<T> fieldType,
                               java.util.function.Consumer<T> setter) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value != null && fieldType.isInstance(value)) {
                T typedValue = fieldType.cast(value);
                if (setter != null) {
                    setter.accept(typedValue);
                }
                return typedValue;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Field doesn't exist or can't be accessed
        }
        return null;
    }
}
