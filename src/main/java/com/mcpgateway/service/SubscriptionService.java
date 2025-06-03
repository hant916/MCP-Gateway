package com.mcpgateway.service;

import com.mcpgateway.domain.*;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import com.mcpgateway.repository.McpToolRepository;
import com.mcpgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SubscriptionService {
    private final ToolSubscriptionRepository subscriptionRepository;
    private final McpToolRepository toolRepository;
    private final UserRepository userRepository;
    
    public ToolSubscription subscribe(UUID clientId, UUID toolId, McpTool.PricingModel pricingModel) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
                
        McpTool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found"));
        
        if (tool.getStatus() != McpTool.ToolStatus.PUBLISHED) {
            throw new IllegalStateException("Tool is not published");
        }
        
        ToolSubscription subscription = new ToolSubscription();
        subscription.setClient(client);
        subscription.setTool(tool);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        
        // Set subscription end date based on pricing model
        if (pricingModel == McpTool.PricingModel.MONTHLY) {
            subscription.setEndDate(LocalDateTime.now().plusMonths(1));
            subscription.setRemainingQuota(tool.getUsageQuota());
        }
        
        return subscriptionRepository.save(subscription);
    }
    
    public void cancelSubscription(UUID subscriptionId) {
        ToolSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
                
        subscription.setStatus(ToolSubscription.SubscriptionStatus.CANCELLED);
        subscription.setEndDate(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }
    
    public ToolSubscription renewSubscription(UUID subscriptionId) {
        ToolSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
                
        if (subscription.getTool().getPricingModel() != McpTool.PricingModel.MONTHLY) {
            throw new IllegalStateException("Only monthly subscriptions can be renewed");
        }
        
        subscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setEndDate(LocalDateTime.now().plusMonths(1));
        subscription.setRemainingQuota(subscription.getTool().getUsageQuota());
        
        return subscriptionRepository.save(subscription);
    }

    /**
     * 查找用户的活跃订阅
     */
    public Optional<ToolSubscription> findActiveSubscription(UUID userId, UUID toolId) {
        return subscriptionRepository.findByClientIdAndToolIdAndStatus(
                userId, 
                toolId, 
                ToolSubscription.SubscriptionStatus.ACTIVE
        );
    }
} 