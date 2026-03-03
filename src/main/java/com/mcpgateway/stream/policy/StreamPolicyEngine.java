package com.mcpgateway.stream.policy;

import com.mcpgateway.stream.observability.StreamEvent;
import com.mcpgateway.stream.observability.StreamLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * StreamSafe Policy Engine - the core decision maker.
 *
 * Design Invariants:
 * 1. Never assume downstream can stream
 * 2. Never assume entry link is transparent
 * 3. TTFB > 1s = streaming failure
 * 4. Fallback is a SUCCESS path, not an exception
 * 5. Every request must be able to explain "why I delivered this way"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamPolicyEngine {

    private final StreamLogger streamLogger;

    @Value("${mcp.stream.max-latency-for-streaming-seconds:20}")
    private int maxLatencyForStreamingSeconds;

    @Value("${mcp.stream.enable-sse-through-api-gateway:false}")
    private boolean enableSseThroughApiGateway;

    @Value("${mcp.stream.enable-sse-through-cdn:false}")
    private boolean enableSseThroughCdn;

    /**
     * Make a streaming decision based on context.
     *
     * V1 is rule-based - no AI, no ML needed.
     * Rules are sufficient for 80% of cases.
     */
    public StreamDecision decide(StreamContext ctx) {
        log.debug("Making stream decision for request: {}", ctx.getRequestId());

        // Rule 1: API Gateway blocks streaming
        if (ctx.getEntryTopology() == EntryTopology.API_GATEWAY && !enableSseThroughApiGateway) {
            return logAndReturn(ctx, StreamDecision.async("api_gateway_blocks_streaming"));
        }

        // Rule 2: CDN may buffer - be cautious
        if (ctx.getEntryTopology() == EntryTopology.CDN && !enableSseThroughCdn) {
            return logAndReturn(ctx, StreamDecision.async("cdn_may_buffer_streaming"));
        }

        // Rule 3: High latency expectation - use async
        if (ctx.getExpectedLatency() != null &&
            ctx.getExpectedLatency().toSeconds() > maxLatencyForStreamingSeconds) {
            return logAndReturn(ctx, StreamDecision.async("latency_risk_too_high"));
        }

        // Rule 4: Client doesn't support SSE and doesn't support WebSocket
        if (!ctx.isSseSupported() && !ctx.isWebSocketSupported()) {
            return logAndReturn(ctx, StreamDecision.sync("client_no_streaming_support"));
        }

        // Rule 5: Client explicitly requested non-streaming
        if (!ctx.isStreamingRequested()) {
            return logAndReturn(ctx, StreamDecision.sync("streaming_not_requested"));
        }

        // Rule 6: WebSocket preferred for persistent connections
        if (ctx.isWebSocketSupported() && ctx.getClientType() == ClientType.SDK) {
            return logAndReturn(ctx, StreamDecision.webSocket("sdk_prefers_websocket"));
        }

        // Rule 7: Unknown topology - be cautious
        if (ctx.getEntryTopology() == EntryTopology.UNKNOWN) {
            // For browsers, still try SSE (most common case)
            if (ctx.getClientType() == ClientType.BROWSER && ctx.isSseSupported()) {
                return logAndReturn(ctx, StreamDecision.sse("browser_direct_sse_attempt"));
            }
            // For others, use async to be safe
            return logAndReturn(ctx, StreamDecision.async("unknown_topology_use_async"));
        }

        // Rule 8: Reverse proxy - check if it's configured for streaming
        if (ctx.getEntryTopology() == EntryTopology.REVERSE_PROXY) {
            // Assume nginx with proxy_buffering off for direct connections
            if (ctx.getClientType() == ClientType.BROWSER || ctx.getClientType() == ClientType.CLI) {
                return logAndReturn(ctx, StreamDecision.sse("reverse_proxy_sse_allowed"));
            }
        }

        // Rule 9: ALB with HTTP/2 might work
        if (ctx.getEntryTopology() == EntryTopology.ALB) {
            // Conservative: use async for ALB
            return logAndReturn(ctx, StreamDecision.async("alb_streaming_unreliable"));
        }

        // Rule 10: NLB is safe (L4, no buffering)
        if (ctx.getEntryTopology() == EntryTopology.NLB) {
            return logAndReturn(ctx, StreamDecision.sse("nlb_direct_streaming_safe"));
        }

        // Rule 11: Direct connection - safest for streaming
        if (ctx.getEntryTopology() == EntryTopology.DIRECT) {
            if (ctx.isSseSupported()) {
                return logAndReturn(ctx, StreamDecision.sse("direct_connection_sse_safe"));
            }
            if (ctx.isWebSocketSupported()) {
                return logAndReturn(ctx, StreamDecision.webSocket("direct_connection_ws_safe"));
            }
        }

        // Rule 12: Persistence not allowed - use stateless streaming
        if (!ctx.isPersistenceAllowed()) {
            return logAndReturn(ctx, StreamDecision.sse("stateless_streaming_required"));
        }

        // Default: Use SSE if supported, otherwise async
        if (ctx.isSseSupported()) {
            return logAndReturn(ctx, StreamDecision.sse("default_sse_allowed"));
        }

        return logAndReturn(ctx, StreamDecision.async("fallback_to_async"));
    }

    /**
     * Re-evaluate decision when streaming fails.
     * This creates a FALLBACK decision, which is a SUCCESS path.
     */
    public StreamDecision fallback(StreamContext ctx, StreamDecision original, String failureReason) {
        log.info("Streaming fallback for request {}: {} -> ASYNC_JOB (reason: {})",
                ctx.getRequestId(), original.getMode(), failureReason);

        StreamDecision fallbackDecision = StreamDecision.fallbackTo(
                DeliveryMode.ASYNC_JOB,
                original,
                failureReason
        );

        streamLogger.logFallback(ctx, original, fallbackDecision);
        return fallbackDecision;
    }

    private StreamDecision logAndReturn(StreamContext ctx, StreamDecision decision) {
        decision.validate();
        log.debug("Stream decision for {}: mode={}, reason={}",
                ctx.getRequestId(), decision.getMode(), decision.getReason());
        streamLogger.logDecision(ctx, decision);
        return decision;
    }
}
