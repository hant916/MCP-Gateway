package com.mcpgateway.repository;

import com.mcpgateway.domain.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {

    /**
     * Find logs for a specific webhook config
     */
    Page<WebhookLog> findByWebhookConfigId(UUID webhookConfigId, Pageable pageable);

    /**
     * Find logs for a webhook scoped to owner user id.
     */
    Page<WebhookLog> findByWebhookConfigIdAndWebhookConfigUserId(
            UUID webhookConfigId,
            UUID userId,
            Pageable pageable
    );

    /**
     * Find recent failed deliveries for a webhook
     */
    List<WebhookLog> findTop10ByWebhookConfigIdAndStatusOrderByCreatedAtDesc(
            UUID webhookConfigId,
            WebhookLog.DeliveryStatus status
    );

    /**
     * Count failures for a webhook in a time period
     */
    Integer countByWebhookConfigIdAndStatusAndCreatedAtAfter(
            UUID webhookConfigId,
            WebhookLog.DeliveryStatus status,
            LocalDateTime after
    );

    /**
     * Find retry deliveries that are due.
     */
    Page<WebhookLog> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            WebhookLog.DeliveryStatus status,
            LocalDateTime nextRetryAt,
            Pageable pageable
    );

    /**
     * Claim a due retry record to prevent duplicate processing across instances.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WebhookLog w SET w.status = :claimedStatus, w.claimedAt = :claimedAt, w.claimedBy = :claimedBy " +
           "WHERE w.id = :id AND w.status = :expectedStatus AND w.nextRetryAt <= :now")
    int claimDueRetry(
            @Param("id") UUID id,
            @Param("expectedStatus") WebhookLog.DeliveryStatus expectedStatus,
            @Param("claimedStatus") WebhookLog.DeliveryStatus claimedStatus,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("claimedBy") String claimedBy,
            @Param("now") LocalDateTime now
    );

    /**
     * Delete old logs (for cleanup)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
