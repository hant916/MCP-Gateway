package com.mcpgateway.loadbalancer;

import java.util.List;

/**
 * Interface for load balancing strategies
 */
public interface LoadBalancerStrategy {

    /**
     * Select a server instance from the available list
     *
     * @param instances List of available server instances
     * @param context   Optional context for making routing decisions (e.g., client IP, session ID)
     * @return The selected server instance, or null if no instance is available
     */
    ServerInstance select(List<ServerInstance> instances, LoadBalancerContext context);

    /**
     * Get the strategy name
     */
    String getName();

    /**
     * Called when a request to a server succeeds
     */
    default void recordSuccess(ServerInstance instance, long responseTimeMs) {
        instance.recordSuccess(responseTimeMs);
    }

    /**
     * Called when a request to a server fails
     */
    default void recordFailure(ServerInstance instance) {
        instance.recordFailure();
    }
}
