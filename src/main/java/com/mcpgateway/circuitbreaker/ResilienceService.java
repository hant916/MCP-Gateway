package com.mcpgateway.circuitbreaker;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Unified Resilience Service - Ralph Loop Enhanced.
 *
 * Combines all resilience patterns:
 * - Circuit Breaker: Prevent cascade failures
 * - Bulkhead: Thread isolation
 * - Rate Limiter: Control request rate
 * - Retry: Automatic retry with backoff
 * - Time Limiter: Timeout control
 * - Fallback: Graceful degradation
 *
 * Design Principles:
 * 1. Every call is protected
 * 2. Failures are isolated
 * 3. Resources are bounded
 * 4. Recovery is automatic
 */
@Slf4j
@Service
public class ResilienceService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final FallbackRegistry fallbackRegistry;

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${mcp.resilience.bulkhead.max-concurrent-calls:25}")
    private int bulkheadMaxConcurrentCalls;

    @Value("${mcp.resilience.bulkhead.max-wait-duration:500}")
    private long bulkheadMaxWaitDuration;

    @Value("${mcp.resilience.rate-limiter.limit-for-period:100}")
    private int rateLimitForPeriod;

    @Value("${mcp.resilience.rate-limiter.limit-refresh-period:1}")
    private int rateLimitRefreshPeriod;

    public ResilienceService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            MeterRegistry meterRegistry,
            ApplicationEventPublisher eventPublisher,
            FallbackRegistry fallbackRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
        this.fallbackRegistry = fallbackRegistry;
    }

    /**
     * Execute with full resilience stack.
     * Order: RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> TimeLimiter -> Call
     */
    public <T> T execute(String name, Supplier<T> supplier) {
        return execute(name, supplier, null);
    }

    /**
     * Execute with full resilience stack and fallback.
     */
    public <T> T execute(String name, Supplier<T> supplier, Fallback<T> fallback) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";

        try {
            // Get or create all components
            CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(name);
            Bulkhead bulkhead = getOrCreateBulkhead(name);
            RateLimiter rateLimiter = getOrCreateRateLimiter(name);
            Retry retry = retryRegistry.retry(name);
            TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(name);

            // Build decorated supplier (inside out)
            // The call flows: RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> TimeLimiter -> supplier
            Supplier<CompletableFuture<T>> futureSupplier = () ->
                    CompletableFuture.supplyAsync(supplier, executor);

            Callable<T> timeLimited = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
            Supplier<T> retried = Retry.decorateCallable(retry, timeLimited);
            Supplier<T> circuitBroken = CircuitBreaker.decorateSupplier(circuitBreaker, retried);
            Supplier<T> bulkheaded = Bulkhead.decorateSupplier(bulkhead, circuitBroken);
            Supplier<T> rateLimited = RateLimiter.decorateSupplier(rateLimiter, bulkheaded);

            return rateLimited.get();

        } catch (Exception e) {
            outcome = "failure";
            log.warn("Resilience execution failed for {}: {}", name, e.getMessage());

            // Publish failure event
            eventPublisher.publishEvent(new ResilienceFailureEvent(name, e));

            // Try fallback
            if (fallback != null) {
                outcome = "fallback";
                recordFallback(name);
                return fallback.execute(e);
            }

            // Try registered fallback
            Fallback<T> registeredFallback = fallbackRegistry.getFallback(name);
            if (registeredFallback != null) {
                outcome = "fallback";
                recordFallback(name);
                return registeredFallback.execute(e);
            }

            throw e;
        } finally {
            sample.stop(Timer.builder("mcp.resilience.call")
                    .tag("name", name)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    /**
     * Execute async with full resilience stack.
     */
    public <T> CompletableFuture<T> executeAsync(String name, Supplier<T> supplier) {
        return executeAsync(name, supplier, null);
    }

    /**
     * Execute async with full resilience stack and fallback.
     */
    public <T> CompletableFuture<T> executeAsync(String name, Supplier<T> supplier, Fallback<T> fallback) {
        return CompletableFuture.supplyAsync(() -> execute(name, supplier, fallback), executor);
    }

    /**
     * Execute with only circuit breaker (lightweight).
     */
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(name);
        return circuitBreaker.executeSupplier(supplier);
    }

    /**
     * Execute with circuit breaker and bulkhead.
     */
    public <T> T executeWithIsolation(String name, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(name);
        Bulkhead bulkhead = getOrCreateBulkhead(name);

        Supplier<T> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                Bulkhead.decorateSupplier(bulkhead, supplier));

        return decorated.get();
    }

    /**
     * Check if circuit is open.
     */
    public boolean isCircuitOpen(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        return cb != null && cb.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * Get resilience statistics.
     */
    public ResilienceStats getStats(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        Bulkhead bh = bulkheads.get(name);
        RateLimiter rl = rateLimiters.get(name);

        return new ResilienceStats(
                name,
                cb != null ? cb.getState().name() : "UNKNOWN",
                cb != null ? cb.getMetrics().getFailureRate() : 0,
                cb != null ? cb.getMetrics().getNumberOfSuccessfulCalls() : 0,
                cb != null ? cb.getMetrics().getNumberOfFailedCalls() : 0,
                bh != null ? bh.getMetrics().getAvailableConcurrentCalls() : 0,
                rl != null ? rl.getMetrics().getAvailablePermissions() : 0
        );
    }

    /**
     * Get all stats.
     */
    public Map<String, ResilienceStats> getAllStats() {
        Map<String, ResilienceStats> stats = new ConcurrentHashMap<>();
        circuitBreakers.keySet().forEach(name -> stats.put(name, getStats(name)));
        return stats;
    }

    /**
     * Reset all components for a name.
     */
    public void reset(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb != null) {
            cb.reset();
        }
        log.info("Resilience components reset for: {}", name);
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String name) {
        return circuitBreakers.computeIfAbsent(name, n -> {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(n);

            // Register event handlers
            cb.getEventPublisher()
                    .onStateTransition(event -> {
                        log.info("Circuit breaker {} state: {} -> {}",
                                n, event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState());

                        eventPublisher.publishEvent(new CircuitBreakerStateChangeEvent(
                                n,
                                event.getStateTransition().getFromState().name(),
                                event.getStateTransition().getToState().name()
                        ));

                        meterRegistry.counter("mcp.circuit_breaker.transition",
                                "name", n,
                                "from", event.getStateTransition().getFromState().name(),
                                "to", event.getStateTransition().getToState().name())
                                .increment();
                    })
                    .onSlowCallRateExceeded(event ->
                            log.warn("Circuit breaker {} slow call rate exceeded: {}%",
                                    n, event.getSlowCallRate()))
                    .onFailureRateExceeded(event ->
                            log.warn("Circuit breaker {} failure rate exceeded: {}%",
                                    n, event.getFailureRate()));

            return cb;
        });
    }

    private Bulkhead getOrCreateBulkhead(String name) {
        return bulkheads.computeIfAbsent(name, n -> {
            BulkheadConfig config = BulkheadConfig.custom()
                    .maxConcurrentCalls(bulkheadMaxConcurrentCalls)
                    .maxWaitDuration(Duration.ofMillis(bulkheadMaxWaitDuration))
                    .build();

            Bulkhead bh = bulkheadRegistry.bulkhead(n, config);

            bh.getEventPublisher()
                    .onCallRejected(event -> {
                        log.warn("Bulkhead {} rejected call", n);
                        meterRegistry.counter("mcp.bulkhead.rejected", "name", n).increment();
                    })
                    .onCallFinished(event ->
                            meterRegistry.timer("mcp.bulkhead.duration", "name", n)
                                    .record(event.getCallDuration()));

            return bh;
        });
    }

    private RateLimiter getOrCreateRateLimiter(String name) {
        return rateLimiters.computeIfAbsent(name, n -> {
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(rateLimitForPeriod)
                    .limitRefreshPeriod(Duration.ofSeconds(rateLimitRefreshPeriod))
                    .timeoutDuration(Duration.ofMillis(500))
                    .build();

            RateLimiter rl = rateLimiterRegistry.rateLimiter(n, config);

            rl.getEventPublisher()
                    .onFailure(event -> {
                        log.debug("Rate limiter {} rejected", n);
                        meterRegistry.counter("mcp.rate_limiter.rejected", "name", n).increment();
                    });

            return rl;
        });
    }

    private void recordFallback(String name) {
        meterRegistry.counter("mcp.resilience.fallback", "name", name).increment();
    }

    /**
     * Statistics record.
     */
    public record ResilienceStats(
            String name,
            String circuitState,
            float failureRate,
            int successfulCalls,
            int failedCalls,
            int availableBulkheadCalls,
            int availableRateLimitPermits
    ) {}

    /**
     * Event: Circuit breaker state change.
     */
    public record CircuitBreakerStateChangeEvent(String name, String fromState, String toState) {}

    /**
     * Event: Resilience failure.
     */
    public record ResilienceFailureEvent(String name, Throwable cause) {}
}
