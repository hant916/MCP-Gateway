package com.mcpgateway.dto;

import com.mcpgateway.domain.McpTool;
import lombok.Data;

import java.util.UUID;

@Data
public class SubscriptionRequest {
    private UUID toolId;
    private McpTool.PricingModel pricingModel;
} 