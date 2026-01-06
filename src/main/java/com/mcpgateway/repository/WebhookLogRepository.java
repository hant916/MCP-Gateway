package com.mcpgateway.repository;

import com.mcpgateway.domain.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Delete old logs (for cleanup)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
