package com.mcpgateway.ratelimit;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;

/**
 * Rate limit rule configuration
 *
 * Defines how rate limiting should be applied for a specific context
 */
@Data
@Builder
public class RateLimitRule {

    /**
     * Unique identifier for this rule
     */
    private String ruleId;

    /**
     * Maximum number of requests allowed
     */
    private long limit;

    /**
     * Time window for the limit
     */
    private Duration window;

    /**
     * Rate limiting strategy to use
     */
    @Builder.Default
    private RateLimitStrategy strategy = RateLimitStrategy.SLIDING_WINDOW;

    /**
     * Key template for Redis storage
     * Supports placeholders: {userId}, {toolId}, {subscriptionId}, {serverId}
     */
    private String keyTemplate;

    /**
     * Priority of this rule (higher priority checked first)
     */
    @Builder.Default
    private int priority = 0;

    /**
     * Conditions that must be met for this rule to apply
     */
    private List<RateLimitCondition> conditions;

    /**
     * Cost per request (for token bucket strategy)
     * Default is 1, but expensive operations can cost more
     */
    @Builder.Default
    private int costPerRequest = 1;

    /**
     * Whether to include rate limit headers in response
     */
    @Builder.Default
    private boolean includeHeaders = true;

    /**
     * Custom error message when limit is exceeded
     */
    private String errorMessage;

    /**
     * HTTP status code to return when limit is exceeded
     */
    @Builder.Default
    private int errorStatusCode = 429;

    /**
     * Condition for rate limit rule application
     */
    @Data
    @Builder
    public static class RateLimitCondition {
        private String field;
        private String operator;  // equals, greater_than, less_than, contains
        private Object value;
    }
}
