package com.mcpgateway.stream.policy;

/**
 * Delivery modes for streaming responses.
 *
 * The mode determines HOW content is delivered to the client,
 * which is decoupled from whether the upstream uses streaming.
 */
public enum DeliveryMode {

    /**
     * Direct Server-Sent Events streaming.
     * Best for: Browser clients with direct connection, low latency requirements.
     * Constraint: First byte must arrive within 1 second.
     */
    SSE_DIRECT,

    /**
     * WebSocket push delivery.
     * Best for: Clients that need bidirectional communication, reconnection support.
     * Supports: Token replay on reconnect.
     */
    WS_PUSH,

    /**
     * Asynchronous job with polling/webhook.
     * Best for: API Gateway environments, high latency scenarios, audit requirements.
     * Returns: Job ID immediately, content via polling or webhook.
     */
    ASYNC_JOB,

    /**
     * Synchronous response (no streaming).
     * Best for: Simple clients, debugging, guaranteed delivery.
     * Returns: Complete response after processing finishes.
     */
    SYNC
}
