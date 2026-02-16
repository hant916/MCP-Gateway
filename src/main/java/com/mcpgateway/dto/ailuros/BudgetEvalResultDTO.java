package com.mcpgateway.dto.ailuros;

import com.mcpgateway.domain.ailuros.AcBudgetEval;
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
public class BudgetEvalResultDTO {
    private UUID id;
    private String appId;
    private String env;
    private String route;

    private Instant windowStartTs;
    private Instant windowEndTs;

    private BigDecimal costUsd;
    private BigDecimal limitUsd;
    private BigDecimal forecastMonthlyUsd;
    private AcBudgetEval.Status status;
    private Instant createdTs;
}
