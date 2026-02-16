package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Baseline values used to explain why a call was flagged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineRefDTO {
    private Double latencyP95Ms;
    private BigDecimal costRollingAvgUsd;
    private String baselineModel;
    private String baselinePromptHash;
}
