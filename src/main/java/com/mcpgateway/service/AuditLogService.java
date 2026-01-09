package com.mcpgateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.AuditLog;
import com.mcpgateway.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Log Service
 *
 * Handles audit logging for critical operations:
 * - Asynchronous logging to avoid impacting request performance
 * - Separate transaction to ensure logs are saved even if main transaction rolls back
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log an audit event asynchronously
     *
     * @param userId User performing the action
     * @param username Username
     * @param action Action performed
     * @param resourceType Type of resource
     * @param resourceId Resource ID
     * @param status Operation status
     * @param metadata Additional context
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(
            UUID userId,
            String username,
            String action,
            String resourceType,
            String resourceId,
            AuditLog.Status status,
            Map<String, Object> metadata
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .status(status)
                    .timestamp(LocalDateTime.now())
                    .build();

            if (metadata != null && !metadata.isEmpty()) {
                auditLog.setMetadata(objectMapper.writeValueAsString(metadata));
            }

            auditLogRepository.save(auditLog);
            log.debug("Audit log created: {} by user {} on {} {}", action, username, resourceType, resourceId);

        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }

    /**
     * Log with full HTTP context
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWithHttpContext(
            UUID userId,
            String username,
            String action,
            String resourceType,
            String resourceId,
            String httpMethod,
            String endpoint,
            String ipAddress,
            String userAgent,
            AuditLog.Status status,
            String errorMessage,
            Long executionTimeMs,
            Map<String, Object> metadata
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .httpMethod(httpMethod)
                    .endpoint(endpoint)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status(status)
                    .errorMessage(errorMessage)
                    .executionTimeMs(executionTimeMs)
                    .timestamp(LocalDateTime.now())
                    .build();

            if (metadata != null && !metadata.isEmpty()) {
                auditLog.setMetadata(objectMapper.writeValueAsString(metadata));
            }

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            log.error("Failed to create audit log with HTTP context", e);
        }
    }

    /**
     * Log payment operations (critical for compliance)
     */
    public void logPaymentOperation(
            UUID userId,
            String username,
            String action,
            String paymentId,
            String amount,
            String currency,
            AuditLog.Status status
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("amount", amount);
        metadata.put("currency", currency);

        logAsync(userId, username, action, "Payment", paymentId, status, metadata);
    }

    /**
     * Log user management operations
     */
    public void logUserManagement(
            UUID adminId,
            String adminUsername,
            String action,
            UUID targetUserId,
            AuditLog.Status status,
            Map<String, Object> changes
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("adminId", adminId.toString());
        metadata.put("targetUserId", targetUserId.toString());
        if (changes != null) {
            metadata.putAll(changes);
        }

        logAsync(adminId, adminUsername, action, "User", targetUserId.toString(), status, metadata);
    }

    /**
     * Log API key operations
     */
    public void logApiKeyOperation(
            UUID userId,
            String username,
            String action,
            String apiKeyId,
            AuditLog.Status status
    ) {
        logAsync(userId, username, action, "ApiKey", apiKeyId, status, null);
    }

    /**
     * Query audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByResource(String resourceType, String resourceId, Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceIdOrderByTimestampDesc(
                resourceType, resourceId, pageable
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getSecurityEvents(int hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        return auditLogRepository.findSecurityEvents(since);
    }

    /**
     * Get user activity count (for rate limiting audit)
     */
    @Transactional(readOnly = true)
    public long getUserActivityCount(UUID userId, int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        return auditLogRepository.countByUserIdSince(userId, since);
    }
}
