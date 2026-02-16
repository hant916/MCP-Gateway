package com.mcpgateway.service.ailuros;

import com.mcpgateway.config.AilurosGovernanceProperties;
import com.mcpgateway.domain.ailuros.AcBudgetEval;
import com.mcpgateway.domain.ailuros.AcBudgetPolicy;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.dto.ailuros.BudgetEvaluateResponseDTO;
import com.mcpgateway.repository.ailuros.AcBudgetEvalRepository;
import com.mcpgateway.repository.ailuros.AcBudgetPolicyRepository;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AilurosBudgetGuardServiceTest {

    @Mock
    private AcBudgetPolicyRepository policyRepository;
    @Mock
    private AcBudgetEvalRepository evalRepository;
    @Mock
    private AcCallRepository callRepository;
    @Mock
    private AilurosGovernanceWebhookService webhookService;

    private AilurosBudgetGuardService service;

    @BeforeEach
    void setUp() {
        AilurosGovernanceProperties properties = new AilurosGovernanceProperties();
        properties.getBudget().setForecastDays(7);
        service = new AilurosBudgetGuardService(
            policyRepository,
            evalRepository,
            callRepository,
            webhookService,
            properties
        );
    }

    @Test
    void evaluatePolicies_shouldMarkExceeded_whenCostAboveDailyLimit() {
        AcBudgetPolicy policy = AcBudgetPolicy.builder()
            .id(UUID.randomUUID())
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .dailyUsdLimit(new BigDecimal("1.000000"))
            .monthlyUsdLimit(new BigDecimal("100.000000"))
            .forecastMonthlyUsdLimit(new BigDecimal("120.000000"))
            .isEnabled(true)
            .build();

        AcCall expensiveCall = AcCall.builder()
            .id(UUID.randomUUID())
            .projectKey("clarity")
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .status("ok")
            .provider("openai")
            .model("gpt-4o")
            .traceId("trace-x")
            .costEstimateUsd(new BigDecimal("2.500000"))
            .requestTs(Instant.now())
            .createdAt(Instant.now())
            .build();

        when(policyRepository.findEnabledPolicies("clarity", "prod", "/v1/chat"))
            .thenReturn(List.of(policy));
        when(callRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(expensiveCall));
        when(evalRepository.save(any(AcBudgetEval.class))).thenAnswer(invocation -> {
            AcBudgetEval eval = invocation.getArgument(0);
            eval.setId(UUID.randomUUID());
            eval.setCreatedTs(Instant.now());
            return eval;
        });

        BudgetEvaluateResponseDTO result = service.evaluatePolicies("clarity", "prod", "/v1/chat", "manual");

        assertFalse(result.getEvaluations().isEmpty());
        assertEquals(AcBudgetEval.Status.EXCEEDED, result.getEvaluations().get(0).getStatus());
        verify(webhookService, times(1)).notifyEvent(eq("budget_exceeded"), any());
    }
}
