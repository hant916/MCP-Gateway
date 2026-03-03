package com.mcpgateway.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Trace Context Propagator for inter-service communication.
 *
 * Handles:
 * - Extract trace context from incoming requests
 * - Inject trace context into outgoing requests
 * - MDC integration for log correlation
 * - Baggage propagation for custom data
 *
 * Standard Headers:
 * - traceparent: W3C Trace Context
 * - tracestate: W3C Trace State
 * - X-B3-TraceId, X-B3-SpanId, X-B3-Sampled: B3 format
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracePropagator {

    private final Tracer tracer;
    private final Propagator propagator;

    // Standard header names
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    public static final String SAMPLED_HEADER = "X-Sampled";

    // B3 headers
    public static final String B3_TRACE_ID = "X-B3-TraceId";
    public static final String B3_SPAN_ID = "X-B3-SpanId";
    public static final String B3_PARENT_SPAN_ID = "X-B3-ParentSpanId";
    public static final String B3_SAMPLED = "X-B3-Sampled";
    public static final String B3_SINGLE = "b3";

    // W3C headers
    public static final String W3C_TRACEPARENT = "traceparent";
    public static final String W3C_TRACESTATE = "tracestate";

    // MDC keys
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_PARENT_SPAN_ID = "parentSpanId";
    public static final String MDC_REQUEST_ID = "requestId";

    /**
     * Extract trace context from incoming headers.
     */
    public TraceContext extract(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        try {
            // Use the propagator to extract
            Span.Builder builder = propagator.extract(headers, new MapGetter());
            if (builder != null) {
                return builder.start().context();
            }
        } catch (Exception e) {
            log.debug("Failed to extract trace context: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Inject trace context into outgoing headers.
     */
    public void inject(Map<String, String> headers) {
        if (tracer == null) {
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }

        try {
            // Use propagator to inject
            propagator.inject(currentSpan.context(), headers, new MapSetter());

            // Also add custom headers for compatibility
            TraceContext ctx = currentSpan.context();
            headers.put(TRACE_ID_HEADER, ctx.traceId());
            headers.put(SPAN_ID_HEADER, ctx.spanId());
            if (ctx.parentId() != null) {
                headers.put(PARENT_SPAN_ID_HEADER, ctx.parentId());
            }
            headers.put(SAMPLED_HEADER, String.valueOf(ctx.sampled()));
        } catch (Exception e) {
            log.debug("Failed to inject trace context: {}", e.getMessage());
        }
    }

    /**
     * Inject trace context into a BiConsumer (for various HTTP clients).
     */
    public void inject(BiConsumer<String, String> headerSetter) {
        Map<String, String> headers = new HashMap<>();
        inject(headers);
        headers.forEach(headerSetter);
    }

    /**
     * Set MDC context from current span.
     */
    public void setMdcContext() {
        if (tracer == null) {
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            TraceContext ctx = currentSpan.context();
            MDC.put(MDC_TRACE_ID, ctx.traceId());
            MDC.put(MDC_SPAN_ID, ctx.spanId());
            if (ctx.parentId() != null) {
                MDC.put(MDC_PARENT_SPAN_ID, ctx.parentId());
            }
        }
    }

    /**
     * Set MDC context with request ID.
     */
    public void setMdcContext(String requestId) {
        setMdcContext();
        if (requestId != null) {
            MDC.put(MDC_REQUEST_ID, requestId);
        }
    }

    /**
     * Clear MDC context.
     */
    public void clearMdcContext() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_PARENT_SPAN_ID);
        MDC.remove(MDC_REQUEST_ID);
    }

    /**
     * Get current trace context as a map.
     */
    public Map<String, String> getCurrentContextAsMap() {
        Map<String, String> context = new HashMap<>();

        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TraceContext ctx = currentSpan.context();
                context.put("traceId", ctx.traceId());
                context.put("spanId", ctx.spanId());
                if (ctx.parentId() != null) {
                    context.put("parentSpanId", ctx.parentId());
                }
                context.put("sampled", String.valueOf(ctx.sampled()));
            }
        }

        return context;
    }

    /**
     * Create a child span with propagation.
     */
    public Span createChildSpan(String name, Map<String, String> incomingHeaders) {
        TraceContext parentContext = extract(incomingHeaders);

        if (parentContext != null && tracer != null) {
            return tracer.spanBuilder()
                    .setParent(parentContext)
                    .name(name)
                    .start();
        }

        // No parent context, create new span
        return tracer != null ? tracer.nextSpan().name(name).start() : null;
    }

    /**
     * Getter implementation for Map.
     */
    private static class MapGetter implements Propagator.Getter<Map<String, String>> {
        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }

    /**
     * Setter implementation for Map.
     */
    private static class MapSetter implements Propagator.Setter<Map<String, String>> {
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            carrier.put(key, value);
        }
    }
}
