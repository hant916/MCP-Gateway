package com.mcpgateway.repository;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolSubscriptionRepository extends JpaRepository<ToolSubscription, UUID> {
    Optional<ToolSubscription> findByClientIdAndToolIdAndStatus(
            UUID clientId,
            UUID toolId,
            ToolSubscription.SubscriptionStatus status
    );

    List<ToolSubscription> findByTool(McpTool tool);

    /**
     * Find all subscriptions by status
     */
    List<ToolSubscription> findByStatus(ToolSubscription.SubscriptionStatus status);

    /**
     * Count subscriptions by status
     */
    Integer countByStatus(ToolSubscription.SubscriptionStatus status);

    /**
     * Count new subscriptions in date range
     */
    @Query("SELECT COUNT(s) FROM ToolSubscription s " +
           "WHERE s.status = 'ACTIVE' AND s.createdAt BETWEEN :start AND :end")
    Integer countNewSubscriptions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Count churned subscriptions in date range
     */
    @Query("SELECT COUNT(s) FROM ToolSubscription s " +
           "WHERE s.status = 'INACTIVE' AND s.updatedAt BETWEEN :start AND :end")
    Integer countChurnedSubscriptions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
} 