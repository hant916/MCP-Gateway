package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancing strategy
 * Distributes requests evenly across all available servers in circular order
 */
@Component
public class RoundRobinStrategy implements LoadBalancerStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

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

        int index = Math.abs(counter.getAndIncrement() % available.size());
        ServerInstance selected = available.get(index);
        selected.incrementConnections();
        return selected;
    }

    @Override
    public String getName() {
        return "round-robin";
    }
}
