package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Investor and operator oriented summary metrics for one window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AilurosStatsDTO {
    private String appId;
    private String env;
    private String range;
    private DashboardWindowDTO window;

    private BigDecimal reliability;
    private Long totalCalls;
    private Long errorCalls;
    private BigDecimal errorRate;

    private Long flaggedCount;
    private BigDecimal flaggedRate;

    private BigDecimal totalCost;
    private Double p95LatencyMs;

    private String topRiskDriver;

    private FlagPolicyDTO flagPolicy;

    @Builder.Default
    private Map<String, String> kpiFormulas = new LinkedHashMap<>();
}
