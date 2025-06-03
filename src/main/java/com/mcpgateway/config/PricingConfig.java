package com.mcpgateway.config;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.service.pricing.MonthlyPricingStrategy;
import com.mcpgateway.service.pricing.PayPerUseStrategy;
import com.mcpgateway.service.pricing.PricingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class PricingConfig {
    
    @Bean
    public Map<McpTool.PricingModel, PricingStrategy> pricingStrategies(
            MonthlyPricingStrategy monthlyStrategy,
            PayPerUseStrategy payPerUseStrategy
    ) {
        Map<McpTool.PricingModel, PricingStrategy> strategies = new HashMap<>();
        strategies.put(McpTool.PricingModel.MONTHLY, monthlyStrategy);
        strategies.put(McpTool.PricingModel.PAY_PER_USE, payPerUseStrategy);
        strategies.put(McpTool.PricingModel.FREE, usage -> java.math.BigDecimal.ZERO);
        return strategies;
    }
} 