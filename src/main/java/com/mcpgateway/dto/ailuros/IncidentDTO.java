package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDTO {
    private UUID id;
    private String traceId;
    private String spanId;

    private String appId;
    private String env;
    private String route;
    private String provider;
    private String model;
    private String promptVersion;

    private Boolean streaming;

    private String status;
    private String errorType;
    private Integer httpStatus;

    private Integer latencyMs;
    private BigDecimal costUsd;

    private Instant requestTs;
    private Instant responseTs;

    @Builder.Default
    private List<String> flags = new ArrayList<>();
}
