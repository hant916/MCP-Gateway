package com.mcpgateway.integration;

import com.mcpgateway.circuitbreaker.Fallback;
import com.mcpgateway.circuitbreaker.ResilienceService;
import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerService;
import com.mcpgateway.loadbalancer.ServerInstance;
import com.mcpgateway.tracing.TracePropagator;
import com.mcpgateway.tracing.TracingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Resilient MCP Client - Ralph Loop Integration.
 *
 * Combines all three high-priority features:
 * 1. Circuit Breaker - Prevents cascade failures
 * 2. Load Balancer - Distributes requests across healthy instances
 * 3. Distributed Tracing - Traces requests across services
 *
 * Usage:
 * <pre>
 * McpResponse response = resilientMcpClient.execute(
 *     "mcp-server-pool",
 *     instance -> callMcpServer(instance),
 *     McpResponse.empty()
 * );
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientMcpClient {

    private final ResilienceService resilienceService;
    private final LoadBalancerService loadBalancerService;
    private final TracingService tracingService;
    private final TracePropagator tracePropagator;
    private final MeterRegistry meterRegistry;

    /**
     * Execute a request with full resilience stack.
     *
     * @param poolName The load balancer pool name
     * @param action The action to execute against a server instance
     * @param fallback The fallback value if all attempts fail
     * @return The result or fallback
     */
    public <T> T execute(String poolName, Function<ServerInstance, T> action, T fallback) {
        return execute(poolName, action, fallback, LoadBalancerContext.empty());
    }

    /**
     * Execute with context for load balancing decisions.
     */
    public <T> T execute(String poolName, Function<ServerInstance, T> action, T fallback,
                         LoadBalancerContext context) {

        return tracingService.executeInSpan("mcp.client.execute", Map.of(
                "pool", poolName
        ), () -> {
            // Select instance using load balancer
            ServerInstance instance = loadBalancerService.selectInstance(poolName, context);
            if (instance == null) {
                log.warn("No instance available in pool: {}", poolName);
                tracingService.tagCurrentSpan("error", "no_instance_available");
                meterRegistry.counter("mcp.client.no_instance", "pool", poolName).increment();
                return fallback;
            }

            // Create circuit breaker name based on pool and instance
            String circuitBreakerName = poolName + ":" + instance.getId();

            tracingService.tagCurrentSpan("instance.id", instance.getId());
            tracingService.tagCurrentSpan("instance.host", instance.getHost());

            // Set up trace propagation
            tracePropagator.setMdcContext();

            Timer.Sample sample = Timer.start(meterRegistry);
            long startTime = System.currentTimeMillis();

            try {
                // Execute with resilience protection
                T result = resilienceService.execute(
                        circuitBreakerName,
                        () -> {
                            tracingService.addEvent("executing_request");
                            return action.apply(instance);
                        },
                        Fallback.of("fallback", fallback)
                );

                long responseTime = System.currentTimeMillis() - startTime;

                // Record success
                loadBalancerService.recordSuccess(poolName, instance.getId(), responseTime);
                tracingService.tagCurrentSpan("status", "success");
                tracingService.tagCurrentSpan("response_time_ms", String.valueOf(responseTime));

                sample.stop(Timer.builder("mcp.client.request")
                        .tag("pool", poolName)
                        .tag("instance", instance.getId())
                        .tag("status", "success")
                        .register(meterRegistry));

                return result;

            } catch (Exception e) {
                // Record failure
                loadBalancerService.recordFailure(poolName, instance.getId());
                tracingService.tagCurrentSpan("status", "failure");
                tracingService.tagCurrentSpan("error", e.getClass().getSimpleName());
                tracingService.recordError(e);

                sample.stop(Timer.builder("mcp.client.request")
                        .tag("pool", poolName)
                        .tag("instance", instance.getId())
                        .tag("status", "failure")
                        .register(meterRegistry));

                log.warn("Request failed for instance {} in pool {}: {}",
                        instance.getId(), poolName, e.getMessage());

                // Try another instance (retry with different instance)
                return retryWithDifferentInstance(poolName, action, fallback, context, instance.getId());
            } finally {
                tracePropagator.clearMdcContext();
            }
        });
    }

    /**
     * Retry with a different instance after failure.
     */
    private <T> T retryWithDifferentInstance(String poolName, Function<ServerInstance, T> action,
                                              T fallback, LoadBalancerContext context,
                                              String failedInstanceId) {

        tracingService.addEvent("retry_different_instance");

        // Select a different instance
        ServerInstance instance = loadBalancerService.selectInstance(poolName, context);
        if (instance == null || instance.getId().equals(failedInstanceId)) {
            log.warn("No alternative instance available in pool: {}", poolName);
            return fallback;
        }

        String circuitBreakerName = poolName + ":" + instance.getId();
        tracingService.tagCurrentSpan("retry.instance", instance.getId());

        try {
            return resilienceService.executeWithCircuitBreaker(
                    circuitBreakerName,
                    () -> action.apply(instance)
            );
        } catch (Exception e) {
            loadBalancerService.recordFailure(poolName, instance.getId());
            log.warn("Retry failed for instance {} in pool {}: {}",
                    instance.getId(), poolName, e.getMessage());
            return fallback;
        }
    }

    /**
     * Execute with custom request options.
     */
    public <T> T execute(RequestOptions options, Function<ServerInstance, T> action) {
        LoadBalancerContext context = LoadBalancerContext.builder()
                .clientIp(options.getClientIp())
                .sessionId(options.getSessionId())
                .userId(options.getUserId())
                .preferredZone(options.getPreferredZone())
                .build();

        return execute(options.getPoolName(), action, options.getFallback(), context);
    }

    /**
     * Get headers with trace context for outgoing requests.
     */
    public Map<String, String> getTracingHeaders() {
        Map<String, String> headers = new HashMap<>();
        tracePropagator.inject(headers);
        return headers;
    }

    /**
     * Check if pool is healthy.
     */
    public boolean isPoolHealthy(String poolName) {
        LoadBalancerService.PoolStatistics stats = loadBalancerService.getPoolStatistics(poolName);
        if (stats == null) {
            return false;
        }
        return stats.healthyInstances() > 0 && stats.getSuccessRate() > 0.5;
    }

    /**
     * Get combined statistics.
     */
    public CombinedStats getCombinedStats(String poolName) {
        LoadBalancerService.PoolStatistics lbStats = loadBalancerService.getPoolStatistics(poolName);

        return CombinedStats.builder()
                .poolName(poolName)
                .totalInstances(lbStats != null ? lbStats.totalInstances() : 0)
                .healthyInstances(lbStats != null ? lbStats.healthyInstances() : 0)
                .successRate(lbStats != null ? lbStats.getSuccessRate() : 0)
                .avgResponseTimeMs(lbStats != null ? lbStats.averageResponseTimeMs() : 0)
                .tracingEnabled(tracingService.isEnabled())
                .currentTraceId(tracingService.getCurrentTraceId())
                .build();
    }

    /**
     * Request options for customizing execution.
     */
    @Data
    @Builder
    public static class RequestOptions {
        private String poolName;
        private String clientIp;
        private String sessionId;
        private String userId;
        private String preferredZone;
        private Object fallback;
        private Duration timeout;
        private int maxRetries;

        @SuppressWarnings("unchecked")
        public <T> T getFallback() {
            return (T) fallback;
        }
    }

    /**
     * Combined statistics from all components.
     */
    @Data
    @Builder
    public static class CombinedStats {
        private String poolName;
        private int totalInstances;
        private int healthyInstances;
        private double successRate;
        private double avgResponseTimeMs;
        private boolean tracingEnabled;
        private String currentTraceId;
    }
}
