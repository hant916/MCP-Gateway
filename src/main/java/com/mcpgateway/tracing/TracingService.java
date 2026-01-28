package com.mcpgateway.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Service for managing distributed tracing across MCP Gateway
 */
@Slf4j
public class TracingService {

    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;
    private final boolean enabled;

    public TracingService(Tracer tracer, ObservationRegistry observationRegistry, boolean enabled) {
        this.tracer = tracer;
        this.observationRegistry = observationRegistry;
        this.enabled = enabled;
    }

    /**
     * Get the current trace ID
     */
    public String getCurrentTraceId() {
        if (!enabled || tracer == null) {
            return null;
        }
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }

    /**
     * Get the current span ID
     */
    public String getCurrentSpanId() {
        if (!enabled || tracer == null) {
            return null;
        }
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().spanId() : null;
    }

    /**
     * Create a new span for an operation
     */
    public SpanWrapper startSpan(String name) {
        if (!enabled || tracer == null) {
            return new NoOpSpanWrapper();
        }

        Span span = tracer.nextSpan().name(name).start();
        return new ActiveSpanWrapper(span, tracer);
    }

    /**
     * Create a new span with tags
     */
    public SpanWrapper startSpan(String name, Map<String, String> tags) {
        if (!enabled || tracer == null) {
            return new NoOpSpanWrapper();
        }

        Span span = tracer.nextSpan().name(name);
        tags.forEach(span::tag);
        span.start();
        return new ActiveSpanWrapper(span, tracer);
    }

    /**
     * Execute a supplier within a new span
     */
    public <T> T executeInSpan(String spanName, Supplier<T> supplier) {
        return executeInSpan(spanName, Map.of(), supplier);
    }

    /**
     * Execute a supplier within a new span with tags
     */
    public <T> T executeInSpan(String spanName, Map<String, String> tags, Supplier<T> supplier) {
        if (!enabled) {
            return supplier.get();
        }

        Observation observation = Observation.createNotStarted(spanName, observationRegistry);
        tags.forEach(observation::lowCardinalityKeyValue);

        return observation.observe(supplier);
    }

    /**
     * Execute a callable within a new span
     */
    public <T> T executeInSpan(String spanName, Callable<T> callable) throws Exception {
        if (!enabled) {
            return callable.call();
        }

        try (SpanWrapper span = startSpan(spanName)) {
            try {
                T result = callable.call();
                span.tag("status", "success");
                return result;
            } catch (Exception e) {
                span.error(e);
                throw e;
            }
        }
    }

    /**
     * Execute a runnable within a new span
     */
    public void executeInSpan(String spanName, Runnable runnable) {
        if (!enabled) {
            runnable.run();
            return;
        }

        Observation.createNotStarted(spanName, observationRegistry)
                .observe(runnable);
    }

    /**
     * Create an observation for monitoring
     */
    public Observation createObservation(String name) {
        return Observation.createNotStarted(name, observationRegistry);
    }

    /**
     * Add a tag to the current span
     */
    public void tagCurrentSpan(String key, String value) {
        if (!enabled || tracer == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }

    /**
     * Add an event to the current span
     */
    public void addEvent(String name) {
        if (!enabled || tracer == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.event(name);
        }
    }

    /**
     * Record an error in the current span
     */
    public void recordError(Throwable error) {
        if (!enabled || tracer == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.error(error);
        }
    }

    /**
     * Check if tracing is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Interface for span wrapper
     */
    public interface SpanWrapper extends AutoCloseable {
        void tag(String key, String value);
        void event(String name);
        void error(Throwable throwable);
        String getTraceId();
        String getSpanId();
        @Override
        void close();
    }

    /**
     * Active span wrapper implementation
     */
    private static class ActiveSpanWrapper implements SpanWrapper {
        private final Span span;
        private final Tracer tracer;
        private final Tracer.SpanInScope scope;

        ActiveSpanWrapper(Span span, Tracer tracer) {
            this.span = span;
            this.tracer = tracer;
            this.scope = tracer.withSpan(span);
        }

        @Override
        public void tag(String key, String value) {
            span.tag(key, value);
        }

        @Override
        public void event(String name) {
            span.event(name);
        }

        @Override
        public void error(Throwable throwable) {
            span.error(throwable);
        }

        @Override
        public String getTraceId() {
            return span.context().traceId();
        }

        @Override
        public String getSpanId() {
            return span.context().spanId();
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }

    /**
     * No-op span wrapper for when tracing is disabled
     */
    private static class NoOpSpanWrapper implements SpanWrapper {
        @Override
        public void tag(String key, String value) {}

        @Override
        public void event(String name) {}

        @Override
        public void error(Throwable throwable) {}

        @Override
        public String getTraceId() {
            return null;
        }

        @Override
        public String getSpanId() {
            return null;
        }

        @Override
        public void close() {}
    }
}
