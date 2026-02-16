package com.mcpgateway.dto.ailuros;

import com.mcpgateway.domain.ailuros.AcRegressionRun;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseGateStatusDTO {
    private ReleaseBaselineDTO baseline;
    private ReleaseCandidateDTO candidate;
    private boolean changed;

    private UUID pendingRunId;
    private UUID latestRunId;
    private AcRegressionRun.Status latestRunStatus;
    private boolean releaseBlocked;
}
