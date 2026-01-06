package com.mcpgateway.repository;

import com.mcpgateway.domain.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {

    /**
     * Find all active webhooks for a user
     */
    List<WebhookConfig> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Find all active webhooks that subscribe to a specific event
     */
    @Query("SELECT w FROM WebhookConfig w WHERE w.isActive = true " +
           "AND w.status = 'ACTIVE' " +
           "AND w.events LIKE CONCAT('%', :event, '%')")
    List<WebhookConfig> findActiveWebhooksByEvent(@Param("event") String event);

    /**
     * Find all webhooks for a user
     */
    List<WebhookConfig> findByUserId(UUID userId);

    /**
     * Count active webhooks for a user
     */
    Integer countByUserIdAndIsActiveTrue(UUID userId);
}
