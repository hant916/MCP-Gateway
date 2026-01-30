package com.mcpgateway.loadbalancer.strategy;

import com.mcpgateway.loadbalancer.LoadBalancerContext;
import com.mcpgateway.loadbalancer.LoadBalancerStrategy;
import com.mcpgateway.loadbalancer.ServerInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent Hashing load balancing strategy.
 *
 * Benefits:
 * - Minimizes redistribution when nodes are added/removed
 * - Good cache locality (same key always goes to same server)
 * - Supports virtual nodes for better distribution
 *
 * Use cases:
 * - Session affinity
 * - Caching layers
 * - Stateful services
 */
@Slf4j
@Component
public class ConsistentHashStrategy implements LoadBalancerStrategy {

    private static final int VIRTUAL_NODES = 150;

    // Thread-safe sorted map for the hash ring
    private final ConcurrentSkipListMap<Long, ServerInstance> hashRing = new ConcurrentSkipListMap<>();
    private final Map<String, List<Long>> instanceHashes = new HashMap<>();

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

        // Rebuild ring if needed (simple check - could be optimized)
        rebuildRingIfNeeded(available);

        // Get hash key from context
        String hashKey = getHashKey(context);
        long hash = hash(hashKey);

        // Find the first node >= hash
        Map.Entry<Long, ServerInstance> entry = hashRing.ceilingEntry(hash);
        if (entry == null) {
            // Wrap around to the first node
            entry = hashRing.firstEntry();
        }

        if (entry != null) {
            ServerInstance selected = entry.getValue();
            selected.incrementConnections();
            return selected;
        }

        // Fallback to first available
        ServerInstance selected = available.get(0);
        selected.incrementConnections();
        return selected;
    }

    @Override
    public String getName() {
        return "consistent-hash";
    }

    /**
     * Rebuild the hash ring if instances have changed.
     */
    private synchronized void rebuildRingIfNeeded(List<ServerInstance> instances) {
        Set<String> currentIds = new HashSet<>();
        for (ServerInstance instance : instances) {
            currentIds.add(instance.getId());
        }

        // Check if ring needs rebuilding
        boolean needsRebuild = instanceHashes.size() != instances.size() ||
                !instanceHashes.keySet().equals(currentIds);

        if (needsRebuild) {
            rebuildRing(instances);
        }
    }

    /**
     * Rebuild the entire hash ring.
     */
    private void rebuildRing(List<ServerInstance> instances) {
        log.debug("Rebuilding consistent hash ring with {} instances", instances.size());

        hashRing.clear();
        instanceHashes.clear();

        for (ServerInstance instance : instances) {
            List<Long> hashes = new ArrayList<>();

            // Add virtual nodes based on weight
            int virtualNodesCount = VIRTUAL_NODES * instance.getWeight();
            for (int i = 0; i < virtualNodesCount; i++) {
                String key = instance.getId() + "#" + i;
                long hash = hash(key);
                hashRing.put(hash, instance);
                hashes.add(hash);
            }

            instanceHashes.put(instance.getId(), hashes);
        }

        log.debug("Hash ring rebuilt with {} virtual nodes", hashRing.size());
    }

    /**
     * Get hash key from context.
     * Priority: sessionId > userId > clientIp > random
     */
    private String getHashKey(LoadBalancerContext context) {
        if (context.getSessionId() != null && !context.getSessionId().isEmpty()) {
            return context.getSessionId();
        }
        if (context.getUserId() != null && !context.getUserId().isEmpty()) {
            return context.getUserId();
        }
        if (context.getClientIp() != null && !context.getClientIp().isEmpty()) {
            return context.getClientIp();
        }
        // Fallback to random key
        return UUID.randomUUID().toString();
    }

    /**
     * Hash a string to a long value using MD5.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

            // Use first 8 bytes as a long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return key.hashCode();
        }
    }

    /**
     * Add a server instance to the ring.
     */
    public synchronized void addInstance(ServerInstance instance) {
        List<Long> hashes = new ArrayList<>();
        int virtualNodesCount = VIRTUAL_NODES * instance.getWeight();

        for (int i = 0; i < virtualNodesCount; i++) {
            String key = instance.getId() + "#" + i;
            long hash = hash(key);
            hashRing.put(hash, instance);
            hashes.add(hash);
        }

        instanceHashes.put(instance.getId(), hashes);
        log.debug("Added instance {} with {} virtual nodes", instance.getId(), virtualNodesCount);
    }

    /**
     * Remove a server instance from the ring.
     */
    public synchronized void removeInstance(String instanceId) {
        List<Long> hashes = instanceHashes.remove(instanceId);
        if (hashes != null) {
            for (Long hash : hashes) {
                hashRing.remove(hash);
            }
            log.debug("Removed instance {} with {} virtual nodes", instanceId, hashes.size());
        }
    }
}
