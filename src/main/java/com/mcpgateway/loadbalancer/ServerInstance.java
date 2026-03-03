package com.mcpgateway.loadbalancer;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a server instance in the load balancer
 */
@Data
@NoArgsConstructor
public class ServerInstance {

    private String id;
    private String host;
    private int port;
    private String protocol = "http";
    private int weight = 1;
    private boolean healthy = true;
    private String zone;
    private String version;

    // Metrics
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile Instant lastSuccessfulRequest;
    private volatile int consecutiveFailures = 0;

    public ServerInstance(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public ServerInstance(String id, String host, int port, int weight) {
        this(id, host, port);
        this.weight = weight;
    }

    public String getUrl() {
        return String.format("%s://%s:%d", protocol, host, port);
    }

    public void incrementConnections() {
        activeConnections.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void decrementConnections() {
        activeConnections.decrementAndGet();
    }

    public void recordSuccess(long responseTimeMs) {
        successfulRequests.incrementAndGet();
        totalResponseTimeMs.addAndGet(responseTimeMs);
        lastSuccessfulRequest = Instant.now();
        consecutiveFailures = 0;
        decrementConnections();
    }

    public void recordFailure() {
        failedRequests.incrementAndGet();
        consecutiveFailures++;
        decrementConnections();
    }

    public double getAverageResponseTime() {
        long successful = successfulRequests.get();
        if (successful == 0) {
            return 0;
        }
        return (double) totalResponseTimeMs.get() / successful;
    }

    public double getSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 1.0;
        }
        return (double) successfulRequests.get() / total;
    }

    public int getEffectiveWeight() {
        // Reduce weight based on consecutive failures
        if (consecutiveFailures > 0) {
            return Math.max(1, weight - consecutiveFailures);
        }
        return weight;
    }

    public boolean isAvailable() {
        return healthy && consecutiveFailures < 5;
    }

    public void markHealthy() {
        this.healthy = true;
        this.lastHealthCheck = Instant.now();
    }

    public void markUnhealthy() {
        this.healthy = false;
        this.lastHealthCheck = Instant.now();
    }
}
