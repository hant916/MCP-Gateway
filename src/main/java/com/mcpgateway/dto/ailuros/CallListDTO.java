package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for call list view
 * Optimized for table display - excludes full request/response text
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallListDTO {
    private UUID id;
    private String traceId;
    private String projectKey;
    private String env;
    private String status;
    private String provider;
    private String model;
    private String promptRef;
    private Integer tokensTotal;
    private BigDecimal costEstimateUsd;
    private Integer latencyMs;
    private Instant createdAt;
    private Boolean isFlagged;
    private Long flagCount;
}
