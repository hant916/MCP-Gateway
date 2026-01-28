package com.mcpgateway.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
public class CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, CircuitBreaker> serverCircuitBreakers = new ConcurrentHashMap<>();

    public CircuitBreakerService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            MeterRegistry meterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Execute a call with circuit breaker protection for a specific MCP server
     */
    public <T> T executeWithCircuitBreaker(String serverId, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serverId);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = circuitBreaker.executeSupplier(supplier);
            sample.stop(Timer.builder("mcp.circuit_breaker.call")
                    .tag("server", serverId)
                    .tag("status", "success")
                    .register(meterRegistry));
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("mcp.circuit_breaker.call")
                    .tag("server", serverId)
                    .tag("status", "failure")
                    .register(meterRegistry));
            throw e;
        }
    }

    /**
     * Execute a call with circuit breaker and retry protection
     */
    public <T> T executeWithCircuitBreakerAndRetry(String serverId, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serverId);
        Retry retry = retryRegistry.retry(serverId);

        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);

        return decoratedSupplier.get();
    }

    /**
     * Execute an async call with circuit breaker protection
     */
    public <T> CompletionStage<T> executeAsyncWithCircuitBreaker(
            String serverId,
            Supplier<CompletionStage<T>> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serverId);

        return circuitBreaker.executeCompletionStage(supplier);
    }

    /**
     * Execute a call with circuit breaker, retry, and timeout protection
     */
    public <T> T executeWithFullProtection(String serverId, Callable<T> callable, T fallback) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serverId);
        Retry retry = retryRegistry.retry(serverId);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(serverId);

        try {
            Supplier<CompletableFuture<T>> futureSupplier = () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return callable.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            Callable<T> decoratedCallable = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
            decoratedCallable = CircuitBreaker.decorateCallable(circuitBreaker, decoratedCallable);
            Supplier<T> retryableSupplier = Retry.decorateCallable(retry, decoratedCallable);

            return retryableSupplier.get();
        } catch (Exception e) {
            log.warn("Circuit breaker fallback triggered for server {}: {}", serverId, e.getMessage());
            recordFallback(serverId);
            return fallback;
        }
    }

    /**
     * Get circuit breaker state for a specific server
     */
    public CircuitBreakerState getCircuitBreakerState(String serverId) {
        CircuitBreaker circuitBreaker = serverCircuitBreakers.get(serverId);
        if (circuitBreaker == null) {
            return new CircuitBreakerState(serverId, "UNKNOWN", 0, 0, 0);
        }

        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerState(
                serverId,
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls()
        );
    }

    /**
     * Get all circuit breaker states
     */
    public java.util.List<CircuitBreakerState> getAllCircuitBreakerStates() {
        return serverCircuitBreakers.entrySet().stream()
                .map(entry -> {
                    CircuitBreaker cb = entry.getValue();
                    CircuitBreaker.Metrics metrics = cb.getMetrics();
                    return new CircuitBreakerState(
                            entry.getKey(),
                            cb.getState().name(),
                            metrics.getFailureRate(),
                            metrics.getNumberOfSuccessfulCalls(),
                            metrics.getNumberOfFailedCalls()
                    );
                })
                .toList();
    }

    /**
     * Reset circuit breaker for a specific server
     */
    public void resetCircuitBreaker(String serverId) {
        CircuitBreaker circuitBreaker = serverCircuitBreakers.get(serverId);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            log.info("Circuit breaker reset for server: {}", serverId);
        }
    }

    /**
     * Force circuit breaker to open state
     */
    public void forceOpen(String serverId) {
        CircuitBreaker circuitBreaker = serverCircuitBreakers.get(serverId);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToOpenState();
            log.info("Circuit breaker forced to OPEN for server: {}", serverId);
        }
    }

    /**
     * Force circuit breaker to closed state
     */
    public void forceClosed(String serverId) {
        CircuitBreaker circuitBreaker = serverCircuitBreakers.get(serverId);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToClosedState();
            log.info("Circuit breaker forced to CLOSED for server: {}", serverId);
        }
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String serverId) {
        return serverCircuitBreakers.computeIfAbsent(serverId, id -> {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(id);

            // Register event listeners for monitoring
            cb.getEventPublisher()
                    .onStateTransition(event -> {
                        log.info("Circuit breaker state transition for {}: {} -> {}",
                                id, event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState());
                        meterRegistry.counter("mcp.circuit_breaker.state_transition",
                                "server", id,
                                "from", event.getStateTransition().getFromState().name(),
                                "to", event.getStateTransition().getToState().name())
                                .increment();
                    })
                    .onError(event -> {
                        log.debug("Circuit breaker error for {}: {}", id, event.getThrowable().getMessage());
                        meterRegistry.counter("mcp.circuit_breaker.error",
                                "server", id,
                                "exception", event.getThrowable().getClass().getSimpleName())
                                .increment();
                    })
                    .onSuccess(event -> {
                        meterRegistry.counter("mcp.circuit_breaker.success", "server", id).increment();
                    });

            return cb;
        });
    }

    private void recordFallback(String serverId) {
        meterRegistry.counter("mcp.circuit_breaker.fallback", "server", serverId).increment();
    }

    public record CircuitBreakerState(
            String serverId,
            String state,
            float failureRate,
            int successfulCalls,
            int failedCalls
    ) {}
}
