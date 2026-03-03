package com.mcpgateway.stream.observability;

import com.mcpgateway.stream.policy.DeliveryMode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Stream event for observability.
 *
 * EVERY streaming request MUST produce at least one StreamEvent.
 * This is non-negotiable for debugging and monitoring.
 */
@Data
@Builder
public class StreamEvent {

    public enum EventType {
        DECISION_MADE,
        STREAM_STARTED,
        FIRST_BYTE_SENT,
        CHUNK_SENT,
        STREAM_COMPLETED,
        STREAM_FAILED,
        FALLBACK_TRIGGERED,
        BUFFER_OVERFLOW,
        CLIENT_DISCONNECTED,
        TIMEOUT
    }

    /**
     * Unique request identifier.
     */
    private String requestId;

    /**
     * Type of event.
     */
    private EventType eventType;

    /**
     * Delivery mode used/decided.
     */
    private DeliveryMode deliveryMode;

    /**
     * Time to first byte in milliseconds.
     * Only set for FIRST_BYTE_SENT events.
     */
    private Long ttfbMs;

    /**
     * Total duration in milliseconds.
     * Only set for STREAM_COMPLETED events.
     */
    private Long totalDurationMs;

    /**
     * Reason for the decision or failure.
     * REQUIRED for DECISION_MADE and FALLBACK_TRIGGERED events.
     */
    private String reason;

    /**
     * Whether upstream used streaming.
     */
    private Boolean upstreamStreaming;

    /**
     * Number of tokens/chunks delivered.
     */
    private Integer chunksDelivered;

    /**
     * Total bytes delivered.
     */
    private Long bytesDelivered;

    /**
     * Whether this is a fallback from another mode.
     */
    @Builder.Default
    private boolean isFallback = false;

    /**
     * Original mode if this is a fallback.
     */
    private DeliveryMode originalMode;

    /**
     * Fallback reason if applicable.
     */
    private String fallbackReason;

    /**
     * User ID if available.
     */
    private String userId;

    /**
     * Session ID if available.
     */
    private String sessionId;

    /**
     * Client type (browser, cli, sdk, etc.).
     */
    private String clientType;

    /**
     * Entry topology (direct, api_gateway, cdn, etc.).
     */
    private String entryTopology;

    /**
     * When the event occurred.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Additional attributes for debugging.
     */
    private Map<String, Object> attributes;

    /**
     * Error message if applicable.
     */
    private String errorMessage;

    /**
     * Error class if applicable.
     */
    private String errorClass;

    // Factory methods for common events

    public static StreamEvent decisionMade(String requestId, DeliveryMode mode, String reason) {
        return StreamEvent.builder()
                .requestId(requestId)
                .eventType(EventType.DECISION_MADE)
                .deliveryMode(mode)
                .reason(reason)
                .build();
    }

    public static StreamEvent streamStarted(String requestId, DeliveryMode mode) {
        return StreamEvent.builder()
                .requestId(requestId)
                .eventType(EventType.STREAM_STARTED)
                .deliveryMode(mode)
                .build();
    }

    public static StreamEvent firstByteSent(String requestId, DeliveryMode mode, long ttfbMs) {
        return StreamEvent.builder()
                .requestId(requestId)
                .eventType(EventType.FIRST_BYTE_SENT)
                .deliveryMode(mode)
                .ttfbMs(ttfbMs)
                .build();
    }

    public static StreamEvent streamCompleted(String requestId, DeliveryMode mode,
                                              long durationMs, int chunks, long bytes) {
        return StreamEvent.builder()
                .requestId(requestId)
                .eventType(EventType.STREAM_COMPLETED)
                .deliveryMode(mode)
                .totalDurationMs(durationMs)
                .chunksDelivered(chunks)
                .bytesDelivered(bytes)
                .build();
    }

    public static StreamEvent streamFailed(String requestId, DeliveryMode mode,
                                           String errorMessage, String errorClass) {
        return StreamEvent.builder()
                .requestId(requestId)
                .eventType(EventType.STREAM_FAILED)
                .deliveryMode(mode)
                .errorMessage(errorMessage)
                .errorClass(errorClass)
                .build();
    }

    public static StreamEvent fallbackTriggered(String requestId, DeliveryMode originalMode,
                                                DeliveryMode newMode, String reason) {
        return StreamEvent.builder()
                .requestId(requestId)
                .eventType(EventType.FALLBACK_TRIGGERED)
                .deliveryMode(newMode)
                .originalMode(originalMode)
                .isFallback(true)
                .fallbackReason(reason)
                .reason(reason)
                .build();
    }
}
