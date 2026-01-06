package com.mcpgateway.ratelimit;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Result of a rate limit check
 */
@Data
@Builder
public class RateLimitResult {

    /**
     * Whether the request is allowed
     */
    private boolean allowed;

    /**
     * Total limit for this time window
     */
    private long limit;

    /**
     * Remaining quota
     */
    private long remaining;

    /**
     * When the rate limit will reset
     */
    private Instant resetTime;

    /**
     * Time to wait before retry (in seconds)
     */
    private Long retryAfterSeconds;

    /**
     * Rule that was applied
     */
    private String appliedRule;

    /**
     * Current usage count
     */
    private long currentUsage;

    /**
     * Create a result for allowed request
     */
    public static RateLimitResult allowed(long limit, long remaining, Instant resetTime, String rule) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(remaining)
                .resetTime(resetTime)
                .appliedRule(rule)
                .currentUsage(limit - remaining)
                .build();
    }

    /**
     * Create a result for rejected request
     */
    public static RateLimitResult rejected(long limit, Instant resetTime, long retryAfter, String rule) {
        return RateLimitResult.builder()
                .allowed(false)
                .limit(limit)
                .remaining(0)
                .resetTime(resetTime)
                .retryAfterSeconds(retryAfter)
                .appliedRule(rule)
                .currentUsage(limit)
                .build();
    }
}
