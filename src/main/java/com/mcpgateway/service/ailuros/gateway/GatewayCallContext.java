package com.mcpgateway.service.ailuros.gateway;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Builder
public class GatewayCallContext {
    private final String eventVersion;
    private final String traceId;
    private final String spanId;

    private final String appId;
    private final String env;
    private final String route;
    private final String provider;
    private final String model;
    private final String promptVersion;
    private final String userTier;
    private final boolean streaming;

    private final Instant requestTs;
    private final String promptHash;

    @Builder.Default
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    private final AtomicBoolean finalized = new AtomicBoolean(false);

    public boolean markFinalized() {
        return finalized.compareAndSet(false, true);
    }
}
