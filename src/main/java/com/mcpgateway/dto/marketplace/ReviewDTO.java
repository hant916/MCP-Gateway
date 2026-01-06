package com.mcpgateway.dto.marketplace;

import com.mcpgateway.domain.ToolReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for tool review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {

    private UUID id;
    private UUID toolId;
    private String toolName;

    private UUID userId;
    private String username;

    private Integer rating;
    private String title;
    private String comment;

    private ToolReview.ReviewStatus status;
    private Boolean isVerifiedPurchase;
    private Integer helpfulCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert from ToolReview entity
     */
    public static ReviewDTO from(ToolReview review) {
        return ReviewDTO.builder()
                .id(review.getId())
                .toolId(review.getTool().getId())
                .toolName(review.getTool().getName())
                .userId(review.getUser().getId())
                .username(review.getUser().getUsername())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .status(review.getStatus())
                .isVerifiedPurchase(review.getIsVerifiedPurchase())
                .helpfulCount(review.getHelpfulCount())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
