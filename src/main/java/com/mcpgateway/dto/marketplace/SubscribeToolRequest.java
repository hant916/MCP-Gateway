package com.mcpgateway.dto.marketplace;

import com.mcpgateway.domain.McpTool;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO for subscribing to a tool
 */
@Data
public class SubscribeToolRequest {

    @NotNull(message = "Tool ID is required")
    private UUID toolId;

    @NotNull(message = "Pricing model is required")
    private McpTool.PricingModel pricingModel;

    private Integer monthlyQuota; // For MONTHLY pricing
}
