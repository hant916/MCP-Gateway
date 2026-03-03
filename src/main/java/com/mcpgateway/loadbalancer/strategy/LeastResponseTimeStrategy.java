package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Least Response Time load balancing strategy
 * Routes requests to the server with the lowest average response time
 */
@Component
public class LeastResponseTimeStrategy implements LoadBalancerStrategy {

    @Override
    public ServerInstance select(List<ServerInstance> instances, LoadBalancerContext context) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        List<ServerInstance> available = instances.stream()
                .filter(ServerInstance::isAvailable)
                .toList();

        if (available.isEmpty()) {
            return null;
        }

        // Calculate score: lower is better
        // Score = (activeConnections + 1) * averageResponseTime
        // This balances both current load and historical performance
        ServerInstance selected = available.stream()
                .min(Comparator.comparingDouble(this::calculateScore))
                .orElse(available.get(0));

        selected.incrementConnections();
        return selected;
    }

    private double calculateScore(ServerInstance instance) {
        double avgResponseTime = instance.getAverageResponseTime();
        int activeConnections = instance.getActiveConnections().get();

        // If no data yet, return a default score based on weight
        if (avgResponseTime == 0) {
            return 1000.0 / instance.getWeight();
        }

        // Score = (connections + 1) * avgResponseTime / weight
        return (activeConnections + 1) * avgResponseTime / instance.getWeight();
    }

    @Override
    public String getName() {
        return "least-response-time";
    }
}
