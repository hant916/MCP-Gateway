package com.mcpgateway.loadbalancer;

import com.mcpgateway.loadbalancer.strategy.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Load Balancer Service for MCP Gateway
 * Manages server pools and provides load balancing across MCP server instances
 */
@Slf4j
@Service
public class LoadBalancerService {

    private final Map<String, LoadBalancerStrategy> strategies;
    private final MeterRegistry meterRegistry;

    // Server pools: poolName -> List of server instances
    private final ConcurrentHashMap<String, List<ServerInstance>> serverPools = new ConcurrentHashMap<>();

    // Active strategy per pool
    private final ConcurrentHashMap<String, String> poolStrategies = new ConcurrentHashMap<>();

    @Value("${mcp.load-balancer.default-strategy:round-robin}")
    private String defaultStrategy;

    @Value("${mcp.load-balancer.health-check.enabled:true}")
    private boolean healthCheckEnabled;

    @Value("${mcp.load-balancer.health-check.interval-seconds:30}")
    private int healthCheckIntervalSeconds;

    @Value("${mcp.load-balancer.health-check.unhealthy-threshold:3}")
    private int unhealthyThreshold;

    public LoadBalancerService(
            RoundRobinStrategy roundRobinStrategy,
            WeightedRoundRobinStrategy weightedRoundRobinStrategy,
            LeastConnectionsStrategy leastConnectionsStrategy,
            IPHashStrategy ipHashStrategy,
            LeastResponseTimeStrategy leastResponseTimeStrategy,
            RandomStrategy randomStrategy,
            MeterRegistry meterRegistry) {

        this.meterRegistry = meterRegistry;

        this.strategies = Map.of(
                "round-robin", roundRobinStrategy,
                "weighted-round-robin", weightedRoundRobinStrategy,
                "least-connections", leastConnectionsStrategy,
                "ip-hash", ipHashStrategy,
                "least-response-time", leastResponseTimeStrategy,
                "random", randomStrategy
        );
    }

    /**
     * Register a server pool with instances
     */
    public void registerPool(String poolName, List<ServerInstance> instances) {
        serverPools.put(poolName, new ArrayList<>(instances));
        log.info("Registered server pool '{}' with {} instances", poolName, instances.size());
    }

    /**
     * Add a server instance to a pool
     */
    public void addInstance(String poolName, ServerInstance instance) {
        serverPools.computeIfAbsent(poolName, k -> new ArrayList<>()).add(instance);
        log.info("Added instance {} to pool '{}'", instance.getId(), poolName);
        meterRegistry.gauge("mcp.loadbalancer.pool.size",
                Tags.of("pool", poolName),
                serverPools.get(poolName).size());
    }

    /**
     * Remove a server instance from a pool
     */
    public void removeInstance(String poolName, String instanceId) {
        List<ServerInstance> pool = serverPools.get(poolName);
        if (pool != null) {
            pool.removeIf(i -> i.getId().equals(instanceId));
            log.info("Removed instance {} from pool '{}'", instanceId, poolName);
        }
    }

    /**
     * Set the load balancing strategy for a pool
     */
    public void setStrategy(String poolName, String strategyName) {
        if (!strategies.containsKey(strategyName)) {
            throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        poolStrategies.put(poolName, strategyName);
        log.info("Set strategy '{}' for pool '{}'", strategyName, poolName);
    }

    /**
     * Select a server instance using load balancing
     */
    public ServerInstance selectInstance(String poolName) {
        return selectInstance(poolName, LoadBalancerContext.empty());
    }

    /**
     * Select a server instance with context
     */
    public ServerInstance selectInstance(String poolName, LoadBalancerContext context) {
        List<ServerInstance> pool = serverPools.get(poolName);
        if (pool == null || pool.isEmpty()) {
            log.warn("No instances available in pool '{}'", poolName);
            meterRegistry.counter("mcp.loadbalancer.no_instance", "pool", poolName).increment();
            return null;
        }

        String strategyName = poolStrategies.getOrDefault(poolName, defaultStrategy);
        LoadBalancerStrategy strategy = strategies.get(strategyName);

        Timer.Sample sample = Timer.start(meterRegistry);
        ServerInstance selected = strategy.select(pool, context);
        sample.stop(Timer.builder("mcp.loadbalancer.select")
                .tag("pool", poolName)
                .tag("strategy", strategyName)
                .register(meterRegistry));

        if (selected != null) {
            log.debug("Selected instance {} from pool '{}' using strategy '{}'",
                    selected.getId(), poolName, strategyName);
            meterRegistry.counter("mcp.loadbalancer.selected",
                    "pool", poolName,
                    "instance", selected.getId(),
                    "strategy", strategyName).increment();
        }

        return selected;
    }

    /**
     * Record successful request to an instance
     */
    public void recordSuccess(String poolName, String instanceId, long responseTimeMs) {
        List<ServerInstance> pool = serverPools.get(poolName);
        if (pool != null) {
            pool.stream()
                    .filter(i -> i.getId().equals(instanceId))
                    .findFirst()
                    .ifPresent(instance -> {
                        String strategyName = poolStrategies.getOrDefault(poolName, defaultStrategy);
                        strategies.get(strategyName).recordSuccess(instance, responseTimeMs);
                        meterRegistry.timer("mcp.loadbalancer.response_time",
                                "pool", poolName,
                                "instance", instanceId)
                                .record(Duration.ofMillis(responseTimeMs));
                    });
        }
    }

    /**
     * Record failed request to an instance
     */
    public void recordFailure(String poolName, String instanceId) {
        List<ServerInstance> pool = serverPools.get(poolName);
        if (pool != null) {
            pool.stream()
                    .filter(i -> i.getId().equals(instanceId))
                    .findFirst()
                    .ifPresent(instance -> {
                        String strategyName = poolStrategies.getOrDefault(poolName, defaultStrategy);
                        strategies.get(strategyName).recordFailure(instance);
                        meterRegistry.counter("mcp.loadbalancer.failure",
                                "pool", poolName,
                                "instance", instanceId).increment();
                    });
        }
    }

    /**
     * Get all pool statistics
     */
    public Map<String, PoolStatistics> getPoolStatistics() {
        Map<String, PoolStatistics> stats = new HashMap<>();
        for (Map.Entry<String, List<ServerInstance>> entry : serverPools.entrySet()) {
            stats.put(entry.getKey(), calculatePoolStatistics(entry.getKey(), entry.getValue()));
        }
        return stats;
    }

    /**
     * Get statistics for a specific pool
     */
    public PoolStatistics getPoolStatistics(String poolName) {
        List<ServerInstance> pool = serverPools.get(poolName);
        if (pool == null) {
            return null;
        }
        return calculatePoolStatistics(poolName, pool);
    }

    /**
     * Get all available strategies
     */
    public Set<String> getAvailableStrategies() {
        return strategies.keySet();
    }

    /**
     * Health check for all pools
     */
    @Scheduled(fixedRateString = "${mcp.load-balancer.health-check.interval-seconds:30}000")
    public void performHealthChecks() {
        if (!healthCheckEnabled) {
            return;
        }

        log.debug("Performing health checks on all server pools");
        for (Map.Entry<String, List<ServerInstance>> entry : serverPools.entrySet()) {
            for (ServerInstance instance : entry.getValue()) {
                checkInstanceHealth(entry.getKey(), instance);
            }
        }
    }

    private void checkInstanceHealth(String poolName, ServerInstance instance) {
        // Mark unhealthy if consecutive failures exceed threshold
        if (instance.getConsecutiveFailures() >= unhealthyThreshold) {
            if (instance.isHealthy()) {
                instance.markUnhealthy();
                log.warn("Instance {} in pool '{}' marked unhealthy after {} consecutive failures",
                        instance.getId(), poolName, instance.getConsecutiveFailures());
                meterRegistry.counter("mcp.loadbalancer.health.unhealthy",
                        "pool", poolName,
                        "instance", instance.getId()).increment();
            }
        }

        // Auto-recover if last successful request was recent
        if (!instance.isHealthy() && instance.getLastSuccessfulRequest() != null) {
            Instant lastSuccess = instance.getLastSuccessfulRequest();
            if (Duration.between(lastSuccess, Instant.now()).toMinutes() < 5) {
                instance.markHealthy();
                log.info("Instance {} in pool '{}' marked healthy again", instance.getId(), poolName);
                meterRegistry.counter("mcp.loadbalancer.health.recovered",
                        "pool", poolName,
                        "instance", instance.getId()).increment();
            }
        }
    }

    private PoolStatistics calculatePoolStatistics(String poolName, List<ServerInstance> pool) {
        long totalRequests = pool.stream().mapToLong(i -> i.getTotalRequests().get()).sum();
        long successfulRequests = pool.stream().mapToLong(i -> i.getSuccessfulRequests().get()).sum();
        long failedRequests = pool.stream().mapToLong(i -> i.getFailedRequests().get()).sum();
        int totalConnections = pool.stream().mapToInt(i -> i.getActiveConnections().get()).sum();
        long healthyCount = pool.stream().filter(ServerInstance::isHealthy).count();

        double avgResponseTime = pool.stream()
                .mapToDouble(ServerInstance::getAverageResponseTime)
                .average()
                .orElse(0);

        return new PoolStatistics(
                poolName,
                poolStrategies.getOrDefault(poolName, defaultStrategy),
                pool.size(),
                (int) healthyCount,
                totalConnections,
                totalRequests,
                successfulRequests,
                failedRequests,
                avgResponseTime
        );
    }

    public record PoolStatistics(
            String poolName,
            String strategy,
            int totalInstances,
            int healthyInstances,
            int activeConnections,
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double averageResponseTimeMs
    ) {
        public double getSuccessRate() {
            if (totalRequests == 0) return 1.0;
            return (double) successfulRequests / totalRequests;
        }
    }

    private static class Tags {
        static io.micrometer.core.instrument.Tags of(String... keyValues) {
            return io.micrometer.core.instrument.Tags.of(keyValues);
        }
    }
}
