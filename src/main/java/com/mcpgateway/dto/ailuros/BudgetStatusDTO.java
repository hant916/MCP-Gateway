package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStatusDTO {
    private String appId;
    private String env;
    private String range;
    private DashboardWindowDTO window;

    private BigDecimal monthToDateCostUsd;
    private BigDecimal monthlyLimitUsd;
    private BigDecimal forecastMonthlyUsd;
    private Long exceededCount;

    @Builder.Default
    private List<TimeseriesPointDTO> dailyCost = new ArrayList<>();
}
