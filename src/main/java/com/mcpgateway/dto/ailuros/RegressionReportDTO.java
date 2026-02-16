package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionReportDTO {
    private UUID runId;
    private String status;
    private String reportHtml;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();
}
