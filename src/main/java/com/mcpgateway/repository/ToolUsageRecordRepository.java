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
    @Query("SELECT r FROM ToolUsageRecord r " +
           "WHERE r.subscription.client.id = :clientId " +
           "AND r.usageTime BETWEEN :startDate AND :endDate")
    List<ToolUsageRecord> findBySubscriptionClientIdAndUsageTimeBetween(
            @Param("clientId")
            UUID clientId,
            @Param("startDate")
            LocalDateTime startDate,
            @Param("endDate")
            LocalDateTime endDate
    );

    // Analytics queries

    /**
     * Count total requests in date range
     */
    @Query("SELECT COUNT(r) FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end")
    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count distinct users in date range
     */
    @Query("SELECT COUNT(DISTINCT r.subscription.client.id) FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end")
    Long countDistinctUsersByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get request trend by date
     */
    @Query("SELECT CAST(r.usageTime AS date) as date, COUNT(r) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
           "GROUP BY CAST(r.usageTime AS date) " +
           "ORDER BY date")
    List<Object[]> getRequestTrendByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get active user trend by date
     */
    @Query("SELECT CAST(r.usageTime AS date) as date, COUNT(DISTINCT r.subscription.client.id) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
           "GROUP BY CAST(r.usageTime AS date) " +
           "ORDER BY date")
    List<Object[]> getActiveUserTrendByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get top tools by usage
     */
    @Query("SELECT r.subscription.tool.id, r.subscription.tool.name, " +
           "COUNT(r) as requestCount, COUNT(DISTINCT r.subscription.client.id) as uniqueUsers " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
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
    @Query("SELECT 'UNKNOWN', COUNT(r) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end")
    List<Object[]> getUsageByTransportType(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get usage by hour of day
     */
    @Query("SELECT HOUR(r.usageTime) as hour, COUNT(r) as count " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
           "GROUP BY HOUR(r.usageTime) " +
           "ORDER BY hour")
    List<Object[]> getUsageByHour(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get average response time
     */
    @Query("SELECT COALESCE(AVG(0.0), 0.0) FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end")
    Double getAverageResponseTime(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get total revenue
     */
    @Query("SELECT SUM(r.cost) FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end")
    BigDecimal getTotalRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get revenue trend by date
     */
    @Query("SELECT CAST(r.usageTime AS date) as date, SUM(r.cost) as revenue " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
           "GROUP BY CAST(r.usageTime AS date) " +
           "ORDER BY date")
    List<Object[]> getRevenueTrendByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get revenue by subscription tier
     */
    @Query("SELECT COALESCE(r.subscription.client.subscriptionTierName, 'UNKNOWN'), SUM(r.cost) as revenue " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
           "GROUP BY COALESCE(r.subscription.client.subscriptionTierName, 'UNKNOWN')")
    List<Object[]> getRevenueBySubscriptionTier(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get top tools by revenue
     */
    @Query("SELECT r.subscription.tool.id, r.subscription.tool.name, " +
           "SUM(r.cost) as revenue, COUNT(DISTINCT r.subscription.client.id) as subscribers " +
           "FROM ToolUsageRecord r " +
           "WHERE r.usageTime BETWEEN :start AND :end " +
           "GROUP BY r.subscription.tool.id, r.subscription.tool.name " +
           "ORDER BY revenue DESC")
    List<Object[]> getTopToolsByRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
} 
