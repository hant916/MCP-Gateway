package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionRunRequestDTO {
    private String appId;
    private String env;
    private String route;

    private String baselineModel;
    private String baselinePromptVersion;
    private String candidateModel;
    private String candidatePromptVersion;
}
