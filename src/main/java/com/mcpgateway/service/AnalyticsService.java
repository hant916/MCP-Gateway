package com.mcpgateway.service;

import com.mcpgateway.domain.*;
import com.mcpgateway.dto.analytics.RevenueStatsDTO;
import com.mcpgateway.dto.analytics.UsageStatsDTO;
import com.mcpgateway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analytics and reporting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ToolUsageRecordRepository usageRecordRepository;
    private final ToolSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final McpToolRepository toolRepository;
    private final SessionRepository sessionRepository;

    /**
     * Get usage statistics
     */
    @Transactional(readOnly = true)
    public UsageStatsDTO getUsageStats(LocalDate startDate, LocalDate endDate, String groupBy) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // Total requests
        Long totalRequests = usageRecordRepository.countByCreatedAtBetween(start, end);

        // Active users
        Long activeUsers = usageRecordRepository.countDistinctUsersByCreatedAtBetween(start, end);

        // Request trend (daily)
        List<UsageStatsDTO.DataPoint> requestTrend = usageRecordRepository
                .getRequestTrendByDateRange(start, end)
                .stream()
                .map(result -> UsageStatsDTO.DataPoint.builder()
                        .date(((java.sql.Date) result[0]).toLocalDate())
                        .value(((Number) result[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Active user trend (daily)
        List<UsageStatsDTO.DataPoint> userTrend = usageRecordRepository
                .getActiveUserTrendByDateRange(start, end)
                .stream()
                .map(result -> UsageStatsDTO.DataPoint.builder()
                        .date(((java.sql.Date) result[0]).toLocalDate())
                        .value(((Number) result[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Top tools by usage
        List<UsageStatsDTO.ToolUsageStats> topTools = usageRecordRepository
                .getTopToolsByUsage(start, end, PageRequest.of(0, 10))
                .stream()
                .map(result -> {
                    UUID toolId = (UUID) result[0];
                    String toolName = (String) result[1];
                    Long requestCount = ((Number) result[2]).longValue();
                    Long uniqueUsers = ((Number) result[3]).longValue();

                    return UsageStatsDTO.ToolUsageStats.builder()
                            .toolId(toolId.toString())
                            .toolName(toolName)
                            .requestCount(requestCount)
                            .uniqueUsers(uniqueUsers.intValue())
                            .build();
                })
                .collect(Collectors.toList());

        // Usage by transport type
        Map<String, Long> usageByTransport = usageRecordRepository
                .getUsageByTransportType(start, end)
                .stream()
                .collect(Collectors.toMap(
                        result -> (String) result[0],
                        result -> ((Number) result[1]).longValue()
                ));

        // Usage by hour (for heat map)
        Map<Integer, Long> usageByHour = usageRecordRepository
                .getUsageByHour(start, end)
                .stream()
                .collect(Collectors.toMap(
                        result -> ((Number) result[0]).intValue(),
                        result -> ((Number) result[1]).longValue()
                ));

        // Calculate average response time
        Double avgResponseTime = usageRecordRepository.getAverageResponseTime(start, end);

        return UsageStatsDTO.builder()
                .totalRequests(totalRequests != null ? totalRequests : 0L)
                .activeUsers(activeUsers != null ? activeUsers : 0L)
                .requestTrend(requestTrend)
                .userTrend(userTrend)
                .topTools(topTools)
                .usageByTransport(usageByTransport)
                .usageByHour(usageByHour)
                .averageResponseTime(avgResponseTime != null ? avgResponseTime : 0.0)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    /**
     * Get revenue statistics
     */
    @Transactional(readOnly = true)
    public RevenueStatsDTO getRevenueStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // Total revenue from usage records
        BigDecimal totalRevenue = usageRecordRepository.getTotalRevenue(start, end);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        // Active users for ARPU calculation
        Long activeUsers = usageRecordRepository.countDistinctUsersByCreatedAtBetween(start, end);
        BigDecimal averageRevenuePerUser = BigDecimal.ZERO;
        if (activeUsers != null && activeUsers > 0) {
            averageRevenuePerUser = totalRevenue.divide(
                    BigDecimal.valueOf(activeUsers),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        // Monthly Recurring Revenue (MRR) - sum of all active monthly subscriptions
        BigDecimal monthlyRecurringRevenue = calculateMRR();

        // Revenue trend (daily)
        List<RevenueStatsDTO.RevenueDataPoint> revenueTrend = usageRecordRepository
                .getRevenueTrendByDateRange(start, end)
                .stream()
                .map(result -> {
                    LocalDate date = ((java.sql.Date) result[0]).toLocalDate();
                    BigDecimal revenue = (BigDecimal) result[1];

                    // Get subscription changes for this date
                    LocalDateTime dayStart = date.atStartOfDay();
                    LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

                    Integer newSubs = subscriptionRepository.countNewSubscriptions(dayStart, dayEnd);
                    Integer churnedSubs = subscriptionRepository.countChurnedSubscriptions(dayStart, dayEnd);

                    return RevenueStatsDTO.RevenueDataPoint.builder()
                            .date(date)
                            .revenue(revenue != null ? revenue : BigDecimal.ZERO)
                            .newSubscriptions(newSubs != null ? newSubs : 0)
                            .churnedSubscriptions(churnedSubs != null ? churnedSubs : 0)
                            .build();
                })
                .collect(Collectors.toList());

        // Revenue by subscription tier
        Map<String, BigDecimal> revenueByTier = usageRecordRepository
                .getRevenueBySubscriptionTier(start, end)
                .stream()
                .collect(Collectors.toMap(
                        result -> result[0] != null ? result[0].toString() : "UNKNOWN",
                        result -> (BigDecimal) result[1]
                ));

        // Top revenue tools
        List<RevenueStatsDTO.ToolRevenueStats> topRevenueTools = usageRecordRepository
                .getTopToolsByRevenue(start, end, PageRequest.of(0, 10))
                .stream()
                .map(result -> {
                    UUID toolId = (UUID) result[0];
                    String toolName = (String) result[1];
                    BigDecimal revenue = (BigDecimal) result[2];
                    Integer subscribers = ((Number) result[3]).intValue();

                    return RevenueStatsDTO.ToolRevenueStats.builder()
                            .toolId(toolId.toString())
                            .toolName(toolName)
                            .revenue(revenue != null ? revenue : BigDecimal.ZERO)
                            .subscribers(subscribers)
                            .build();
                })
                .collect(Collectors.toList());

        // Subscription statistics
        RevenueStatsDTO.SubscriptionStats subscriptionStats = getSubscriptionStats(startDate, endDate);

        return RevenueStatsDTO.builder()
                .totalRevenue(totalRevenue)
                .averageRevenuePerUser(averageRevenuePerUser)
                .monthlyRecurringRevenue(monthlyRecurringRevenue)
                .startDate(startDate)
                .endDate(endDate)
                .revenueTrend(revenueTrend)
                .revenueByTier(revenueByTier)
                .topRevenueTools(topRevenueTools)
                .subscriptionStats(subscriptionStats)
                .build();
    }

    /**
     * Calculate Monthly Recurring Revenue (MRR)
     */
    private BigDecimal calculateMRR() {
        List<ToolSubscription> activeSubscriptions = subscriptionRepository
                .findByStatus(ToolSubscription.SubscriptionStatus.ACTIVE);

        BigDecimal mrr = BigDecimal.ZERO;
        for (ToolSubscription subscription : activeSubscriptions) {
            McpTool tool = subscription.getTool();
            if (tool.getPricingModel() == McpTool.PricingModel.MONTHLY) {
                mrr = mrr.add(tool.getPrice() != null ? tool.getPrice() : BigDecimal.ZERO);
            }
        }

        return mrr;
    }

    /**
     * Get subscription statistics
     */
    private RevenueStatsDTO.SubscriptionStats getSubscriptionStats(LocalDate startDate, LocalDate endDate) {
        // Total active subscriptions
        Integer totalActive = subscriptionRepository.countByStatus(
                ToolSubscription.SubscriptionStatus.ACTIVE
        );

        // Get current month boundaries
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay();

        // New subscriptions this month
        Integer newThisMonth = subscriptionRepository.countNewSubscriptions(monthStart, monthEnd);

        // Churned subscriptions this month
        Integer churnedThisMonth = subscriptionRepository.countChurnedSubscriptions(monthStart, monthEnd);

        // Calculate churn rate
        BigDecimal churnRate = BigDecimal.ZERO;
        if (totalActive != null && totalActive > 0 && churnedThisMonth != null) {
            churnRate = BigDecimal.valueOf(churnedThisMonth)
                    .divide(BigDecimal.valueOf(totalActive), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return RevenueStatsDTO.SubscriptionStats.builder()
                .totalActiveSubscriptions(totalActive != null ? totalActive : 0)
                .newSubscriptionsThisMonth(newThisMonth != null ? newThisMonth : 0)
                .churnedSubscriptionsThisMonth(churnedThisMonth != null ? churnedThisMonth : 0)
                .churnRate(churnRate)
                .build();
    }

    /**
     * Get user growth statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserGrowthStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        Map<String, Object> stats = new HashMap<>();

        // Total users
        Long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);

        // New users in period
        Long newUsers = userRepository.countByCreatedAtBetween(start, end);
        stats.put("newUsers", newUsers);

        // User registration trend
        List<Map<String, Object>> registrationTrend = userRepository
                .getUserRegistrationTrend(start, end)
                .stream()
                .map(result -> {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("date", ((java.sql.Date) result[0]).toLocalDate());
                    dataPoint.put("count", ((Number) result[1]).longValue());
                    return dataPoint;
                })
                .collect(Collectors.toList());
        stats.put("registrationTrend", registrationTrend);

        // Active users (users with usage in period)
        Long activeUsers = usageRecordRepository.countDistinctUsersByCreatedAtBetween(start, end);
        stats.put("activeUsers", activeUsers);

        // User retention rate
        if (newUsers != null && newUsers > 0 && activeUsers != null) {
            BigDecimal retentionRate = BigDecimal.valueOf(activeUsers)
                    .divide(BigDecimal.valueOf(totalUsers), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.put("retentionRate", retentionRate);
        } else {
            stats.put("retentionRate", BigDecimal.ZERO);
        }

        // Users by subscription tier
        Map<String, Long> usersByTier = userRepository.getUsersBySubscriptionTier()
                .stream()
                .collect(Collectors.toMap(
                        result -> result[0] != null ? result[0].toString() : "FREE",
                        result -> ((Number) result[1]).longValue()
                ));
        stats.put("usersByTier", usersByTier);

        return stats;
    }
}
