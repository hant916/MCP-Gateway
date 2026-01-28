package com.mcpgateway.stream;

import com.mcpgateway.stream.policy.DeliveryMode;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics collection for StreamSafe.
 *
 * Key metrics:
 * 1. TTFB (Time to First Byte) - must be < 1s for streaming
 * 2. Decision distribution by mode
 * 3. Fallback rate
 * 4. Error rate by mode
 * 5. Throughput (tokens/sec, bytes/sec)
 */
@Slf4j
@Component
public class StreamMetrics {

    private final MeterRegistry meterRegistry;

    // Active sessions gauge
    private final Map<DeliveryMode, AtomicInteger> activeSessions = new ConcurrentHashMap<>();

    // Counters
    private final Counter totalRequests;
    private final Counter totalTokens;
    private final Counter totalBytes;
    private final Counter ttfbExceeded;

    public StreamMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.totalRequests = Counter.builder("stream.requests.total")
                .description("Total streaming requests")
                .register(meterRegistry);

        this.totalTokens = Counter.builder("stream.tokens.total")
                .description("Total tokens delivered")
                .register(meterRegistry);

        this.totalBytes = Counter.builder("stream.bytes.total")
                .description("Total bytes delivered")
                .register(meterRegistry);

        this.ttfbExceeded = Counter.builder("stream.ttfb.exceeded")
                .description("Requests where TTFB exceeded 1 second")
                .register(meterRegistry);

        // Initialize gauges for each delivery mode
        for (DeliveryMode mode : DeliveryMode.values()) {
            AtomicInteger counter = new AtomicInteger(0);
            activeSessions.put(mode, counter);
            Gauge.builder("stream.sessions.active", counter, AtomicInteger::get)
                    .tag("mode", mode.name())
                    .description("Active streaming sessions")
                    .register(meterRegistry);
        }
    }

    /**
     * Record a new streaming request.
     */
    public void recordRequest(DeliveryMode mode, String reason) {
        totalRequests.increment();

        Counter.builder("stream.requests")
                .tag("mode", mode.name())
                .tag("reason", sanitizeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record session start.
     */
    public void recordSessionStart(DeliveryMode mode) {
        activeSessions.get(mode).incrementAndGet();
    }

    /**
     * Record session end.
     */
    public void recordSessionEnd(DeliveryMode mode) {
        activeSessions.get(mode).decrementAndGet();
    }

    /**
     * Record time to first byte.
     */
    public void recordTtfb(DeliveryMode mode, long ttfbMs) {
        Timer.builder("stream.ttfb")
                .tag("mode", mode.name())
                .description("Time to first byte")
                .register(meterRegistry)
                .record(Duration.ofMillis(ttfbMs));

        if (ttfbMs > 1000) {
            ttfbExceeded.increment();
            Counter.builder("stream.ttfb.exceeded.by_mode")
                    .tag("mode", mode.name())
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Record successful completion.
     */
    public void recordCompletion(DeliveryMode mode, long durationMs, int chunks, long bytes) {
        Timer.builder("stream.duration")
                .tag("mode", mode.name())
                .description("Total stream duration")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        totalTokens.increment(chunks);
        totalBytes.increment(bytes);

        Counter.builder("stream.completed")
                .tag("mode", mode.name())
                .register(meterRegistry)
                .increment();

        // Record distribution
        DistributionSummary.builder("stream.chunks")
                .tag("mode", mode.name())
                .description("Chunks per request")
                .register(meterRegistry)
                .record(chunks);

        DistributionSummary.builder("stream.bytes")
                .tag("mode", mode.name())
                .description("Bytes per request")
                .register(meterRegistry)
                .record(bytes);
    }

    /**
     * Record failure.
     */
    public void recordFailure(DeliveryMode mode, String errorType) {
        Counter.builder("stream.failures")
                .tag("mode", mode.name())
                .tag("error", sanitizeTag(errorType))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record fallback.
     */
    public void recordFallback(DeliveryMode originalMode, DeliveryMode newMode, String reason) {
        Counter.builder("stream.fallbacks")
                .tag("from", originalMode.name())
                .tag("to", newMode.name())
                .tag("reason", sanitizeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record client disconnect.
     */
    public void recordClientDisconnect(DeliveryMode mode) {
        Counter.builder("stream.client_disconnects")
                .tag("mode", mode.name())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record buffer overflow.
     */
    public void recordBufferOverflow(DeliveryMode mode) {
        Counter.builder("stream.buffer_overflow")
                .tag("mode", mode.name())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Get current active session count.
     */
    public int getActiveSessionCount(DeliveryMode mode) {
        return activeSessions.get(mode).get();
    }

    /**
     * Get total active session count.
     */
    public int getTotalActiveSessionCount() {
        return activeSessions.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
    }

    private String sanitizeTag(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .substring(0, Math.min(value.length(), 50));
    }
}
