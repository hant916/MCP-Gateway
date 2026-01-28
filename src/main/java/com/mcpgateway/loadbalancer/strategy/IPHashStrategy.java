package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IP Hash load balancing strategy
 * Routes requests from the same client IP to the same server (session persistence)
 */
@Component
public class IPHashStrategy implements LoadBalancerStrategy {

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

        String hashKey = getHashKey(context);
        int hash = consistentHash(hashKey);
        int index = Math.abs(hash % available.size());

        ServerInstance selected = available.get(index);
        selected.incrementConnections();
        return selected;
    }

    private String getHashKey(LoadBalancerContext context) {
        if (context == null) {
            return "default";
        }

        // Prefer session ID for stickiness, then client IP
        if (context.getSessionId() != null && !context.getSessionId().isEmpty()) {
            return context.getSessionId();
        }
        if (context.getClientIp() != null && !context.getClientIp().isEmpty()) {
            return context.getClientIp();
        }
        if (context.getUserId() != null && !context.getUserId().isEmpty()) {
            return context.getUserId();
        }

        return "default";
    }

    /**
     * Consistent hash using FNV-1a algorithm
     */
    private int consistentHash(String key) {
        final int FNV_PRIME = 0x01000193;
        final int FNV_OFFSET_BASIS = 0x811c9dc5;

        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    @Override
    public String getName() {
        return "ip-hash";
    }
}
