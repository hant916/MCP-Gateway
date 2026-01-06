package com.mcpgateway.dto.marketplace;

import com.mcpgateway.domain.ToolReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for review summary (used in tool listings)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryDTO {

    private UUID id;
    private String username;
    private Integer rating;
    private String title;
    private String comment;
    private Boolean isVerifiedPurchase;
    private Integer helpfulCount;
    private LocalDateTime createdAt;

    /**
     * Convert from ToolReview entity
     */
    public static ReviewSummaryDTO from(ToolReview review) {
        // Truncate long comments
        String truncatedComment = review.getComment();
        if (truncatedComment != null && truncatedComment.length() > 200) {
            truncatedComment = truncatedComment.substring(0, 197) + "...";
        }

        return ReviewSummaryDTO.builder()
                .id(review.getId())
                .username(review.getUser().getUsername())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(truncatedComment)
                .isVerifiedPurchase(review.getIsVerifiedPurchase())
                .helpfulCount(review.getHelpfulCount())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
