package com.mcpgateway.stream.policy;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.Map;

/**
 * Context for stream policy decisions.
 *
 * This is the INPUT to the policy engine - all information needed
 * to decide how to deliver the response.
 */
@Data
@Builder
public class StreamContext {

    /**
     * Request ID for tracing.
     */
    private String requestId;

    /**
     * Type of client making the request.
     */
    private ClientType clientType;

    /**
     * How the request reached the gateway.
     */
    private EntryTopology entryTopology;

    /**
     * Expected latency for this request type (p95 estimate).
     */
    private Duration expectedLatency;

    /**
     * Whether persistence/audit is allowed for this request.
     */
    @Builder.Default
    private boolean persistenceAllowed = true;

    /**
     * Cost budget constraints.
     */
    @Builder.Default
    private CostBudget budget = CostBudget.standard();

    /**
     * Whether the client explicitly requested streaming.
     */
    @Builder.Default
    private boolean streamingRequested = true;

    /**
     * Whether the client supports SSE.
     */
    @Builder.Default
    private boolean sseSupported = true;

    /**
     * Whether the client supports WebSocket.
     */
    @Builder.Default
    private boolean webSocketSupported = false;

    /**
     * User ID for rate limiting and preferences.
     */
    private String userId;

    /**
     * Additional attributes for custom policy rules.
     */
    private Map<String, Object> attributes;

    /**
     * Client IP address.
     */
    private String clientIp;

    /**
     * User-Agent header value.
     */
    private String userAgent;

    /**
     * Accept header value.
     */
    private String acceptHeader;

    public static StreamContext forBrowser(String requestId) {
        return StreamContext.builder()
                .requestId(requestId)
                .clientType(ClientType.BROWSER)
                .entryTopology(EntryTopology.UNKNOWN)
                .expectedLatency(Duration.ofSeconds(10))
                .sseSupported(true)
                .webSocketSupported(true)
                .build();
    }

    public static StreamContext forCli(String requestId) {
        return StreamContext.builder()
                .requestId(requestId)
                .clientType(ClientType.CLI)
                .entryTopology(EntryTopology.DIRECT)
                .expectedLatency(Duration.ofSeconds(30))
                .sseSupported(true)
                .webSocketSupported(false)
                .build();
    }

    public static StreamContext forSdk(String requestId) {
        return StreamContext.builder()
                .requestId(requestId)
                .clientType(ClientType.SDK)
                .entryTopology(EntryTopology.DIRECT)
                .expectedLatency(Duration.ofSeconds(30))
                .sseSupported(true)
                .webSocketSupported(true)
                .build();
    }
}
