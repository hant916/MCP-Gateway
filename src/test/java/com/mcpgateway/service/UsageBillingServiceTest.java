package com.mcpgateway.service;

import com.mcpgateway.domain.BillingRule;
import com.mcpgateway.domain.UsageRecord;
import com.mcpgateway.repository.BillingRuleRepository;
import com.mcpgateway.repository.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageBillingServiceTest {

    @Mock
    private UsageRecordRepository usageRecordRepository;

    @Mock
    private BillingRuleRepository billingRuleRepository;

    @InjectMocks
    private UsageBillingService usageBillingService;

    private List<BillingRule> billingRules;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        billingRules = new ArrayList<>();

        // SSE Message Rule
        BillingRule sseRule = new BillingRule();
        sseRule.setId(UUID.randomUUID());
        sseRule.setRuleName("SSE Message");
        sseRule.setApiPattern("/api/v1/sse/message");
        sseRule.setCostPerCall(new BigDecimal("0.001"));
        sseRule.setPriority(10);
        sseRule.setActive(true);
        billingRules.add(sseRule);

        // Default Rule
        BillingRule defaultRule = new BillingRule();
        defaultRule.setId(UUID.randomUUID());
        defaultRule.setRuleName("Default");
        defaultRule.setApiPattern("*");
        defaultRule.setCostPerCall(new BigDecimal("0.001"));
        defaultRule.setPriority(1);
        defaultRule.setActive(true);
        billingRules.add(defaultRule);
    }

    @Test
    void recordUsage_ShouldCreateUsageRecord() {
        // Arrange
        when(billingRuleRepository.findAll()).thenReturn(billingRules);
        when(usageRecordRepository.save(any(UsageRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        usageBillingService.recordUsage(
            sessionId,
            "/api/v1/sse/message",
            "POST",
            200,
            System.currentTimeMillis(),
            null,
            null,
            100
        );

        // Assert
        verify(usageRecordRepository, times(1)).save(any(UsageRecord.class));
    }

    @Test
    void calculateCost_ForSseMessage_ShouldReturn0_001() {
        // Arrange
        when(billingRuleRepository.findAll()).thenReturn(billingRules);

        // Act
        BigDecimal cost = calculateCostForEndpoint("/api/v1/sse/message");

        // Assert
        assertEquals(new BigDecimal("0.001"), cost);
    }

    @Test
    void calculateCost_ForUnknownEndpoint_ShouldUseDefaultRule() {
        // Arrange
        when(billingRuleRepository.findAll()).thenReturn(billingRules);

        // Act
        BigDecimal cost = calculateCostForEndpoint("/api/v1/unknown");

        // Assert
        assertEquals(new BigDecimal("0.001"), cost);
    }

    private BigDecimal calculateCostForEndpoint(String endpoint) {
        // Find matching rule
        BillingRule matchedRule = billingRules.stream()
            .filter(rule -> rule.isActive() && matches(endpoint, rule.getApiPattern()))
            .max((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
            .orElse(null);

        return matchedRule != null ? matchedRule.getCostPerCall() : BigDecimal.ZERO;
    }

    private boolean matches(String endpoint, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        String regex = pattern.replace("*", ".*");
        return endpoint.matches(regex);
    }
}
