package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Zone-Aware load balancing strategy.
 *
 * Prefers instances in the same zone/region as the gateway to minimize latency.
 * Falls back to cross-zone instances if no local instances are available.
 *
 * Configuration:
 * - mcp.load-balancer.zone: Current zone identifier
 * - mcp.load-balancer.zone-affinity-threshold: Min healthy instances in zone before fallback
 */
@Slf4j
@Component
public class ZoneAwareStrategy implements LoadBalancerStrategy {

    @Value("${mcp.load-balancer.zone:default}")
    private String currentZone;

    @Value("${mcp.load-balancer.zone-affinity-threshold:1}")
    private int zoneAffinityThreshold;

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

        // Determine preferred zone
        String preferredZone = context.getPreferredZone() != null
                ? context.getPreferredZone()
                : currentZone;

        // Filter instances in preferred zone
        List<ServerInstance> zoneInstances = available.stream()
                .filter(i -> preferredZone.equals(i.getZone()))
                .toList();

        // Use zone instances if threshold met
        if (zoneInstances.size() >= zoneAffinityThreshold) {
            ServerInstance selected = selectRoundRobin(zoneInstances);
            log.debug("Selected instance {} from zone {}", selected.getId(), preferredZone);
            return selected;
        }

        // Fallback to any available instance
        log.debug("Zone {} has {} instances (threshold: {}), falling back to cross-zone",
                preferredZone, zoneInstances.size(), zoneAffinityThreshold);
        return selectRoundRobin(available);
    }

    private ServerInstance selectRoundRobin(List<ServerInstance> instances) {
        int index = Math.abs(counter.getAndIncrement() % instances.size());
        ServerInstance selected = instances.get(index);
        selected.incrementConnections();
        return selected;
    }

    @Override
    public String getName() {
        return "zone-aware";
    }

    /**
     * Get current zone.
     */
    public String getCurrentZone() {
        return currentZone;
    }

    /**
     * Set current zone (for testing).
     */
    public void setCurrentZone(String zone) {
        this.currentZone = zone;
    }
}
