package com.mcpgateway.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Distributed Tracing Configuration
 *
 * Enables distributed tracing across microservices using:
 * - Micrometer Tracing (abstraction layer)
 * - Brave (implementation)
 * - Zipkin (backend for visualization)
 *
 * Trace propagation:
 * - B3 format (compatible with Zipkin, Jaeger)
 * - Automatic trace/span ID injection in HTTP headers
 * - Correlation IDs in logs
 *
 * What gets traced:
 * - All HTTP requests (automatic)
 * - Database queries (automatic)
 * - Redis operations (automatic)
 * - Custom business operations (via @Observed annotation)
 *
 * Configuration:
 * - Sampling rate: 100% in dev, 10-20% in production
 * - Zipkin endpoint: http://localhost:9411/api/v2/spans
 * - Propagation format: B3
 */
@Slf4j
@Configuration
@ConditionalOnEnabledTracing
public class TracingConfig {

    /**
     * Enable @Observed annotation for custom spans
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("Enabling @Observed annotation support for custom tracing spans");
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Log trace context on application startup
     */
    @Bean
    public TracingLogger tracingLogger(Tracer tracer) {
        return new TracingLogger(tracer);
    }

    /**
     * Helper class for logging with trace context
     */
    public static class TracingLogger {
        private final Tracer tracer;

        public TracingLogger(Tracer tracer) {
            this.tracer = tracer;
            log.info("Distributed tracing enabled. Tracer: {}", tracer.getClass().getSimpleName());
        }

        public String getCurrentTraceId() {
            if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
                return tracer.currentSpan().context().traceId();
            }
            return "no-trace";
        }

        public String getCurrentSpanId() {
            if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
                return tracer.currentSpan().context().spanId();
            }
            return "no-span";
        }
    }
}
