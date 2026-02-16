package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for cost summary and analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostSummaryDTO {
    private BigDecimal totalCost;
    private BigDecimal forecastedCost;
    private List<DailyCostDTO> dailyCosts;
    private List<ModelCostDTO> costsByModel;
    private CostTrendDTO trend;
    private DashboardWindowDTO window;
}
