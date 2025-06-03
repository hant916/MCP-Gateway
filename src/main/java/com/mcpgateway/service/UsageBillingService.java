package com.mcpgateway.service;

import com.mcpgateway.domain.BillingRule;
import com.mcpgateway.domain.Session;
import com.mcpgateway.domain.UsageRecord;
import com.mcpgateway.dto.billing.UsageRecordDTO;
import com.mcpgateway.dto.billing.UsageSummaryDTO;
import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.repository.BillingRuleRepository;
import com.mcpgateway.repository.SessionRepository;
import com.mcpgateway.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageBillingService {
    
    private final UsageRecordRepository usageRecordRepository;
    private final BillingRuleRepository billingRuleRepository;
    private final SessionRepository sessionRepository;

    /**
     * 异步记录使用量 - 主要入口点
     */
    @Async
    public void recordUsageAsync(UUID sessionId, String apiEndpoint, String httpMethod, 
                                Integer statusCode, MessageRequest message) {
        try {
            recordUsage(sessionId, apiEndpoint, httpMethod, statusCode, message, null, null, null, null, null);
        } catch (Exception e) {
            log.error("Failed to record usage asynchronously for session: {}, endpoint: {}", 
                sessionId, apiEndpoint, e);
        }
    }

    /**
     * 记录使用量 - 完整版本
     */
    @Transactional
    public UsageRecord recordUsage(UUID sessionId, String apiEndpoint, String httpMethod,
                                  Integer statusCode, MessageRequest message, 
                                  Long requestSize, Long responseSize, Integer processingMs,
                                  String clientIp, String userAgent) {
        
        // 获取session信息
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // 创建使用记录
        UsageRecord usageRecord = new UsageRecord(sessionId, session.getUser().getId(), 
            apiEndpoint, httpMethod, statusCode);

        // 设置额外信息
        if (requestSize != null) usageRecord.setRequestSize(requestSize);
        if (responseSize != null) usageRecord.setResponseSize(responseSize);
        if (processingMs != null) usageRecord.setProcessingMs(processingMs);
        if (clientIp != null) usageRecord.setClientIp(clientIp);
        if (userAgent != null) usageRecord.setUserAgent(userAgent);

        // 设置消息类型
        if (message != null) {
            if (message.isJsonRpcFormat()) {
                usageRecord.setMessageType(message.getMethod());
            } else {
                usageRecord.setMessageType(message.getType());
            }
        }

        // 设置错误信息（如果状态码表示失败）
        if (statusCode >= 400) {
            usageRecord.setErrorMessage("HTTP " + statusCode + " error");
        }

        // 计算费用
        BigDecimal cost = calculateCost(apiEndpoint, httpMethod, requestSize, responseSize, 
            processingMs, usageRecord.getBillingStatus());
        usageRecord.setCostAmount(cost);

        // 检查重复记录
        Long duplicateCount = usageRecordRepository.countDuplicateRecords(
            sessionId, apiEndpoint, usageRecord.getTimestamp());
        if (duplicateCount > 0) {
            log.warn("Potential duplicate usage record detected for session: {}, endpoint: {}", 
                sessionId, apiEndpoint);
        }

        // 保存记录
        UsageRecord savedRecord = usageRecordRepository.save(usageRecord);
        
        log.info("Recorded usage: sessionId={}, userId={}, endpoint={}, cost={}, status={}", 
            sessionId, session.getUser().getId(), apiEndpoint, cost, statusCode);

        return savedRecord;
    }

    /**
     * 计算费用
     */
    public BigDecimal calculateCost(String apiEndpoint, String httpMethod, 
                                   Long requestSize, Long responseSize, 
                                   Integer processingMs, UsageRecord.BillingStatus billingStatus) {
        
        // 获取匹配的计费规则
        BillingRule rule = findBestMatchingRule(apiEndpoint, httpMethod);
        
        if (rule == null) {
            log.warn("No billing rule found for endpoint: {} {}", httpMethod, apiEndpoint);
            return BigDecimal.ZERO;
        }

        // 检查是否对失败调用计费
        if (billingStatus != UsageRecord.BillingStatus.SUCCESS && !rule.getBillFailedCalls()) {
            log.debug("Not billing failed call for endpoint: {} {}, status: {}", 
                httpMethod, apiEndpoint, billingStatus);
            return BigDecimal.ZERO;
        }

        // 使用规则计算费用
        BigDecimal cost = rule.calculateCost(requestSize, responseSize, processingMs);
        
        log.debug("Calculated cost {} using rule '{}' for endpoint {} {}", 
            cost, rule.getRuleName(), httpMethod, apiEndpoint);

        return cost;
    }

    /**
     * 查找最佳匹配的计费规则
     */
    private BillingRule findBestMatchingRule(String apiEndpoint, String httpMethod) {
        List<BillingRule> activeRules = billingRuleRepository.findActiveRulesOrderByPriority();
        
        // 首先尝试精确匹配
        for (BillingRule rule : activeRules) {
            if (rule.matches(apiEndpoint, httpMethod)) {
                return rule;
            }
        }

        // 如果没有匹配的规则，尝试查找默认规则
        List<BillingRule> defaultRules = billingRuleRepository.findDefaultRules();
        return defaultRules.isEmpty() ? null : defaultRules.get(0);
    }

    /**
     * 查询使用记录
     */
    @Transactional(readOnly = true)
    public Page<UsageRecordDTO> getUsageRecords(UUID userId, ZonedDateTime startTime, 
                                               ZonedDateTime endTime, Pageable pageable) {
        Timestamp start = startTime != null ? Timestamp.from(startTime.toInstant()) : null;
        Timestamp end = endTime != null ? Timestamp.from(endTime.toInstant()) : null;

        Page<UsageRecord> records;
        if (start != null && end != null) {
            records = usageRecordRepository.findByUserIdAndTimestampBetween(userId, start, end, pageable);
        } else {
            records = usageRecordRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }

        return records.map(UsageRecordDTO::fromEntity);
    }

    /**
     * 获取使用量摘要
     */
    @Transactional(readOnly = true)
    public UsageSummaryDTO getUsageSummary(UUID userId, ZonedDateTime startTime, ZonedDateTime endTime) {
        Timestamp start = startTime != null ? Timestamp.from(startTime.toInstant()) : 
            Timestamp.from(ZonedDateTime.now().minusDays(30).toInstant());
        Timestamp end = endTime != null ? Timestamp.from(endTime.toInstant()) : 
            Timestamp.from(ZonedDateTime.now().toInstant());

        // 基础统计
        Long totalCalls = usageRecordRepository.countByUserIdAndTimestampBetween(userId, start, end);
        BigDecimal totalCost = usageRecordRepository.sumCostByUserIdAndTimestampBetween(userId, start, end);

        // 构建摘要
        UsageSummaryDTO summary = UsageSummaryDTO.builder()
            .userId(userId)
            .periodStart(startTime != null ? startTime : ZonedDateTime.now().minusDays(30))
            .periodEnd(endTime != null ? endTime : ZonedDateTime.now())
            .totalCalls(totalCalls)
            .totalCost(totalCost)
            .build();

        // 按API端点统计
        List<Object[]> apiStats = usageRecordRepository.getUsageStatsByApiEndpoint(userId);
        List<UsageSummaryDTO.ApiEndpointStats> apiEndpointStats = apiStats.stream()
            .map(row -> UsageSummaryDTO.ApiEndpointStats.builder()
                .apiEndpoint((String) row[0])
                .callCount((Long) row[1])
                .totalCost((BigDecimal) row[2])
                .build())
            .collect(Collectors.toList());
        summary.setApiEndpointStats(apiEndpointStats);

        // 每日统计
        List<Object[]> dailyStats = usageRecordRepository.getDailyUsageStats(userId, start, end);
        List<UsageSummaryDTO.DailyStats> dailyStatsList = dailyStats.stream()
            .map(row -> UsageSummaryDTO.DailyStats.builder()
                .date(((java.sql.Date) row[0]).toLocalDate())
                .callCount((Long) row[1])
                .totalCost((BigDecimal) row[2])
                .build())
            .collect(Collectors.toList());
        summary.setDailyStats(dailyStatsList);

        // 按状态统计
        List<Object[]> statusStats = usageRecordRepository.getUsageStatsByStatus(userId);
        Map<String, UsageSummaryDTO.StatusStats> statusStatsMap = statusStats.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> UsageSummaryDTO.StatusStats.builder()
                    .status(row[0].toString())
                    .count((Long) row[1])
                    .totalCost((BigDecimal) row[2])
                    .percentage(totalCalls > 0 ? (Long) row[1] * 100.0 / totalCalls : 0.0)
                    .build()
            ));
        summary.setStatusStats(statusStatsMap);

        // 计算派生统计
        summary.calculateDerivedStats();

        return summary;
    }

    /**
     * 获取当前费用（实时）
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentCost(UUID userId) {
        return usageRecordRepository.sumCostByUserId(userId);
    }

    /**
     * 数据清理 - 删除旧记录
     */
    @Transactional
    public void cleanupOldRecords(int daysToKeep) {
        ZonedDateTime cutoffTime = ZonedDateTime.now().minusDays(daysToKeep);
        Timestamp cutoffTimestamp = Timestamp.from(cutoffTime.toInstant());
        
        usageRecordRepository.deleteOldRecords(cutoffTimestamp);
        log.info("Cleaned up usage records older than {} days", daysToKeep);
    }

    /**
     * 获取会话的使用记录
     */
    @Transactional(readOnly = true)
    public List<UsageRecordDTO> getSessionUsageRecords(UUID sessionId) {
        List<UsageRecord> records = usageRecordRepository.findBySessionIdOrderByTimestampDesc(sessionId);
        return records.stream()
            .map(UsageRecordDTO::fromEntity)
            .collect(Collectors.toList());
    }
} 