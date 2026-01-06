package com.mcpgateway.ratelimit;

import lombok.Getter;

import java.time.Instant;

/**
 * Exception thrown when rate limit is exceeded
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long limit;
    private final Instant resetTime;
    private final Long retryAfterSeconds;
    private final String appliedRule;

    public RateLimitExceededException(RateLimitResult result) {
        super(String.format("Rate limit exceeded. Limit: %d, Reset at: %s, Retry after: %d seconds",
                result.getLimit(), result.getResetTime(), result.getRetryAfterSeconds()));
        this.limit = result.getLimit();
        this.resetTime = result.getResetTime();
        this.retryAfterSeconds = result.getRetryAfterSeconds();
        this.appliedRule = result.getAppliedRule();
    }

    public RateLimitExceededException(String message, long limit, Instant resetTime, Long retryAfter, String rule) {
        super(message);
        this.limit = limit;
        this.resetTime = resetTime;
        this.retryAfterSeconds = retryAfter;
        this.appliedRule = rule;
    }
}
