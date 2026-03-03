package com.mcpgateway.tracing;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Configuration for distributed tracing using Micrometer Tracing
 */
@Configuration
public class TracingConfig {

    @Value("${mcp.tracing.enabled:true}")
    private boolean tracingEnabled;

    @Value("${mcp.tracing.sampling-probability:1.0}")
    private float samplingProbability;

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean
    public TracingService tracingService(Tracer tracer, ObservationRegistry observationRegistry) {
        return new TracingService(tracer, observationRegistry, tracingEnabled);
    }

    @Bean
    public TracingFilter tracingFilter(TracingService tracingService) {
        return new TracingFilter(tracingService);
    }
}
