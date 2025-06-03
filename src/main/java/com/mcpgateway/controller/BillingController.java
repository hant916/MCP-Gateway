package com.mcpgateway.controller;

import com.mcpgateway.dto.billing.UsageRecordDTO;
import com.mcpgateway.dto.billing.UsageSummaryDTO;
import com.mcpgateway.service.UsageBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "计费和使用量统计APIs")
@SecurityRequirement(name = "bearerAuth")
public class BillingController {

    private final UsageBillingService usageBillingService;

    @GetMapping("/usage")
    @Operation(summary = "查询使用记录", description = "根据用户ID和时间范围查询使用记录")
    public ResponseEntity<Page<UsageRecordDTO>> getUsageRecords(
            @RequestParam UUID userId,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "开始时间 (ISO 8601格式)", example = "2024-01-01T00:00:00Z")
            ZonedDateTime startTime,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "结束时间 (ISO 8601格式)", example = "2024-01-31T23:59:59Z")
            ZonedDateTime endTime,
            Pageable pageable) {
        
        Page<UsageRecordDTO> records = usageBillingService.getUsageRecords(userId, startTime, endTime, pageable);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/usage/session/{sessionId}")
    @Operation(summary = "查询会话使用记录", description = "获取特定会话的所有使用记录")
    public ResponseEntity<List<UsageRecordDTO>> getSessionUsageRecords(
            @PathVariable UUID sessionId) {
        
        List<UsageRecordDTO> records = usageBillingService.getSessionUsageRecords(sessionId);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/usage/summary")
    @Operation(summary = "获取使用量摘要", description = "获取用户的使用量统计和费用汇总")
    public ResponseEntity<UsageSummaryDTO> getUsageSummary(
            @RequestParam UUID userId,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "统计开始时间", example = "2024-01-01T00:00:00Z")
            ZonedDateTime startTime,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "统计结束时间", example = "2024-01-31T23:59:59Z")
            ZonedDateTime endTime) {
        
        UsageSummaryDTO summary = usageBillingService.getUsageSummary(userId, startTime, endTime);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/cost/current")
    @Operation(summary = "获取当前总费用", description = "获取用户的累计费用")
    public ResponseEntity<Map<String, Object>> getCurrentCost(
            @RequestParam UUID userId) {
        
        BigDecimal currentCost = usageBillingService.getCurrentCost(userId);
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "currentCost", currentCost,
            "currency", "USD",
            "timestamp", ZonedDateTime.now()
        ));
    }

    @PostMapping("/test/record")
    @Operation(summary = "测试记录使用量", description = "手动记录使用量用于测试")
    public ResponseEntity<Map<String, String>> testRecordUsage(
            @RequestParam UUID sessionId,
            @RequestParam String apiEndpoint,
            @RequestParam(defaultValue = "POST") String httpMethod,
            @RequestParam(defaultValue = "200") Integer statusCode,
            @RequestParam(required = false) Long requestSize,
            @RequestParam(required = false) Long responseSize,
            @RequestParam(required = false) Integer processingMs) {
        
        try {
            usageBillingService.recordUsage(sessionId, apiEndpoint, httpMethod, statusCode, 
                null, requestSize, responseSize, processingMs, null, null);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Usage recorded successfully",
                "sessionId", sessionId.toString(),
                "apiEndpoint", apiEndpoint
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "计费服务健康检查", description = "检查计费服务状态")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "billing",
            "timestamp", ZonedDateTime.now(),
            "version", "1.0.0"
        ));
    }
} 