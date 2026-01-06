package com.mcpgateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Redis-based distributed rate limiter
 *
 * Implements multiple rate limiting algorithms using Redis for distributed coordination:
 * - Sliding window: Accurate rate limiting across distributed instances
 * - Token bucket: Allow burst traffic with token refill
 * - Fixed window: Simple counter-based limiting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Lua script for sliding window rate limiting
     * Uses sorted sets with timestamps as scores for accurate sliding window
     */
    private static final String SLIDING_WINDOW_SCRIPT =
            """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])

            -- Remove old entries outside the window
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

            -- Count current entries in window
            local current = redis.call('ZCARD', key)

            if current + cost <= limit then
                -- Add new entry with current timestamp
                redis.call('ZADD', key, now, now)
                redis.call('EXPIRE', key, window)
                return {1, limit - current - cost, now + window}
            else
                -- Rate limit exceeded
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local resetTime = tonumber(oldest[2]) + window
                return {0, 0, resetTime}
            end
            """;

    /**
     * Lua script for token bucket rate limiting
     * Tokens refill at constant rate, consumed per request
     */
    private static final String TOKEN_BUCKET_SCRIPT =
            """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refillRate = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])

            -- Get current state
            local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens = tonumber(bucket[1] or capacity)
            local lastRefill = tonumber(bucket[2] or now)

            -- Calculate tokens to add based on time elapsed
            local elapsed = now - lastRefill
            local tokensToAdd = math.floor(elapsed * refillRate)
            tokens = math.min(capacity, tokens + tokensToAdd)

            if tokens >= cost then
                -- Consume tokens
                tokens = tokens - cost
                redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
                redis.call('EXPIRE', key, 3600)
                return {1, tokens, now + math.ceil((capacity - tokens) / refillRate)}
            else
                -- Not enough tokens
                local waitTime = math.ceil((cost - tokens) / refillRate)
                return {0, tokens, now + waitTime}
            end
            """;

    /**
     * Lua script for fixed window rate limiting
     * Simple counter that resets at fixed intervals
     */
    private static final String FIXED_WINDOW_SCRIPT =
            """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local cost = tonumber(ARGV[3])

            local current = tonumber(redis.call('GET', key) or '0')

            if current + cost <= limit then
                local newCount = redis.call('INCRBY', key, cost)
                if newCount == cost then
                    redis.call('EXPIRE', key, window)
                end
                local ttl = redis.call('TTL', key)
                return {1, limit - newCount, ttl}
            else
                local ttl = redis.call('TTL', key)
                return {0, 0, ttl}
            end
            """;

    /**
     * Check rate limit using sliding window algorithm
     *
     * @param key Redis key for this limit
     * @param limit Maximum requests allowed
     * @param window Time window in seconds
     * @param cost Cost of this request (default 1)
     * @return Rate limit check result
     */
    public RateLimitResult checkSlidingWindow(String key, long limit, Duration window, int cost) {
        long now = Instant.now().toEpochMilli();
        long windowMillis = window.toMillis();

        try {
            RedisScript<List> script = RedisScript.of(SLIDING_WINDOW_SCRIPT, List.class);
            List<Long> result = redisTemplate.execute(
                    script,
                    Arrays.asList(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit),
                    String.valueOf(cost)
            );

            boolean allowed = result.get(0) == 1;
            long remaining = result.get(1);
            long resetTimeMillis = result.get(2);

            Instant resetTime = Instant.ofEpochMilli(resetTimeMillis);

            if (allowed) {
                return RateLimitResult.allowed(limit, remaining, resetTime, "sliding-window");
            } else {
                long retryAfter = (resetTimeMillis - now) / 1000;
                return RateLimitResult.rejected(limit, resetTime, retryAfter, "sliding-window");
            }
        } catch (Exception e) {
            log.error("Error checking rate limit for key: {}", key, e);
            // Fail open - allow the request if Redis fails
            return RateLimitResult.allowed(limit, limit, Instant.now().plus(window), "error-fallback");
        }
    }

    /**
     * Check rate limit using token bucket algorithm
     *
     * @param key Redis key for this limit
     * @param capacity Maximum tokens (burst capacity)
     * @param refillRate Tokens added per second
     * @param cost Cost of this request
     * @return Rate limit check result
     */
    public RateLimitResult checkTokenBucket(String key, long capacity, double refillRate, int cost) {
        long now = Instant.now().getEpochSecond();

        try {
            RedisScript<List> script = RedisScript.of(TOKEN_BUCKET_SCRIPT, List.class);
            List<Long> result = redisTemplate.execute(
                    script,
                    Arrays.asList(key),
                    String.valueOf(now),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(cost)
            );

            boolean allowed = result.get(0) == 1;
            long remaining = result.get(1);
            long resetTime = result.get(2);

            Instant resetInstant = Instant.ofEpochSecond(resetTime);

            if (allowed) {
                return RateLimitResult.allowed(capacity, remaining, resetInstant, "token-bucket");
            } else {
                long retryAfter = resetTime - now;
                return RateLimitResult.rejected(capacity, resetInstant, retryAfter, "token-bucket");
            }
        } catch (Exception e) {
            log.error("Error checking token bucket for key: {}", key, e);
            return RateLimitResult.allowed(capacity, capacity, Instant.now().plusSeconds(60), "error-fallback");
        }
    }

    /**
     * Check rate limit using fixed window algorithm
     *
     * @param key Redis key for this limit
     * @param limit Maximum requests allowed
     * @param window Time window in seconds
     * @param cost Cost of this request
     * @return Rate limit check result
     */
    public RateLimitResult checkFixedWindow(String key, long limit, Duration window, int cost) {
        long windowSeconds = window.getSeconds();

        try {
            RedisScript<List> script = RedisScript.of(FIXED_WINDOW_SCRIPT, List.class);
            List<Long> result = redisTemplate.execute(
                    script,
                    Arrays.asList(key),
                    String.valueOf(windowSeconds),
                    String.valueOf(limit),
                    String.valueOf(cost)
            );

            boolean allowed = result.get(0) == 1;
            long remaining = result.get(1);
            long ttl = result.get(2);

            Instant resetTime = Instant.now().plusSeconds(ttl);

            if (allowed) {
                return RateLimitResult.allowed(limit, remaining, resetTime, "fixed-window");
            } else {
                return RateLimitResult.rejected(limit, resetTime, ttl, "fixed-window");
            }
        } catch (Exception e) {
            log.error("Error checking fixed window for key: {}", key, e);
            return RateLimitResult.allowed(limit, limit, Instant.now().plus(window), "error-fallback");
        }
    }

    /**
     * Get current usage for a key (for monitoring)
     *
     * @param key Redis key
     * @return Current usage count
     */
    public long getCurrentUsage(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.error("Error getting usage for key: {}", key, e);
            return 0;
        }
    }

    /**
     * Reset rate limit for a key (for testing or admin operations)
     *
     * @param key Redis key to reset
     */
    public void resetLimit(String key) {
        try {
            redisTemplate.delete(key);
            log.info("Reset rate limit for key: {}", key);
        } catch (Exception e) {
            log.error("Error resetting rate limit for key: {}", key, e);
        }
    }
}
