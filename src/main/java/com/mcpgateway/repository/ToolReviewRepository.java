package com.mcpgateway.repository;

import com.mcpgateway.domain.ToolReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolReviewRepository extends JpaRepository<ToolReview, UUID> {

    Page<ToolReview> findByToolIdAndStatus(UUID toolId, ToolReview.ReviewStatus status, Pageable pageable);

    Optional<ToolReview> findByToolIdAndUserId(UUID toolId, UUID userId);

    List<ToolReview> findByUserId(UUID userId);

    @Query("SELECT AVG(r.rating) FROM ToolReview r WHERE r.tool.id = :toolId AND r.status = 'APPROVED'")
    Double getAverageRatingByToolId(UUID toolId);

    @Query("SELECT COUNT(r) FROM ToolReview r WHERE r.tool.id = :toolId AND r.status = 'APPROVED'")
    Long getReviewCountByToolId(UUID toolId);

    @Query("SELECT r FROM ToolReview r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<ToolReview> findPendingReviews();
}
