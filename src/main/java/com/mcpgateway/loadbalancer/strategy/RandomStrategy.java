package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balancing strategy
 * Randomly selects a server from available instances
 */
@Component
public class RandomStrategy implements LoadBalancerStrategy {

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

        int index = ThreadLocalRandom.current().nextInt(available.size());
        ServerInstance selected = available.get(index);
        selected.incrementConnections();
        return selected;
    }

    @Override
    public String getName() {
        return "random";
    }
}
