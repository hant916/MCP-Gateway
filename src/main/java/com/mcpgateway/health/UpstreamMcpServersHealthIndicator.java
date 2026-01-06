package com.mcpgateway.health;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.repository.McpServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Health Indicator for Upstream MCP Servers
 *
 * Checks connectivity to configured MCP servers and reports:
 * - Total servers configured
 * - Number of healthy servers
 * - Number of unhealthy servers
 * - Individual server status
 */
@Slf4j
@Component("upstreamMcpServers")
@RequiredArgsConstructor
public class UpstreamMcpServersHealthIndicator implements HealthIndicator {

    private final McpServerRepository mcpServerRepository;
    private final WebClient webClient;

    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final int ACCEPTABLE_FAILURE_THRESHOLD = 50; // 50% of servers must be healthy

    @Override
    public Health health() {
        try {
            List<McpServer> servers = mcpServerRepository.findAll();

            if (servers.isEmpty()) {
                return Health.up()
                        .withDetail("message", "No MCP servers configured")
                        .withDetail("totalServers", 0)
                        .build();
            }

            AtomicInteger healthyCount = new AtomicInteger(0);
            AtomicInteger unhealthyCount = new AtomicInteger(0);
            Map<String, String> serverStatuses = new HashMap<>();

            // Check each server's health
            servers.forEach(server -> {
                boolean isHealthy = checkServerHealth(server);
                if (isHealthy) {
                    healthyCount.incrementAndGet();
                    serverStatuses.put(server.getServiceName(), "UP");
                } else {
                    unhealthyCount.incrementAndGet();
                    serverStatuses.put(server.getServiceName(), "DOWN");
                }
            });

            int totalServers = servers.size();
            double healthyPercentage = (healthyCount.get() * 100.0) / totalServers;

            Health.Builder healthBuilder = healthyPercentage >= ACCEPTABLE_FAILURE_THRESHOLD
                    ? Health.up()
                    : Health.down();

            return healthBuilder
                    .withDetail("totalServers", totalServers)
                    .withDetail("healthyServers", healthyCount.get())
                    .withDetail("unhealthyServers", unhealthyCount.get())
                    .withDetail("healthyPercentage", String.format("%.2f%%", healthyPercentage))
                    .withDetail("servers", serverStatuses)
                    .build();

        } catch (Exception e) {
            log.error("Error checking upstream MCP servers health", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * Check individual server health with timeout
     */
    private boolean checkServerHealth(McpServer server) {
        try {
            // Simple connectivity check - attempt to reach the server
            String healthCheckUrl = buildHealthCheckUrl(server);

            Boolean isHealthy = webClient.get()
                    .uri(healthCheckUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode().is2xxSuccessful())
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .onErrorResume(error -> {
                        log.debug("Server {} health check failed: {}", server.getServiceName(), error.getMessage());
                        return Mono.just(false);
                    })
                    .block();

            return Boolean.TRUE.equals(isHealthy);

        } catch (Exception e) {
            log.debug("Server {} health check error: {}", server.getServiceName(), e.getMessage());
            return false;
        }
    }

    /**
     * Build health check URL for a server
     */
    private String buildHealthCheckUrl(McpServer server) {
        // Try common health check endpoints
        String baseUrl = server.getBaseUrl();

        // Most servers have a health or status endpoint
        if (baseUrl.endsWith("/")) {
            return baseUrl + "health";
        }
        return baseUrl + "/health";
    }
}
