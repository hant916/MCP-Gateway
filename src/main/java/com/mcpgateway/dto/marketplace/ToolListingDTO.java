package com.mcpgateway.dto.marketplace;

import com.mcpgateway.domain.McpTool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for tool listing in marketplace
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolListingDTO {

    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private String[] tags;

    private UUID categoryId;
    private String categoryName;

    private McpTool.PricingModel pricingModel;
    private BigDecimal price;
    private Integer usageQuota;

    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer subscriberCount;

    private String version;
    private McpTool.ToolStatus status;

    private UUID builderId;
    private String builderName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert from McpTool entity
     */
    public static ToolListingDTO from(McpTool tool) {
        return ToolListingDTO.builder()
                .id(tool.getId())
                .name(tool.getName())
                .description(tool.getDescription())
                .iconUrl(tool.getIconUrl())
                .tags(tool.getTags() != null ? tool.getTags().split(",") : new String[0])
                .categoryId(tool.getCategory() != null ? tool.getCategory().getId() : null)
                .categoryName(tool.getCategory() != null ? tool.getCategory().getName() : null)
                .pricingModel(tool.getPricingModel())
                .price(tool.getPrice())
                .usageQuota(tool.getUsageQuota())
                .averageRating(tool.getAverageRating())
                .reviewCount(tool.getReviewCount())
                .subscriberCount(tool.getSubscriberCount())
                .version(tool.getVersion())
                .status(tool.getStatus())
                .builderId(tool.getBuilder() != null ? tool.getBuilder().getId() : null)
                .builderName(tool.getBuilder() != null ? tool.getBuilder().getUsername() : null)
                .createdAt(tool.getCreatedAt())
                .updatedAt(tool.getUpdatedAt())
                .build();
    }
}
