package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Weighted Round Robin load balancing strategy
 * Distributes requests based on server weights using smooth weighted round-robin algorithm
 */
@Component
public class WeightedRoundRobinStrategy implements LoadBalancerStrategy {

    private final ConcurrentHashMap<String, AtomicInteger> currentWeights = new ConcurrentHashMap<>();

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

        // Calculate total weight
        int totalWeight = available.stream()
                .mapToInt(ServerInstance::getEffectiveWeight)
                .sum();

        if (totalWeight == 0) {
            // Fallback to simple round-robin if all weights are 0
            return available.get(0);
        }

        // Smooth weighted round-robin
        ServerInstance selected = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        for (ServerInstance instance : available) {
            String key = instance.getId();
            AtomicInteger currentWeight = currentWeights.computeIfAbsent(key, k -> new AtomicInteger(0));

            // Add effective weight to current weight
            int newWeight = currentWeight.addAndGet(instance.getEffectiveWeight());

            if (newWeight > maxCurrentWeight) {
                maxCurrentWeight = newWeight;
                selected = instance;
            }
        }

        if (selected != null) {
            // Subtract total weight from selected server's current weight
            currentWeights.get(selected.getId()).addAndGet(-totalWeight);
            selected.incrementConnections();
        }

        return selected;
    }

    @Override
    public String getName() {
        return "weighted-round-robin";
    }
}
