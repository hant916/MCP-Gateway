package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for cost trend analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostTrendDTO {
    private BigDecimal currentPeriodCost;
    private BigDecimal previousPeriodCost;
    private BigDecimal changeAmount;
    private BigDecimal changePercentage;
    private String trend; // "up", "down", "stable"
}
