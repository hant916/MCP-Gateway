package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for cost by model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCostDTO {
    private String model;
    private BigDecimal cost;
    private Long callCount;
    private BigDecimal percentage;
}
