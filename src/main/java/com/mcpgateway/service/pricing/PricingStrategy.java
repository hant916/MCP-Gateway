package com.mcpgateway.service.pricing;

import com.mcpgateway.domain.ToolUsageRecord;
import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal calculatePrice(ToolUsageRecord usage);
} 