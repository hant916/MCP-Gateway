package com.mcpgateway.controller;

import com.mcpgateway.domain.User;
import com.mcpgateway.dto.analytics.RevenueStatsDTO;
import com.mcpgateway.dto.analytics.UsageStatsDTO;
import com.mcpgateway.ratelimit.RateLimit;
import com.mcpgateway.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Controller for analytics and reporting
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Analytics and reporting APIs")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/usage")
    @Operation(summary = "Get usage statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<UsageStatsDTO> getUsageStats(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Group by: day, week, month")
            @RequestParam(defaultValue = "day") String groupBy,
            @AuthenticationPrincipal User user) {

        UsageStatsDTO stats = analyticsService.getUsageStats(startDate, endDate, groupBy);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get revenue statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<RevenueStatsDTO> getRevenueStats(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user) {

        RevenueStatsDTO stats = analyticsService.getRevenueStats(startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    @Operation(summary = "Get user growth statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Map<String, Object>> getUserGrowthStats(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user) {

        Map<String, Object> stats = analyticsService.getUserGrowthStats(startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard summary statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @AuthenticationPrincipal User user) {

        // Last 30 days by default
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        UsageStatsDTO usageStats = analyticsService.getUsageStats(startDate, endDate, "day");
        RevenueStatsDTO revenueStats = analyticsService.getRevenueStats(startDate, endDate);
        Map<String, Object> userStats = analyticsService.getUserGrowthStats(startDate, endDate);

        Map<String, Object> dashboard = Map.of(
                "usage", usageStats,
                "revenue", revenueStats,
                "users", userStats,
                "period", Map.of(
                        "startDate", startDate,
                        "endDate", endDate
                )
        );

        return ResponseEntity.ok(dashboard);
    }
}
