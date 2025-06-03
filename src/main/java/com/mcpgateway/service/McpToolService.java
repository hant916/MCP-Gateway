package com.mcpgateway.service;

import com.mcpgateway.domain.ApiSpecification;
import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.dto.tool.McpToolDTO;
import com.mcpgateway.repository.ApiSpecificationRepository;
import com.mcpgateway.repository.McpToolRepository;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpToolService {
    private final McpToolRepository mcpToolRepository;
    private final ApiSpecificationRepository apiSpecificationRepository;
    private final ToolSubscriptionRepository toolSubscriptionRepository;

    @Transactional(readOnly = true)
    public List<McpToolDTO> getToolsByApiSpecification(UUID apiSpecId) {
        return mcpToolRepository.findByApiSpecificationId(apiSpecId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public McpToolDTO createTool(UUID apiSpecId, String name, String description, String parameters) {
        ApiSpecification apiSpec = apiSpecificationRepository.findById(apiSpecId)
                .orElseThrow(() -> new IllegalArgumentException("API Specification not found"));

        McpTool tool = new McpTool();
        tool.setName(name);
        tool.setDescription(description);
        tool.setParameters(parameters);
        tool.setApiSpecification(apiSpec);
        tool.setStatus(McpTool.ToolStatus.DRAFT);

        McpTool savedTool = mcpToolRepository.save(tool);
        return mapToDTO(savedTool);
    }

    @Transactional(readOnly = true)
    public McpToolDTO getTool(UUID id) {
        return mcpToolRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new IllegalArgumentException("MCP Tool not found"));
    }

    @Transactional
    public void deleteTool(UUID id) {
        if (!mcpToolRepository.existsById(id)) {
            throw new IllegalArgumentException("MCP Tool not found");
        }
        mcpToolRepository.deleteById(id);
    }

    @Transactional
    public McpTool updatePricing(UUID id, BigDecimal price, McpTool.PricingModel pricingModel) {
        McpTool tool = mcpToolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP Tool not found"));
        
        tool.setPrice(price);
        tool.setPricingModel(pricingModel);
        
        return mcpToolRepository.save(tool);
    }

    @Transactional(readOnly = true)
    public List<ToolSubscription> getToolSubscriptions(UUID id) {
        McpTool tool = mcpToolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP Tool not found"));
        return toolSubscriptionRepository.findByTool(tool);
    }

    private McpToolDTO mapToDTO(McpTool tool) {
        return McpToolDTO.builder()
                .id(tool.getId())
                .name(tool.getName())
                .description(tool.getDescription())
                .parameters(tool.getParameters())
                .apiSpecificationId(tool.getApiSpecification().getId())
                .createdAt(tool.getCreatedAt() != null ? 
                    ZonedDateTime.of(tool.getCreatedAt(), ZoneId.systemDefault()) : null)
                .updatedAt(tool.getUpdatedAt() != null ? 
                    ZonedDateTime.of(tool.getUpdatedAt(), ZoneId.systemDefault()) : null)
                .build();
    }
} 