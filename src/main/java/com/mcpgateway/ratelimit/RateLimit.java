package com.mcpgateway.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * Annotation for declarative rate limiting on controller methods
 *
 * Usage:
 * <pre>
 * @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
 * public ResponseEntity<?> myEndpoint() { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of requests allowed in the window
     */
    long limit() default 100;

    /**
     * Time window value
     */
    long window() default 60;

    /**
     * Time window unit
     */
    ChronoUnit windowUnit() default ChronoUnit.SECONDS;

    /**
     * Rate limit strategy
     */
    RateLimitStrategy strategy() default RateLimitStrategy.SLIDING_WINDOW;

    /**
     * Key type for rate limiting
     * - "user": Per user
     * - "ip": Per IP address
     * - "user:tool": Per user per tool
     * - "subscription": Per subscription
     * - "global": Global limit
     * - Custom expression using SpEL
     */
    String key() default "user";

    /**
     * Cost per request (for token bucket)
     */
    int cost() default 1;

    /**
     * Whether to include rate limit headers in response
     */
    boolean includeHeaders() default true;

    /**
     * Custom error message when rate limit is exceeded
     */
    String errorMessage() default "Rate limit exceeded";

    /**
     * HTTP status code for rate limit exceeded
     */
    int errorStatusCode() default 429;
}
