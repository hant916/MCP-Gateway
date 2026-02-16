package com.mcpgateway.dto.ailuros;

import com.mcpgateway.domain.ailuros.AcRegressionRun;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionRunDTO {
    private UUID id;

    private String appId;
    private String env;
    private String route;

    private String baselineModel;
    private String candidateModel;
    private String baselinePromptVersion;
    private String candidatePromptVersion;

    private AcRegressionRun.Status status;
    private Instant startedTs;
    private Instant endedTs;
    private Instant createdTs;

    private Boolean releaseBlocked;
    private String reportUri;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();
}
