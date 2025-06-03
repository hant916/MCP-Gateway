package com.mcpgateway.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageSummaryDTO {
    private UUID userId;
    private ZonedDateTime periodStart;
    private ZonedDateTime periodEnd;
    private Long totalCalls;
    private BigDecimal totalCost;
    private Long successfulCalls;
    private Long failedCalls;
    private Long timeoutCalls;
    private Long rateLimitedCalls;
    private BigDecimal averageCostPerCall;
    private Long totalRequestSize;
    private Long totalResponseSize;
    private Integer averageProcessingMs;
    
    // API端点统计
    private List<ApiEndpointStats> apiEndpointStats;
    
    // 每日统计
    private List<DailyStats> dailyStats;
    
    // 按状态统计
    private Map<String, StatusStats> statusStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointStats {
        private String apiEndpoint;
        private Long callCount;
        private BigDecimal totalCost;
        private BigDecimal averageCost;
        private Long successfulCalls;
        private Long failedCalls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDate date;
        private Long callCount;
        private BigDecimal totalCost;
        private Long successfulCalls;
        private Long failedCalls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusStats {
        private String status;
        private Long count;
        private BigDecimal totalCost;
        private Double percentage;
    }

    // 计算方法
    public void calculateDerivedStats() {
        if (totalCalls != null && totalCalls > 0 && totalCost != null) {
            this.averageCostPerCall = totalCost.divide(new BigDecimal(totalCalls), 6, BigDecimal.ROUND_HALF_UP);
        } else {
            this.averageCostPerCall = BigDecimal.ZERO;
        }
    }
} 