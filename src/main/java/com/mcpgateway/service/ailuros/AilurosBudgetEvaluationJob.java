package com.mcpgateway.service.ailuros;

import com.mcpgateway.config.AilurosGovernanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AilurosBudgetEvaluationJob {

    private final AilurosGovernanceProperties governanceProperties;
    private final AilurosBudgetGuardService budgetGuardService;

    @Scheduled(cron = "${ailuros.budget.eval-cron:0 0 * * * *}")
    public void runScheduledEvaluation() {
        if (!governanceProperties.getBudget().isEnabled()) {
            return;
        }

        try {
            budgetGuardService.evaluatePolicies(null, null, null, "scheduled");
        } catch (Exception ex) {
            log.warn("Budget evaluation job failed: {}", ex.getMessage());
        }
    }
}
