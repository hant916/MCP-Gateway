package com.mcpgateway.service;

import com.mcpgateway.domain.*;
import com.mcpgateway.repository.PaymentTransactionRepository;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import com.mcpgateway.repository.ToolUsageRecordRepository;
import com.mcpgateway.service.pricing.PricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private ToolUsageRecordRepository usageRecordRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ToolSubscriptionRepository subscriptionRepository;

    @Mock
    private Map<McpTool.PricingModel, PricingStrategy> pricingStrategies;

    @Mock
    private PricingStrategy payAsYouGoPricingStrategy;

    @Mock
    private PricingStrategy monthlyPricingStrategy;

    @InjectMocks
    private BillingService billingService;

    private UUID toolId;
    private UUID userId;
    private McpTool testTool;
    private ToolSubscription activeSubscription;
    private ToolUsageRecord usageRecord;

    @BeforeEach
    void setUp() {
        toolId = UUID.randomUUID();
        userId = UUID.randomUUID();

        testTool = new McpTool();
        testTool.setId(toolId);
        testTool.setToolName("Test Tool");
        testTool.setPricingModel(McpTool.PricingModel.PAY_AS_YOU_GO);

        activeSubscription = new ToolSubscription();
        activeSubscription.setId(UUID.randomUUID());
        activeSubscription.setTool(testTool);
        activeSubscription.setClientId(userId);
        activeSubscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);

        usageRecord = new ToolUsageRecord();
        usageRecord.setId(UUID.randomUUID());
        usageRecord.setSubscription(activeSubscription);
        usageRecord.setResourceConsumption(100L);
        usageRecord.setCost(new BigDecimal("0.50"));
    }

    @Test
    void recordUsage_WithActiveSubscription_ShouldCreateUsageRecord() {
        // Arrange
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE))
            .thenReturn(Optional.of(activeSubscription));
        when(pricingStrategies.get(McpTool.PricingModel.PAY_AS_YOU_GO)).thenReturn(payAsYouGoPricingStrategy);
        when(payAsYouGoPricingStrategy.calculatePrice(any(ToolUsageRecord.class)))
            .thenReturn(new BigDecimal("0.50"));
        when(usageRecordRepository.save(any(ToolUsageRecord.class))).thenReturn(usageRecord);

        // Act
        ToolUsageRecord result = billingService.recordUsage(toolId, userId, 100L);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("0.50"), result.getCost());
        verify(subscriptionRepository).findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE);
        verify(usageRecordRepository).save(any(ToolUsageRecord.class));
    }

    @Test
    void recordUsage_WithNoActiveSubscription_ShouldThrowException() {
        // Arrange
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE))
            .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> billingService.recordUsage(toolId, userId, 100L)
        );
        assertEquals("No active subscription found", exception.getMessage());
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    void recordUsage_WithMonthlySubscription_ShouldDecrementQuota() {
        // Arrange
        testTool.setPricingModel(McpTool.PricingModel.MONTHLY);
        activeSubscription.setRemainingQuota(10);

        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE))
            .thenReturn(Optional.of(activeSubscription));
        when(pricingStrategies.get(McpTool.PricingModel.MONTHLY)).thenReturn(monthlyPricingStrategy);
        when(monthlyPricingStrategy.calculatePrice(any(ToolUsageRecord.class)))
            .thenReturn(BigDecimal.ZERO);
        when(usageRecordRepository.save(any(ToolUsageRecord.class))).thenReturn(usageRecord);

        // Act
        billingService.recordUsage(toolId, userId, 100L);

        // Assert
        verify(subscriptionRepository).save(argThat(subscription ->
            subscription.getRemainingQuota() == 9
        ));
    }

    @Test
    void recordUsage_WithInactiveSubscription_ShouldThrowException() {
        // Arrange
        activeSubscription.setStatus(ToolSubscription.SubscriptionStatus.INACTIVE);
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE))
            .thenReturn(Optional.of(activeSubscription));

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> billingService.recordUsage(toolId, userId, 100L)
        );
        assertEquals("Subscription is not active", exception.getMessage());
    }

    @Test
    void calculateBill_WithUsageRecords_ShouldReturnTotalCost() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();

        ToolUsageRecord record1 = new ToolUsageRecord();
        record1.setCost(new BigDecimal("1.50"));
        ToolUsageRecord record2 = new ToolUsageRecord();
        record2.setCost(new BigDecimal("2.50"));
        ToolUsageRecord record3 = new ToolUsageRecord();
        record3.setCost(new BigDecimal("3.00"));

        List<ToolUsageRecord> records = Arrays.asList(record1, record2, record3);
        when(usageRecordRepository.findBySubscriptionClientIdAndUsageTimeBetween(userId, startDate, endDate))
            .thenReturn(records);

        // Act
        BigDecimal total = billingService.calculateBill(userId, startDate, endDate);

        // Assert
        assertEquals(new BigDecimal("7.00"), total);
        verify(usageRecordRepository).findBySubscriptionClientIdAndUsageTimeBetween(userId, startDate, endDate);
    }

    @Test
    void calculateBill_WithNoUsageRecords_ShouldReturnZero() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        when(usageRecordRepository.findBySubscriptionClientIdAndUsageTimeBetween(userId, startDate, endDate))
            .thenReturn(Collections.emptyList());

        // Act
        BigDecimal total = billingService.calculateBill(userId, startDate, endDate);

        // Assert
        assertEquals(BigDecimal.ZERO, total);
    }

    @Test
    void processPayment_WithValidAmount_ShouldCreateTransaction() {
        // Arrange
        BigDecimal amount = new BigDecimal("50.00");
        String paymentMethod = "credit_card";
        PaymentTransaction expectedTransaction = new PaymentTransaction();
        expectedTransaction.setId(UUID.randomUUID());
        expectedTransaction.setAmount(amount);
        expectedTransaction.setPaymentMethod(paymentMethod);
        expectedTransaction.setStatus(PaymentTransaction.PaymentStatus.COMPLETED);

        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
            .thenReturn(expectedTransaction);

        // Act
        PaymentTransaction result = billingService.processPayment(userId, amount, paymentMethod);

        // Assert
        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        assertEquals(paymentMethod, result.getPaymentMethod());
        assertEquals(PaymentTransaction.PaymentStatus.COMPLETED, result.getStatus());
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void getTransactionHistory_WithDateRange_ShouldReturnTransactions() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();

        PaymentTransaction txn1 = new PaymentTransaction();
        txn1.setAmount(new BigDecimal("10.00"));
        PaymentTransaction txn2 = new PaymentTransaction();
        txn2.setAmount(new BigDecimal("20.00"));

        List<PaymentTransaction> transactions = Arrays.asList(txn1, txn2);
        when(paymentTransactionRepository.findByClientIdAndTransactionTimeBetween(userId, startDate, endDate))
            .thenReturn(transactions);

        // Act
        List<PaymentTransaction> result = billingService.getTransactionHistory(userId, startDate, endDate);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(paymentTransactionRepository).findByClientIdAndTransactionTimeBetween(userId, startDate, endDate);
    }
}
