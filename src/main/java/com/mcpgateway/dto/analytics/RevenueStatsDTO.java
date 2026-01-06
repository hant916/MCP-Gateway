package com.mcpgateway.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO for revenue statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatsDTO {

    // Overall revenue
    private BigDecimal totalRevenue;
    private BigDecimal averageRevenuePerUser;
    private BigDecimal monthlyRecurringRevenue; // MRR

    // Time period
    private LocalDate startDate;
    private LocalDate endDate;

    // Revenue trend (time series)
    private List<RevenueDataPoint> revenueTrend;

    // Revenue by subscription tier
    private Map<String, BigDecimal> revenueByTier;

    // Revenue by tool
    private List<ToolRevenueStats> topRevenueTools;

    // Subscription statistics
    private SubscriptionStats subscriptionStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueDataPoint {
        private LocalDate date;
        private BigDecimal revenue;
        private Integer newSubscriptions;
        private Integer churnedSubscriptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolRevenueStats {
        private String toolId;
        private String toolName;
        private BigDecimal revenue;
        private Integer subscribers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionStats {
        private Integer totalActiveSubscriptions;
        private Integer newSubscriptionsThisMonth;
        private Integer churnedSubscriptionsThisMonth;
        private BigDecimal churnRate; // Percentage
    }
}
