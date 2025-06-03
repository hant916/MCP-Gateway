package com.mcpgateway.dto;

import com.mcpgateway.domain.McpTool;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class McpToolDTO {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private McpTool.PricingModel pricingModel;
    private McpTool.ToolStatus status;
} 