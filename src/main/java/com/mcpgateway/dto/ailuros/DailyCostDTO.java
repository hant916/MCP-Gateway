package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for daily cost aggregates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCostDTO {
    private LocalDate date;
    private BigDecimal cost;
    private Long callCount;
}
