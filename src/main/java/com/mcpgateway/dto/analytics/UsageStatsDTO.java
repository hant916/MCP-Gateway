package com.mcpgateway.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO for usage statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsDTO {

    // Overall statistics
    private Long totalRequests;
    private Long totalUsers;
    private Long activeUsers;
    private Long totalSessions;

    // Time period
    private LocalDate startDate;
    private LocalDate endDate;

    // Trend data (time series)
    private List<DataPoint> requestTrend;
    private List<DataPoint> userTrend;

    // Top tools by usage
    private List<ToolUsageStats> topTools;

    // Usage by transport type
    private Map<String, Long> usageByTransport;

    // Usage by hour (for heat map)
    private Map<Integer, Long> usageByHour;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private LocalDate date;
        private Long value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolUsageStats {
        private String toolId;
        private String toolName;
        private Long requestCount;
        private Long uniqueUsers;
    }
}
