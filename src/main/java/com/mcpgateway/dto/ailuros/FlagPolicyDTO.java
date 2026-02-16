package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Versioned flag policy metadata for explainability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagPolicyDTO {
    private String version;
    private List<FlagRuleDTO> rules;
    private Map<String, Double> kFactors;
}
