package com.mcpgateway.repository;

import com.mcpgateway.domain.ToolUsageRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ToolUsageRecordRepository extends JpaRepository<ToolUsageRecord, UUID> {
    List<ToolUsageRecord> findBySubscriptionClientIdAndUsageTimeBetween(
            UUID clientId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // Analytics queries

    /**
     * Count total requests in date range
     */
    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count distinct users in date range
     */
    @Query("SELECT COUNT(DISTINCT r.subscription.clientId) FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end")
    Long countDistinctUsersByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get request trend by date
     */
    @Query("SELECT CAST(r.createdAt AS date) as date, COUNT(r) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY CAST(r.createdAt AS date) " +
           "ORDER BY date")
    List<Object[]> getRequestTrendByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get active user trend by date
     */
    @Query("SELECT CAST(r.createdAt AS date) as date, COUNT(DISTINCT r.subscription.clientId) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY CAST(r.createdAt AS date) " +
           "ORDER BY date")
    List<Object[]> getActiveUserTrendByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get top tools by usage
     */
    @Query("SELECT r.subscription.tool.id, r.subscription.tool.name, " +
           "COUNT(r) as requestCount, COUNT(DISTINCT r.subscription.clientId) as uniqueUsers " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY r.subscription.tool.id, r.subscription.tool.name " +
           "ORDER BY requestCount DESC")
    List<Object[]> getTopToolsByUsage(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    /**
     * Get usage by transport type
     */
    @Query("SELECT r.session.transport, COUNT(r) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end AND r.session IS NOT NULL " +
           "GROUP BY r.session.transport")
    List<Object[]> getUsageByTransportType(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get usage by hour of day
     */
    @Query("SELECT HOUR(r.createdAt) as hour, COUNT(r) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY HOUR(r.createdAt) " +
           "ORDER BY hour")
    List<Object[]> getUsageByHour(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get average response time
     */
    @Query("SELECT AVG(r.responseTime) FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end AND r.responseTime IS NOT NULL")
    Double getAverageResponseTime(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get total revenue
     */
    @Query("SELECT SUM(r.cost) FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end")
    BigDecimal getTotalRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get revenue trend by date
     */
    @Query("SELECT CAST(r.createdAt AS date) as date, SUM(r.cost) as revenue " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY CAST(r.createdAt AS date) " +
           "ORDER BY date")
    List<Object[]> getRevenueTrendByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get revenue by subscription tier
     */
    @Query("SELECT r.subscription.subscriptionTier, SUM(r.cost) as revenue " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY r.subscription.subscriptionTier")
    List<Object[]> getRevenueBySubscriptionTier(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get top tools by revenue
     */
    @Query("SELECT r.subscription.tool.id, r.subscription.tool.name, " +
           "SUM(r.cost) as revenue, COUNT(DISTINCT r.subscription.id) as subscribers " +
           "FROM ToolUsageRecord r " +
           "WHERE r.createdAt BETWEEN :start AND :end " +
           "GROUP BY r.subscription.tool.id, r.subscription.tool.name " +
           "ORDER BY revenue DESC")
    List<Object[]> getTopToolsByRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
} 