package com.mcpgateway.dto;

import com.mcpgateway.domain.McpTool;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingUpdateRequest {
    private BigDecimal price;
    private McpTool.PricingModel pricingModel;
    private Integer usageQuota;
} 