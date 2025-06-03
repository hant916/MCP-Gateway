package com.mcpgateway.service.pricing;

import com.mcpgateway.domain.ToolUsageRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PayPerUseStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(ToolUsageRecord usage) {
        BigDecimal basePrice = usage.getSubscription().getTool().getPrice();
        Long resourceConsumption = usage.getResourceConsumption();
        
        // Calculate price based on resource consumption
        // For example, price per unit of resource
        BigDecimal pricePerUnit = basePrice.divide(new BigDecimal(1000), 4, RoundingMode.HALF_UP);
        return pricePerUnit.multiply(new BigDecimal(resourceConsumption))
                .setScale(2, RoundingMode.HALF_UP);
    }
} 