package com.mcpgateway.service;

import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.dto.analytics.RevenueStatsDTO;
import com.mcpgateway.dto.analytics.UsageStatsDTO;
import com.mcpgateway.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ToolUsageRecordRepository usageRecordRepository;

    @Mock
    private ToolSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private McpToolRepository toolRepository;

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    @BeforeEach
    void setUp() {
        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 1, 31);
        startDateTime = startDate.atStartOfDay();
        endDateTime = endDate.plusDays(1).atStartOfDay();
    }

    @Test
    void getUsageStats_ShouldReturnCompleteStatistics() {
        // Arrange
        when(usageRecordRepository.countByCreatedAtBetween(any(), any()))
                .thenReturn(1000L);
        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(50L);

        // Request trend data
        List<Object[]> trendData = Arrays.asList(
                new Object[]{Date.valueOf(LocalDate.of(2024, 1, 1)), 100L},
                new Object[]{Date.valueOf(LocalDate.of(2024, 1, 2)), 150L}
        );
        when(usageRecordRepository.getRequestTrendByDateRange(any(), any()))
                .thenReturn(trendData);

        // User trend data
        when(usageRecordRepository.getActiveUserTrendByDateRange(any(), any()))
                .thenReturn(trendData);

        // Top tools data
        List<Object[]> topToolsData = Arrays.asList(
                new Object[]{UUID.randomUUID(), "Tool A", 500L, 25L},
                new Object[]{UUID.randomUUID(), "Tool B", 300L, 20L}
        );
        when(usageRecordRepository.getTopToolsByUsage(any(), any(), any(Pageable.class)))
                .thenReturn(topToolsData);

        // Usage by transport
        List<Object[]> transportData = Arrays.asList(
                new Object[]{"SSE", 400L},
                new Object[]{"WEBSOCKET", 600L}
        );
        when(usageRecordRepository.getUsageByTransportType(any(), any()))
                .thenReturn(transportData);

        // Usage by hour
        List<Object[]> hourData = Arrays.asList(
                new Object[]{9, 100L},
                new Object[]{10, 150L}
        );
        when(usageRecordRepository.getUsageByHour(any(), any()))
                .thenReturn(hourData);

        // Average response time
        when(usageRecordRepository.getAverageResponseTime(any(), any()))
                .thenReturn(250.5);

        // Act
        UsageStatsDTO result = analyticsService.getUsageStats(startDate, endDate, "day");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalRequests()).isEqualTo(1000L);
        assertThat(result.getActiveUsers()).isEqualTo(50L);
        assertThat(result.getRequestTrend()).hasSize(2);
        assertThat(result.getUserTrend()).hasSize(2);
        assertThat(result.getTopTools()).hasSize(2);
        assertThat(result.getUsageByTransport()).hasSize(2);
        assertThat(result.getUsageByHour()).hasSize(2);
        assertThat(result.getAverageResponseTime()).isEqualTo(250.5);
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getEndDate()).isEqualTo(endDate);
    }

    @Test
    void getUsageStats_WithNullCounts_ShouldReturnZero() {
        // Arrange
        when(usageRecordRepository.countByCreatedAtBetween(any(), any()))
                .thenReturn(null);
        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(null);
        when(usageRecordRepository.getRequestTrendByDateRange(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getActiveUserTrendByDateRange(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getTopToolsByUsage(any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getUsageByTransportType(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getUsageByHour(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getAverageResponseTime(any(), any()))
                .thenReturn(null);

        // Act
        UsageStatsDTO result = analyticsService.getUsageStats(startDate, endDate, "day");

        // Assert
        assertThat(result.getTotalRequests()).isEqualTo(0L);
        assertThat(result.getActiveUsers()).isEqualTo(0L);
        assertThat(result.getAverageResponseTime()).isEqualTo(0.0);
    }

    @Test
    void getRevenueStats_ShouldReturnCompleteStatistics() {
        // Arrange
        BigDecimal totalRevenue = new BigDecimal("5000.00");
        when(usageRecordRepository.getTotalRevenue(any(), any()))
                .thenReturn(totalRevenue);
        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(100L);

        // Active subscriptions for MRR
        ToolSubscription activeSubscription = new ToolSubscription();
        activeSubscription.setMonthlyQuota(1000);
        List<ToolSubscription> activeSubscriptions = Arrays.asList(activeSubscription);
        when(subscriptionRepository.findByStatus(ToolSubscription.SubscriptionStatus.ACTIVE))
                .thenReturn(activeSubscriptions);

        // Revenue trend
        List<Object[]> revenueTrend = Arrays.asList(
                new Object[]{Date.valueOf(LocalDate.of(2024, 1, 1)), new BigDecimal("100.00")},
                new Object[]{Date.valueOf(LocalDate.of(2024, 1, 2)), new BigDecimal("150.00")}
        );
        when(usageRecordRepository.getRevenueTrendByDateRange(any(), any()))
                .thenReturn(revenueTrend);

        // Subscription changes
        when(subscriptionRepository.countNewSubscriptions(any(), any()))
                .thenReturn(10);
        when(subscriptionRepository.countChurnedSubscriptions(any(), any()))
                .thenReturn(2);

        // Revenue by tier
        List<Object[]> revenueByTier = Arrays.asList(
                new Object[]{"BASIC", new BigDecimal("2000.00")},
                new Object[]{"PRO", new BigDecimal("3000.00")}
        );
        when(usageRecordRepository.getRevenueBySubscriptionTier(any(), any()))
                .thenReturn(revenueByTier);

        // Top revenue tools
        List<Object[]> topRevenueTools = Arrays.asList(
                new Object[]{UUID.randomUUID(), "Tool A", new BigDecimal("2500.00"), 50},
                new Object[]{UUID.randomUUID(), "Tool B", new BigDecimal("1500.00"), 30}
        );
        when(usageRecordRepository.getTopToolsByRevenue(any(), any(), any(Pageable.class)))
                .thenReturn(topRevenueTools);

        // Subscription stats
        when(subscriptionRepository.countByStatus(ToolSubscription.SubscriptionStatus.ACTIVE))
                .thenReturn(200);

        // Act
        RevenueStatsDTO result = analyticsService.getRevenueStats(startDate, endDate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalRevenue()).isEqualTo(totalRevenue);
        assertThat(result.getAverageRevenuePerUser()).isEqualTo(new BigDecimal("50.00"));
        assertThat(result.getRevenueTrend()).hasSize(2);
        assertThat(result.getRevenueByTier()).hasSize(2);
        assertThat(result.getTopRevenueTools()).hasSize(2);
        assertThat(result.getSubscriptionStats()).isNotNull();
        assertThat(result.getSubscriptionStats().getTotalActiveSubscriptions()).isEqualTo(200);
    }

    @Test
    void getRevenueStats_WithZeroUsers_ShouldHaveZeroARPU() {
        // Arrange
        when(usageRecordRepository.getTotalRevenue(any(), any()))
                .thenReturn(new BigDecimal("1000.00"));
        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(0L);
        when(subscriptionRepository.findByStatus(any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getRevenueTrendByDateRange(any(), any()))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.countNewSubscriptions(any(), any()))
                .thenReturn(0);
        when(subscriptionRepository.countChurnedSubscriptions(any(), any()))
                .thenReturn(0);
        when(usageRecordRepository.getRevenueBySubscriptionTier(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getTopToolsByRevenue(any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.countByStatus(any()))
                .thenReturn(0);

        // Act
        RevenueStatsDTO result = analyticsService.getRevenueStats(startDate, endDate);

        // Assert
        assertThat(result.getAverageRevenuePerUser()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void getRevenueStats_WithNullRevenue_ShouldReturnZero() {
        // Arrange
        when(usageRecordRepository.getTotalRevenue(any(), any()))
                .thenReturn(null);
        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(0L);
        when(subscriptionRepository.findByStatus(any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getRevenueTrendByDateRange(any(), any()))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.countNewSubscriptions(any(), any()))
                .thenReturn(0);
        when(subscriptionRepository.countChurnedSubscriptions(any(), any()))
                .thenReturn(0);
        when(usageRecordRepository.getRevenueBySubscriptionTier(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.getTopToolsByRevenue(any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.countByStatus(any()))
                .thenReturn(0);

        // Act
        RevenueStatsDTO result = analyticsService.getRevenueStats(startDate, endDate);

        // Assert
        assertThat(result.getTotalRevenue()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void getUserGrowthStats_ShouldReturnCompleteStatistics() {
        // Arrange
        when(userRepository.count()).thenReturn(1000L);
        when(userRepository.countByCreatedAtBetween(any(), any()))
                .thenReturn(50L);

        List<Object[]> registrationTrend = Arrays.asList(
                new Object[]{Date.valueOf(LocalDate.of(2024, 1, 1)), 10L},
                new Object[]{Date.valueOf(LocalDate.of(2024, 1, 2)), 15L}
        );
        when(userRepository.getUserRegistrationTrend(any(), any()))
                .thenReturn(registrationTrend);

        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(800L);

        List<Object[]> usersByTier = Arrays.asList(
                new Object[]{"FREE", 500L},
                new Object[]{"BASIC", 300L},
                new Object[]{"PRO", 200L}
        );
        when(userRepository.getUsersBySubscriptionTier())
                .thenReturn(usersByTier);

        // Act
        Map<String, Object> result = analyticsService.getUserGrowthStats(startDate, endDate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("totalUsers")).isEqualTo(1000L);
        assertThat(result.get("newUsers")).isEqualTo(50L);
        assertThat(result.get("activeUsers")).isEqualTo(800L);
        assertThat(result.get("registrationTrend")).isNotNull();
        assertThat(result.get("usersByTier")).isNotNull();
        assertThat(result.get("retentionRate")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) result.get("registrationTrend");
        assertThat(trend).hasSize(2);

        @SuppressWarnings("unchecked")
        Map<String, Long> tierMap = (Map<String, Long>) result.get("usersByTier");
        assertThat(tierMap).hasSize(3);
        assertThat(tierMap.get("FREE")).isEqualTo(500L);
    }

    @Test
    void getUserGrowthStats_WithNoNewUsers_ShouldReturnZeroRetentionRate() {
        // Arrange
        when(userRepository.count()).thenReturn(1000L);
        when(userRepository.countByCreatedAtBetween(any(), any()))
                .thenReturn(0L);
        when(userRepository.getUserRegistrationTrend(any(), any()))
                .thenReturn(Collections.emptyList());
        when(usageRecordRepository.countDistinctUsersByCreatedAtBetween(any(), any()))
                .thenReturn(0L);
        when(userRepository.getUsersBySubscriptionTier())
                .thenReturn(Collections.emptyList());

        // Act
        Map<String, Object> result = analyticsService.getUserGrowthStats(startDate, endDate);

        // Assert
        assertThat(result.get("retentionRate")).isEqualTo(BigDecimal.ZERO);
    }
}
