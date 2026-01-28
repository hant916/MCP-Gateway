package com.mcpgateway.stream.policy;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * The decision output from the policy engine.
 *
 * CRITICAL: Every decision MUST have a reason.
 * Without reason, observability is useless.
 */
@Data
@Builder
public class StreamDecision {

    /**
     * The delivery mode to use.
     */
    private DeliveryMode mode;

    /**
     * Human-readable reason for this decision.
     * REQUIRED - this is non-negotiable for observability.
     */
    private String reason;

    /**
     * Whether to use streaming when fetching from upstream.
     * This is decoupled from the delivery mode to client.
     */
    @Builder.Default
    private boolean upstreamStreaming = true;

    /**
     * Confidence level of this decision (0.0 - 1.0).
     */
    @Builder.Default
    private double confidence = 1.0;

    /**
     * Policy rule ID that triggered this decision.
     */
    private String ruleId;

    /**
     * When this decision was made.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Whether this is a fallback from a failed streaming attempt.
     */
    @Builder.Default
    private boolean isFallback = false;

    /**
     * Original mode if this is a fallback.
     */
    private DeliveryMode originalMode;

    /**
     * Fallback reason if this is a fallback.
     */
    private String fallbackReason;

    // Factory methods for common decisions

    public static StreamDecision sse(String reason) {
        return StreamDecision.builder()
                .mode(DeliveryMode.SSE_DIRECT)
                .reason(reason)
                .upstreamStreaming(true)
                .build();
    }

    public static StreamDecision webSocket(String reason) {
        return StreamDecision.builder()
                .mode(DeliveryMode.WS_PUSH)
                .reason(reason)
                .upstreamStreaming(true)
                .build();
    }

    public static StreamDecision async(String reason) {
        return StreamDecision.builder()
                .mode(DeliveryMode.ASYNC_JOB)
                .reason(reason)
                .upstreamStreaming(true)
                .build();
    }

    public static StreamDecision sync(String reason) {
        return StreamDecision.builder()
                .mode(DeliveryMode.SYNC)
                .reason(reason)
                .upstreamStreaming(false)
                .build();
    }

    /**
     * Create a fallback decision from a failed streaming attempt.
     */
    public static StreamDecision fallbackTo(DeliveryMode newMode, StreamDecision original, String fallbackReason) {
        return StreamDecision.builder()
                .mode(newMode)
                .reason("Fallback: " + fallbackReason)
                .upstreamStreaming(original.isUpstreamStreaming())
                .isFallback(true)
                .originalMode(original.getMode())
                .fallbackReason(fallbackReason)
                .build();
    }

    /**
     * Validate this decision has required fields.
     */
    public void validate() {
        if (mode == null) {
            throw new IllegalStateException("StreamDecision must have a mode");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("StreamDecision must have a reason - this is non-negotiable");
        }
    }
}
