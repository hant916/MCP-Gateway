package com.mcpgateway.service;

import com.mcpgateway.domain.ToolUsageRecord;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.PaymentTransaction;
import com.mcpgateway.repository.ToolUsageRecordRepository;
import com.mcpgateway.repository.PaymentTransactionRepository;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import com.mcpgateway.service.pricing.PricingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BillingService {
    private final ToolUsageRecordRepository usageRecordRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ToolSubscriptionRepository subscriptionRepository;
    private final Map<com.mcpgateway.domain.McpTool.PricingModel, PricingStrategy> pricingStrategies;
    
    public ToolUsageRecord recordUsage(UUID toolId, UUID userId, Long consumption) {
        // 查找活跃订阅
        ToolSubscription subscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active subscription found"));
                
        if (subscription.getStatus() != ToolSubscription.SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Subscription is not active");
        }
        
        // 检查月度订阅的配额
        if (subscription.getTool().getPricingModel() == com.mcpgateway.domain.McpTool.PricingModel.MONTHLY) {
            Integer remainingQuota = subscription.getRemainingQuota();
            if (remainingQuota != null) {
                subscription.setRemainingQuota(remainingQuota - 1);
                subscriptionRepository.save(subscription);
            }
        }
        
        ToolUsageRecord usageRecord = new ToolUsageRecord();
        usageRecord.setSubscription(subscription);
        usageRecord.setResourceConsumption(consumption);

        // 计算费用
        PricingStrategy pricingStrategy = pricingStrategies.get(subscription.getTool().getPricingModel());
        BigDecimal cost = pricingStrategy.calculatePrice(usageRecord);
        usageRecord.setCost(cost);

        return usageRecordRepository.save(usageRecord);
    }
    
    public BigDecimal calculateBill(UUID clientId, LocalDateTime startDate, LocalDateTime endDate) {
        List<ToolUsageRecord> usageRecords = usageRecordRepository
                .findBySubscriptionClientIdAndUsageTimeBetween(clientId, startDate, endDate);
                
        return usageRecords.stream()
                .map(ToolUsageRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public PaymentTransaction processPayment(UUID clientId, BigDecimal amount, String paymentMethod) {
        // In a real implementation, this would integrate with a payment gateway
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setAmount(amount);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setStatus(PaymentTransaction.PaymentStatus.COMPLETED);
        
        return paymentTransactionRepository.save(transaction);
    }
    
    public List<PaymentTransaction> getTransactionHistory(UUID clientId, LocalDateTime startDate, LocalDateTime endDate) {
        return paymentTransactionRepository.findByClientIdAndTransactionTimeBetween(clientId, startDate, endDate);
    }
} 