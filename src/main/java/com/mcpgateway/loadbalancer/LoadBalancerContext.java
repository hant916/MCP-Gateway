package com.mcpgateway.loadbalancer;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Context information for load balancing decisions
 */
@Data
@Builder
public class LoadBalancerContext {

    private String clientIp;
    private String sessionId;
    private String userId;
    private String requestPath;
    private String requestMethod;
    private String preferredZone;
    private String preferredVersion;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public static LoadBalancerContext empty() {
        return LoadBalancerContext.builder().build();
    }

    public static LoadBalancerContext forClient(String clientIp) {
        return LoadBalancerContext.builder()
                .clientIp(clientIp)
                .build();
    }

    public static LoadBalancerContext forSession(String sessionId) {
        return LoadBalancerContext.builder()
                .sessionId(sessionId)
                .build();
    }
}
