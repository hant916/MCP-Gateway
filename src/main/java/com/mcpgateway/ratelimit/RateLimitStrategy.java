package com.mcpgateway.ratelimit;

/**
 * Rate limiting strategies for MCP Gateway
 *
 * Different strategies for different use cases:
 * - FIXED_WINDOW: Simple counter reset at fixed intervals
 * - SLIDING_WINDOW: More accurate, counts over rolling time window
 * - TOKEN_BUCKET: Allows burst traffic with token refill
 * - LEAKY_BUCKET: Smooths traffic flow, processes at constant rate
 */
public enum RateLimitStrategy {
    /**
     * Fixed window algorithm - simple counter that resets at fixed intervals
     * Fast but can allow 2x limit at window boundaries
     */
    FIXED_WINDOW,

    /**
     * Sliding window algorithm - more accurate rate limiting
     * Uses weighted counting across time windows
     */
    SLIDING_WINDOW,

    /**
     * Token bucket algorithm - allows burst traffic
     * Tokens refill at constant rate, consumed per request
     */
    TOKEN_BUCKET,

    /**
     * Leaky bucket algorithm - smooths traffic
     * Processes requests at constant rate, excess are queued or rejected
     */
    LEAKY_BUCKET
}
