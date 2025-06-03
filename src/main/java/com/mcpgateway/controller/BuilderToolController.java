package com.mcpgateway.controller;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.dto.McpToolDTO;
import com.mcpgateway.dto.PricingUpdateRequest;
import com.mcpgateway.dto.SubscriptionDTO;
import com.mcpgateway.service.McpToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/builder/tools")
@RequiredArgsConstructor
public class BuilderToolController {
    private final McpToolService toolService;
    
    @PutMapping("/{id}/pricing")
    @PreAuthorize("hasRole('BUILDER')")
    public ResponseEntity<McpToolDTO> updateToolPricing(
            @PathVariable UUID id,
            @RequestBody PricingUpdateRequest request
    ) {
        McpTool tool = toolService.updatePricing(id, request.getPrice(), request.getPricingModel());
        return ResponseEntity.ok(convertToDTO(tool));
    }
    
    @GetMapping("/{id}/subscriptions")
    @PreAuthorize("hasRole('BUILDER')")
    public ResponseEntity<List<SubscriptionDTO>> getToolSubscriptions(
            @PathVariable UUID id
    ) {
        List<ToolSubscription> subscriptions = toolService.getToolSubscriptions(id);
        List<SubscriptionDTO> dtos = subscriptions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
    
    private McpToolDTO convertToDTO(McpTool tool) {
        return McpToolDTO.builder()
                .id(tool.getId())
                .name(tool.getName())
                .description(tool.getDescription())
                .price(tool.getPrice())
                .pricingModel(tool.getPricingModel())
                .status(tool.getStatus())
                .build();
    }
    
    private SubscriptionDTO convertToDTO(ToolSubscription subscription) {
        return SubscriptionDTO.builder()
                .id(subscription.getId())
                .toolId(subscription.getTool().getId())
                .clientId(subscription.getClient().getId())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus())
                .remainingQuota(subscription.getRemainingQuota())
                .build();
    }
} 