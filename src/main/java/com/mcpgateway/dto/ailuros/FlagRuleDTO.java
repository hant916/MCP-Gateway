package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes one flag rule used by the dashboard policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagRuleDTO {
    private String code;
    private String desc;
}
