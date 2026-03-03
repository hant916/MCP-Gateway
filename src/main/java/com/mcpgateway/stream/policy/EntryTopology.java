package com.mcpgateway.stream.policy;

/**
 * Entry topology - how the request reaches the gateway.
 *
 * This is critical for streaming decisions because many proxies
 * and load balancers buffer responses, breaking streaming.
 */
public enum EntryTopology {

    /**
     * Direct connection to the gateway (no proxy).
     * Streaming: SAFE
     */
    DIRECT,

    /**
     * Behind an API Gateway (AWS API Gateway, Kong, etc.).
     * Streaming: UNSAFE - most API gateways buffer responses.
     */
    API_GATEWAY,

    /**
     * Behind CloudFlare or similar CDN.
     * Streaming: CONDITIONAL - depends on configuration.
     */
    CDN,

    /**
     * Behind Application Load Balancer (AWS ALB, etc.).
     * Streaming: CONDITIONAL - HTTP/2 may work, HTTP/1.1 may buffer.
     */
    ALB,

    /**
     * Behind Network Load Balancer.
     * Streaming: SAFE - NLB is L4, doesn't buffer.
     */
    NLB,

    /**
     * Behind nginx or similar reverse proxy.
     * Streaming: CONDITIONAL - depends on proxy_buffering setting.
     */
    REVERSE_PROXY,

    /**
     * Unknown or undetectable topology.
     * Streaming: UNSAFE - assume the worst.
     */
    UNKNOWN
}
