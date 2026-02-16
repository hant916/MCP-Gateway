package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseCandidateDTO {
    private String appId;
    private String env;
    private String route;
    private String candidateModel;
    private String candidatePromptVersion;
    private Instant detectedAt;
}
