package com.mcpgateway.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AOP Aspect for automatic rate limiting based on @RateLimit annotation
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;
    private final RedisRateLimiter redisRateLimiter;

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // Get current request and response
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // No HTTP context, skip rate limiting
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        // Build rate limit key
        String rateLimitKey = buildRateLimitKey(rateLimit.key(), request);

        // Apply rate limiting
        Duration window = Duration.of(rateLimit.window(), rateLimit.windowUnit());
        RateLimitResult result = applyRateLimit(
                rateLimitKey,
                rateLimit.limit(),
                window,
                rateLimit.strategy(),
                rateLimit.cost()
        );

        // Add rate limit headers if configured
        if (rateLimit.includeHeaders() && response != null) {
            addRateLimitHeaders(response, result);
        }

        // Check if request is allowed
        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded for key: {}, limit: {}, window: {}",
                    rateLimitKey, rateLimit.limit(), window);
            throw new RateLimitExceededException(
                    rateLimit.errorMessage(),
                    result.getLimit(),
                    result.getResetTime(),
                    result.getRetryAfterSeconds(),
                    result.getAppliedRule()
            );
        }

        // Proceed with the method execution
        return joinPoint.proceed();
    }

    /**
     * Build rate limit key based on key type and request context
     */
    private String buildRateLimitKey(String keyType, HttpServletRequest request) {
        Map<String, String> context = new HashMap<>();

        // Get user ID from security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.mcpgateway.domain.User) {
                UUID userId = ((com.mcpgateway.domain.User) principal).getId();
                context.put("userId", userId.toString());
            }
        }

        // Get IP address
        String ipAddress = getClientIpAddress(request);
        context.put("ip", ipAddress);

        // Parse key type and build key
        return switch (keyType) {
            case "user" -> context.getOrDefault("userId", "anonymous");
            case "ip" -> ipAddress;
            case "global" -> "global";
            case "user:tool" -> {
                String userId = context.getOrDefault("userId", "anonymous");
                String toolId = extractToolIdFromPath(request);
                yield userId + ":tool:" + toolId;
            }
            default -> {
                // Custom key - interpolate with context
                String result = keyType;
                for (Map.Entry<String, String> entry : context.entrySet()) {
                    result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                yield result;
            }
        };
    }

    /**
     * Apply rate limiting based on strategy
     */
    private RateLimitResult applyRateLimit(String key, long limit, Duration window,
                                           RateLimitStrategy strategy, int cost) {
        return switch (strategy) {
            case SLIDING_WINDOW -> redisRateLimiter.checkSlidingWindow(key, limit, window, cost);
            case TOKEN_BUCKET -> {
                double refillRate = (double) limit / window.getSeconds();
                yield redisRateLimiter.checkTokenBucket(key, limit, refillRate, cost);
            }
            case FIXED_WINDOW -> redisRateLimiter.checkFixedWindow(key, limit, window, cost);
            case LEAKY_BUCKET -> redisRateLimiter.checkSlidingWindow(key, limit, window, cost);
        };
    }

    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime().getEpochSecond()));

        if (!result.isAllowed() && result.getRetryAfterSeconds() != null) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        }
    }

    /**
     * Extract tool ID from request path
     */
    private String extractToolIdFromPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Try to extract UUID from path
        String[] parts = path.split("/");
        for (String part : parts) {
            try {
                UUID.fromString(part);
                return part;
            } catch (IllegalArgumentException e) {
                // Not a UUID, continue
            }
        }
        return "unknown";
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
