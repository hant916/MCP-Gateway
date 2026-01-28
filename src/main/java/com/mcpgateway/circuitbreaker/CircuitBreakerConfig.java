package com.mcpgateway.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfig {

    @Value("${mcp.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${mcp.circuit-breaker.slow-call-rate-threshold:100}")
    private float slowCallRateThreshold;

    @Value("${mcp.circuit-breaker.slow-call-duration-threshold:2000}")
    private long slowCallDurationThreshold;

    @Value("${mcp.circuit-breaker.permitted-calls-in-half-open:3}")
    private int permittedCallsInHalfOpen;

    @Value("${mcp.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${mcp.circuit-breaker.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    @Value("${mcp.circuit-breaker.wait-duration-in-open-state:10000}")
    private long waitDurationInOpenState;

    @Value("${mcp.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${mcp.retry.wait-duration:500}")
    private long retryWaitDuration;

    @Value("${mcp.timeout.duration:5000}")
    private long timeoutDuration;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaultConfig =
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThreshold))
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenState))
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    @Bean
    public RetryRegistry retryRegistry() {
        io.github.resilience4j.retry.RetryConfig defaultConfig =
            io.github.resilience4j.retry.RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(retryWaitDuration))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        return RetryRegistry.of(defaultConfig);
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        io.github.resilience4j.timelimiter.TimeLimiterConfig defaultConfig =
            io.github.resilience4j.timelimiter.TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutDuration))
                .cancelRunningFuture(true)
                .build();

        return TimeLimiterRegistry.of(defaultConfig);
    }

    @Bean
    public CircuitBreaker mcpServerCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("mcpServer");
    }

    @Bean
    public CircuitBreaker toolExecutionCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("toolExecution");
    }

    @Bean
    public Retry mcpServerRetry(RetryRegistry registry) {
        return registry.retry("mcpServer");
    }

    @Bean
    public TimeLimiter mcpServerTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter("mcpServer");
    }
}
