package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for comparing two calls
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareDTO {
    private CallDetailDTO callA;
    private CallDetailDTO callB;
    private String diffText;
    private Map<String, Object> summary;
}
