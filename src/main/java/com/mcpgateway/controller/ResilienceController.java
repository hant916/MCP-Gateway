package com.mcpgateway.controller;

import com.mcpgateway.circuitbreaker.FallbackRegistry;
import com.mcpgateway.circuitbreaker.ResilienceService;
import com.mcpgateway.integration.ResilientMcpClient;
import com.mcpgateway.loadbalancer.LoadBalancerService;
import com.mcpgateway.tracing.TracingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Resilience Controller - Ralph Loop Enhanced.
 *
 * Provides unified API for managing all resilience features:
 * - Circuit Breaker status and control
 * - Load Balancer pools and strategies
 * - Distributed Tracing status
 * - Combined health and statistics
 */
@RestController
@RequestMapping("/api/v1/resilience")
@RequiredArgsConstructor
@Tag(name = "Resilience", description = "Unified resilience management - Circuit Breaker, Load Balancer, Tracing")
public class ResilienceController {

    private final ResilienceService resilienceService;
    private final LoadBalancerService loadBalancerService;
    private final TracingService tracingService;
    private final FallbackRegistry fallbackRegistry;
    private final ResilientMcpClient resilientMcpClient;

    /**
     * Get overall resilience status.
     */
    @GetMapping("/status")
    @Operation(summary = "Get overall resilience status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        // Circuit breaker stats
        status.put("circuitBreakers", resilienceService.getAllStats());

        // Load balancer stats
        status.put("loadBalancer", Map.of(
                "pools", loadBalancerService.getPoolStatistics(),
                "strategies", loadBalancerService.getAvailableStrategies()
        ));

        // Tracing status
        status.put("tracing", Map.of(
                "enabled", tracingService.isEnabled(),
                "currentTraceId", tracingService.getCurrentTraceId() != null ?
                        tracingService.getCurrentTraceId() : "N/A"
        ));

        // Fallback registry
        status.put("fallbacks", fallbackRegistry.getAllNames());

        return ResponseEntity.ok(status);
    }

    /**
     * Get detailed statistics for a specific component.
     */
    @GetMapping("/stats/{name}")
    @Operation(summary = "Get detailed statistics for a component")
    public ResponseEntity<Map<String, Object>> getComponentStats(@PathVariable String name) {
        Map<String, Object> stats = new HashMap<>();

        // Circuit breaker stats
        ResilienceService.ResilienceStats cbStats = resilienceService.getStats(name);
        stats.put("circuitBreaker", cbStats);

        // Load balancer stats (if it's a pool name)
        LoadBalancerService.PoolStatistics lbStats = loadBalancerService.getPoolStatistics(name);
        if (lbStats != null) {
            stats.put("loadBalancer", lbStats);
        }

        // Combined stats
        stats.put("combined", resilientMcpClient.getCombinedStats(name));

        return ResponseEntity.ok(stats);
    }

    /**
     * Reset all resilience components for a name.
     */
    @PostMapping("/{name}/reset")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset all resilience components")
    public ResponseEntity<Map<String, String>> reset(@PathVariable String name) {
        resilienceService.reset(name);
        return ResponseEntity.ok(Map.of(
                "message", "Resilience components reset",
                "name", name
        ));
    }

    /**
     * Update resilience configuration.
     */
    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update resilience configuration")
    public ResponseEntity<Map<String, String>> updateConfig(@RequestBody ResilienceConfig config) {
        // Update load balancer strategy if provided
        if (config.getPoolName() != null && config.getStrategy() != null) {
            loadBalancerService.setStrategy(config.getPoolName(), config.getStrategy());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Configuration updated",
                "pool", config.getPoolName() != null ? config.getPoolName() : "N/A",
                "strategy", config.getStrategy() != null ? config.getStrategy() : "N/A"
        ));
    }

    /**
     * Get available strategies.
     */
    @GetMapping("/strategies")
    @Operation(summary = "Get all available load balancing strategies")
    public ResponseEntity<Set<String>> getStrategies() {
        return ResponseEntity.ok(loadBalancerService.getAvailableStrategies());
    }

    /**
     * Health check for all resilience components.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check for resilience components")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        boolean allHealthy = true;

        // Check circuit breakers
        Map<String, ResilienceService.ResilienceStats> cbStats = resilienceService.getAllStats();
        long openCircuits = cbStats.values().stream()
                .filter(s -> "OPEN".equals(s.circuitState()))
                .count();

        health.put("circuitBreakers", Map.of(
                "total", cbStats.size(),
                "open", openCircuits,
                "healthy", openCircuits == 0
        ));

        if (openCircuits > 0) {
            allHealthy = false;
        }

        // Check load balancer pools
        Map<String, LoadBalancerService.PoolStatistics> poolStats = loadBalancerService.getPoolStatistics();
        long unhealthyPools = poolStats.values().stream()
                .filter(s -> s.healthyInstances() == 0)
                .count();

        health.put("loadBalancer", Map.of(
                "pools", poolStats.size(),
                "unhealthyPools", unhealthyPools,
                "healthy", unhealthyPools == 0
        ));

        if (unhealthyPools > 0 && poolStats.size() > 0) {
            allHealthy = false;
        }

        // Check tracing
        health.put("tracing", Map.of(
                "enabled", tracingService.isEnabled(),
                "healthy", true
        ));

        health.put("overall", Map.of(
                "healthy", allHealthy,
                "status", allHealthy ? "UP" : "DEGRADED"
        ));

        return allHealthy ? ResponseEntity.ok(health) :
                ResponseEntity.status(503).body(health);
    }

    /**
     * Configuration DTO.
     */
    @Data
    public static class ResilienceConfig {
        private String poolName;
        private String strategy;
        private Integer circuitBreakerFailureThreshold;
        private Integer bulkheadMaxConcurrent;
        private Integer rateLimitPerSecond;
    }
}
