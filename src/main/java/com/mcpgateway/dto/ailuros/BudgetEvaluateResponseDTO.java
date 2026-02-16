package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetEvaluateResponseDTO {
    private Instant evaluatedAt;
    private String trigger;

    @Builder.Default
    private List<BudgetEvalResultDTO> evaluations = new ArrayList<>();
}
