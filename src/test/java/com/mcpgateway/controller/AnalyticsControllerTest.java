package com.mcpgateway.controller;

import com.mcpgateway.dto.analytics.RevenueStatsDTO;
import com.mcpgateway.dto.analytics.UsageStatsDTO;
import com.mcpgateway.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    private UsageStatsDTO testUsageStats;
    private RevenueStatsDTO testRevenueStats;
    private Map<String, Object> testUserStats;

    @BeforeEach
    void setUp() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        testUsageStats = UsageStatsDTO.builder()
                .totalRequests(1000L)
                .activeUsers(50L)
                .requestTrend(Collections.emptyList())
                .userTrend(Collections.emptyList())
                .topTools(Collections.emptyList())
                .usageByTransport(Map.of("SSE", 400L, "WEBSOCKET", 600L))
                .usageByHour(Map.of(9, 100L, 10, 150L))
                .averageResponseTime(250.5)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        testRevenueStats = RevenueStatsDTO.builder()
                .totalRevenue(new BigDecimal("5000.00"))
                .averageRevenuePerUser(new BigDecimal("100.00"))
                .monthlyRecurringRevenue(new BigDecimal("3000.00"))
                .startDate(startDate)
                .endDate(endDate)
                .revenueTrend(Collections.emptyList())
                .revenueByTier(Map.of("BASIC", new BigDecimal("2000.00")))
                .topRevenueTools(Collections.emptyList())
                .subscriptionStats(RevenueStatsDTO.SubscriptionStats.builder()
                        .totalActiveSubscriptions(200)
                        .newSubscriptionsThisMonth(20)
                        .churnedSubscriptionsThisMonth(5)
                        .churnRate(new BigDecimal("2.50"))
                        .build())
                .build();

        testUserStats = new HashMap<>();
        testUserStats.put("totalUsers", 1000L);
        testUserStats.put("newUsers", 50L);
        testUserStats.put("activeUsers", 800L);
        testUserStats.put("retentionRate", new BigDecimal("80.00"));
        testUserStats.put("registrationTrend", Collections.emptyList());
        testUserStats.put("usersByTier", Map.of("FREE", 500L, "BASIC", 300L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsageStats_ShouldReturnUsageStatistics() throws Exception {
        // Arrange
        when(analyticsService.getUsageStats(any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(testUsageStats);

        // Act & Assert
        mockMvc.perform(get("/api/v1/analytics/usage")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31")
                        .param("groupBy", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1000))
                .andExpect(jsonPath("$.activeUsers").value(50))
                .andExpect(jsonPath("$.averageResponseTime").value(250.5))
                .andExpect(jsonPath("$.usageByTransport.SSE").value(400))
                .andExpect(jsonPath("$.usageByTransport.WEBSOCKET").value(600));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRevenueStats_ShouldReturnRevenueStatistics() throws Exception {
        // Arrange
        when(analyticsService.getRevenueStats(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(testRevenueStats);

        // Act & Assert
        mockMvc.perform(get("/api/v1/analytics/revenue")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(5000.00))
                .andExpect(jsonPath("$.averageRevenuePerUser").value(100.00))
                .andExpect(jsonPath("$.monthlyRecurringRevenue").value(3000.00))
                .andExpect(jsonPath("$.subscriptionStats.totalActiveSubscriptions").value(200))
                .andExpect(jsonPath("$.subscriptionStats.churnRate").value(2.50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserGrowthStats_ShouldReturnUserStatistics() throws Exception {
        // Arrange
        when(analyticsService.getUserGrowthStats(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(testUserStats);

        // Act & Assert
        mockMvc.perform(get("/api/v1/analytics/users")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(1000))
                .andExpect(jsonPath("$.newUsers").value(50))
                .andExpect(jsonPath("$.activeUsers").value(800));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboardStats_ShouldReturnCombinedStatistics() throws Exception {
        // Arrange
        when(analyticsService.getUsageStats(any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(testUsageStats);
        when(analyticsService.getRevenueStats(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(testRevenueStats);
        when(analyticsService.getUserGrowthStats(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(testUserStats);

        // Act & Assert
        mockMvc.perform(get("/api/v1/analytics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage").exists())
                .andExpect(jsonPath("$.revenue").exists())
                .andExpect(jsonPath("$.users").exists())
                .andExpect(jsonPath("$.period").exists())
                .andExpect(jsonPath("$.usage.totalRequests").value(1000))
                .andExpect(jsonPath("$.revenue.totalRevenue").value(5000.00))
                .andExpect(jsonPath("$.users.totalUsers").value(1000));
    }
}
