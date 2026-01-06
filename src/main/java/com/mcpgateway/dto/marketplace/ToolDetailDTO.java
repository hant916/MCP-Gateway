package com.mcpgateway.dto.marketplace;

import com.mcpgateway.domain.McpTool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for detailed tool information in marketplace
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDetailDTO {

    private UUID id;
    private String name;
    private String description;
    private String longDescription;
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
    private String parameters;
    private McpTool.ToolStatus status;

    private UUID builderId;
    private String builderName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Pricing tiers
    private List<PricingTier> pricingTiers;

    // Recent reviews
    private List<ReviewSummaryDTO> recentReviews;

    // Rating distribution
    private RatingDistribution ratingDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingTier {
        private String name;
        private BigDecimal price;
        private Integer quota;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingDistribution {
        private Integer fiveStars;
        private Integer fourStars;
        private Integer threeStars;
        private Integer twoStars;
        private Integer oneStar;
    }

    /**
     * Convert from McpTool entity
     */
    public static ToolDetailDTO from(McpTool tool) {
        return ToolDetailDTO.builder()
                .id(tool.getId())
                .name(tool.getName())
                .description(tool.getDescription())
                .longDescription(tool.getLongDescription())
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
                .parameters(tool.getParameters())
                .status(tool.getStatus())
                .builderId(tool.getBuilder() != null ? tool.getBuilder().getId() : null)
                .builderName(tool.getBuilder() != null ? tool.getBuilder().getUsername() : null)
                .createdAt(tool.getCreatedAt())
                .updatedAt(tool.getUpdatedAt())
                .build();
    }
}
