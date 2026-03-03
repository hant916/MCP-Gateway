package com.mcpgateway.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.annotation.AuditLog;
import com.mcpgateway.domain.User;
import com.mcpgateway.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AOP Aspect for Audit Logging
 *
 * Automatically logs method executions annotated with @AuditLog
 *
 * Captures:
 * - User context
 * - HTTP request details
 * - Method parameters (if enabled)
 * - Execution time
 * - Success/failure status
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object logAuditEvent(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // Extract user context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = null;
        String username = "anonymous";

        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            userId = user.getId();
            username = user.getUsername();
        } else if (authentication != null && authentication.isAuthenticated()) {
            username = authentication.getName();
        }

        // Extract HTTP context
        HttpServletRequest request = getHttpRequest();
        String httpMethod = request != null ? request.getMethod() : null;
        String endpoint = request != null ? request.getRequestURI() : null;
        String ipAddress = request != null ? getClientIpAddress(request) : null;
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        // Extract resource ID from method parameters or result
        String resourceId = null;
        Map<String, Object> metadata = new HashMap<>();

        // Include parameters if enabled
        if (auditLog.includeParams()) {
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                String[] paramNames = signature.getParameterNames();
                Object[] paramValues = joinPoint.getArgs();

                for (int i = 0; i < paramNames.length; i++) {
                    if (paramValues[i] != null) {
                        // Try to extract UUID as resource ID
                        if (paramValues[i] instanceof UUID && resourceId == null) {
                            resourceId = paramValues[i].toString();
                        }

                        // Add to metadata
                        if (isPrimitiveOrWrapper(paramValues[i].getClass()) || paramValues[i] instanceof String) {
                            metadata.put(paramNames[i], paramValues[i]);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to capture method parameters", e);
            }
        }

        Object result = null;
        com.mcpgateway.domain.AuditLog.Status status;
        String errorMessage = null;

        try {
            // Execute the method
            result = joinPoint.proceed();
            status = com.mcpgateway.domain.AuditLog.Status.SUCCESS;

            // Extract resource ID from result if not found in parameters
            if (resourceId == null && result != null) {
                try {
                    if (result instanceof UUID) {
                        resourceId = result.toString();
                    } else if (hasField(result, "id")) {
                        Object idValue = getField(result, "id");
                        if (idValue != null) {
                            resourceId = idValue.toString();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not extract resource ID from result", e);
                }
            }

            // Include response if enabled
            if (auditLog.includeResponse() && result != null) {
                try {
                    if (isPrimitiveOrWrapper(result.getClass()) || result instanceof String) {
                        metadata.put("response", result);
                    } else {
                        metadata.put("responseType", result.getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to capture response", e);
                }
            }

        } catch (Exception e) {
            status = com.mcpgateway.domain.AuditLog.Status.FAILURE;
            errorMessage = e.getMessage();
            throw e;

        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log the audit event asynchronously
            try {
                auditLogService.logWithHttpContext(
                        userId,
                        username,
                        auditLog.action(),
                        auditLog.resource(),
                        resourceId,
                        httpMethod,
                        endpoint,
                        ipAddress,
                        userAgent,
                        status,
                        errorMessage,
                        executionTime,
                        metadata.isEmpty() ? null : metadata
                );
            } catch (Exception e) {
                log.error("Failed to log audit event", e);
            }
        }

        return result;
    }

    private HttpServletRequest getHttpRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Boolean.class ||
                type == Byte.class ||
                type == Character.class ||
                type == Short.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Float.class ||
                type == Double.class;
    }

    private boolean hasField(Object obj, String fieldName) {
        try {
            obj.getClass().getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private Object getField(Object obj, String fieldName) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
}
