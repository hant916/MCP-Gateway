package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseBaselineDTO {
    private UUID id;
    private String appId;
    private String env;
    private String route;
    private String baselineModel;
    private String baselinePromptVersion;
    private Boolean enabled;
    private Instant createdTs;
    private Instant updatedTs;
}
