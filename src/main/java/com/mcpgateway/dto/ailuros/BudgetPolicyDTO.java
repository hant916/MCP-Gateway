package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPolicyDTO {
    private UUID id;
    private String appId;
    private String env;
    private String route;

    private BigDecimal dailyUsdLimit;
    private BigDecimal monthlyUsdLimit;
    private BigDecimal forecastMonthlyUsdLimit;

    private Boolean enabled;
    private Instant createdTs;
    private Instant updatedTs;
}
