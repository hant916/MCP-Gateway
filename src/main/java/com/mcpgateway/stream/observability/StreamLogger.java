package com.mcpgateway.stream.observability;

import com.mcpgateway.stream.policy.StreamContext;
import com.mcpgateway.stream.policy.StreamDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stream logger for observability.
 *
 * Outputs to:
 * 1. Structured logs (for log aggregation)
 * 2. Metrics (for dashboards and alerts)
 * 3. Optional: response headers (for debugging)
 */
@Slf4j
@Component
public class StreamLogger {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> requestStartTimes = new ConcurrentHashMap<>();

    public StreamLogger(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Log a streaming decision.
     */
    public void logDecision(StreamContext ctx, StreamDecision decision) {
        requestStartTimes.put(ctx.getRequestId(), System.currentTimeMillis());

        // Structured log
        log.info("stream.decision requestId={} mode={} reason={} clientType={} topology={} upstreamStreaming={}",
                ctx.getRequestId(),
                decision.getMode(),
                decision.getReason(),
                ctx.getClientType(),
                ctx.getEntryTopology(),
                decision.isUpstreamStreaming());

        // Metrics
        Counter.builder("stream.decision")
                .tag("mode", decision.getMode().name())
                .tag("reason", sanitizeReason(decision.getReason()))
                .tag("client_type", ctx.getClientType() != null ? ctx.getClientType().name() : "UNKNOWN")
                .tag("topology", ctx.getEntryTopology() != null ? ctx.getEntryTopology().name() : "UNKNOWN")
                .register(meterRegistry)
                .increment();

        // Emit event
        StreamEvent event = StreamEvent.decisionMade(
                ctx.getRequestId(),
                decision.getMode(),
                decision.getReason()
        );
        event.setClientType(ctx.getClientType() != null ? ctx.getClientType().name() : null);
        event.setEntryTopology(ctx.getEntryTopology() != null ? ctx.getEntryTopology().name() : null);
        event.setUserId(ctx.getUserId());
        event.setUpstreamStreaming(decision.isUpstreamStreaming());

        emitEvent(event);
    }

    /**
     * Log a fallback event.
     */
    public void logFallback(StreamContext ctx, StreamDecision original, StreamDecision fallback) {
        log.warn("stream.fallback requestId={} originalMode={} newMode={} reason={}",
                ctx.getRequestId(),
                original.getMode(),
                fallback.getMode(),
                fallback.getFallbackReason());

        // Metrics
        Counter.builder("stream.fallback")
                .tag("original_mode", original.getMode().name())
                .tag("new_mode", fallback.getMode().name())
                .tag("reason", sanitizeReason(fallback.getFallbackReason()))
                .register(meterRegistry)
                .increment();

        emitEvent(StreamEvent.fallbackTriggered(
                ctx.getRequestId(),
                original.getMode(),
                fallback.getMode(),
                fallback.getFallbackReason()
        ));
    }

    /**
     * Log first byte sent.
     */
    public void logFirstByte(String requestId, StreamDecision decision) {
        Long startTime = requestStartTimes.get(requestId);
        long ttfbMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

        log.info("stream.first_byte requestId={} mode={} ttfbMs={}",
                requestId, decision.getMode(), ttfbMs);

        // Metrics
        Timer.builder("stream.ttfb")
                .tag("mode", decision.getMode().name())
                .register(meterRegistry)
                .record(Duration.ofMillis(ttfbMs));

        // Check TTFB constraint
        if (ttfbMs > 1000) {
            log.warn("stream.ttfb_exceeded requestId={} ttfbMs={} threshold=1000",
                    requestId, ttfbMs);
            Counter.builder("stream.ttfb_exceeded")
                    .tag("mode", decision.getMode().name())
                    .register(meterRegistry)
                    .increment();
        }

        emitEvent(StreamEvent.firstByteSent(requestId, decision.getMode(), ttfbMs));
    }

    /**
     * Log stream completion.
     */
    public void logCompletion(String requestId, StreamDecision decision,
                              int chunksDelivered, long bytesDelivered) {
        Long startTime = requestStartTimes.remove(requestId);
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

        log.info("stream.completed requestId={} mode={} durationMs={} chunks={} bytes={}",
                requestId, decision.getMode(), durationMs, chunksDelivered, bytesDelivered);

        // Metrics
        Timer.builder("stream.duration")
                .tag("mode", decision.getMode().name())
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("stream.chunks")
                .tag("mode", decision.getMode().name())
                .register(meterRegistry)
                .increment(chunksDelivered);

        Counter.builder("stream.bytes")
                .tag("mode", decision.getMode().name())
                .register(meterRegistry)
                .increment(bytesDelivered);

        emitEvent(StreamEvent.streamCompleted(
                requestId, decision.getMode(), durationMs, chunksDelivered, bytesDelivered
        ));
    }

    /**
     * Log stream failure.
     */
    public void logFailure(String requestId, StreamDecision decision, Throwable error) {
        requestStartTimes.remove(requestId);

        log.error("stream.failed requestId={} mode={} error={} message={}",
                requestId, decision.getMode(),
                error.getClass().getSimpleName(), error.getMessage());

        // Metrics
        Counter.builder("stream.failure")
                .tag("mode", decision.getMode().name())
                .tag("error", error.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();

        emitEvent(StreamEvent.streamFailed(
                requestId, decision.getMode(),
                error.getMessage(), error.getClass().getSimpleName()
        ));
    }

    /**
     * Log client disconnection.
     */
    public void logClientDisconnect(String requestId, StreamDecision decision) {
        requestStartTimes.remove(requestId);

        log.info("stream.client_disconnect requestId={} mode={}",
                requestId, decision.getMode());

        Counter.builder("stream.client_disconnect")
                .tag("mode", decision.getMode().name())
                .register(meterRegistry)
                .increment();

        StreamEvent event = StreamEvent.builder()
                .requestId(requestId)
                .eventType(StreamEvent.EventType.CLIENT_DISCONNECTED)
                .deliveryMode(decision.getMode())
                .build();
        emitEvent(event);
    }

    /**
     * Emit a stream event (can be extended to send to external systems).
     */
    private void emitEvent(StreamEvent event) {
        // For now, just log as JSON-like structure
        // In production, this could send to Kafka, CloudWatch, etc.
        if (log.isDebugEnabled()) {
            log.debug("stream.event type={} requestId={} mode={} reason={}",
                    event.getEventType(),
                    event.getRequestId(),
                    event.getDeliveryMode(),
                    event.getReason());
        }
    }

    /**
     * Sanitize reason string for use as metric tag.
     */
    private String sanitizeReason(String reason) {
        if (reason == null) {
            return "unknown";
        }
        // Limit length and remove special characters
        return reason.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .substring(0, Math.min(reason.length(), 50));
    }
}
