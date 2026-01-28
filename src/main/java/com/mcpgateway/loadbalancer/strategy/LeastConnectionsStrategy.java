package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Least Connections load balancing strategy
 * Routes requests to the server with the fewest active connections
 */
@Component
public class LeastConnectionsStrategy implements LoadBalancerStrategy {

    @Override
    public ServerInstance select(List<ServerInstance> instances, LoadBalancerContext context) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        ServerInstance selected = instances.stream()
                .filter(ServerInstance::isAvailable)
                .min(Comparator.comparingInt(i -> i.getActiveConnections().get()))
                .orElse(null);

        if (selected != null) {
            selected.incrementConnections();
        }

        return selected;
    }

    @Override
    public String getName() {
        return "least-connections";
    }
}
