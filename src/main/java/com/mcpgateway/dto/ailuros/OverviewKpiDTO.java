package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for overview page KPIs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewKpiDTO {
    // Reliability
    private BigDecimal reliabilityScore; // 0-100
    private Long totalCalls;
    private Long errorCalls;
    private BigDecimal errorRate;

    // Flagged calls
    private Long flaggedCallsCount;
    private BigDecimal flaggedPercentage;

    // Cost
    private BigDecimal totalCost;
    private BigDecimal costTrend; // percentage change

    // Performance
    private Double p95LatencyMs;
    private Double avgLatencyMs;

    // Recent flags
    private List<CallListDTO> recentFlaggedCalls;

    // Trends
    private List<DailyCostDTO> costOverTime;
    private List<DailyDriftDTO> driftOverTime;
}
