package com.mcpgateway.service.ailuros;

import com.mcpgateway.config.AilurosGovernanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AilurosReleaseDetectionJob {

    private final AilurosGovernanceProperties governanceProperties;
    private final AilurosReleaseGateService releaseGateService;

    @Scheduled(cron = "${ailuros.regression.detect-cron:0 15 * * * *}")
    public void runReleaseDetection() {
        if (!governanceProperties.getRegression().isEnabled()) {
            return;
        }

        try {
            int queued = releaseGateService.detectAndQueueAllBaselines();
            if (queued > 0) {
                log.info("Release change detector queued {} pending regression runs", queued);
            }
        } catch (Exception ex) {
            log.warn("Release detection job failed: {}", ex.getMessage());
        }
    }
}
