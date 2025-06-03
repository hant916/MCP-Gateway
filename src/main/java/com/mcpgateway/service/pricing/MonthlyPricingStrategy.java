package com.mcpgateway.service.pricing;

import com.mcpgateway.domain.ToolUsageRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MonthlyPricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(ToolUsageRecord usage) {
        // Get the base price from the tool
        BigDecimal basePrice = usage.getSubscription().getTool().getPrice();
        
        // For monthly subscription, we return the base price
        // Additional charges may apply if usage exceeds quota
        Integer remainingQuota = usage.getSubscription().getRemainingQuota();
        if (remainingQuota != null && remainingQuota <= 0) {
            // Apply overage charges - for example, 20% premium
            return basePrice.multiply(new BigDecimal("1.2"));
        }
        
        return basePrice;
    }
} 