package com.mcpgateway.stream.policy;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Cost budget constraints for streaming decisions.
 */
@Data
@Builder
public class CostBudget {

    /**
     * Maximum tokens allowed for this request.
     */
    private Integer maxTokens;

    /**
     * Maximum cost in credits/currency.
     */
    private BigDecimal maxCost;

    /**
     * Whether to allow expensive operations (e.g., persistence).
     */
    private boolean allowExpensiveOperations;

    /**
     * Priority level (higher = more resources allowed).
     */
    @Builder.Default
    private int priority = 0;

    public static CostBudget unlimited() {
        return CostBudget.builder()
                .maxTokens(null)
                .maxCost(null)
                .allowExpensiveOperations(true)
                .priority(10)
                .build();
    }

    public static CostBudget standard() {
        return CostBudget.builder()
                .maxTokens(4096)
                .maxCost(BigDecimal.valueOf(0.10))
                .allowExpensiveOperations(false)
                .priority(5)
                .build();
    }
}
