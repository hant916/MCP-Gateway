package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit Log Entity
 *
 * Records critical system operations for:
 * - Compliance (SOC 2, GDPR, etc.)
 * - Security investigation
 * - User activity tracking
 * - Debugging and troubleshooting
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_resource", columnList = "resource_type,resource_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * User who performed the action
     */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username")
    private String username;

    /**
     * Action performed (PAYMENT_CREATED, USER_DELETED, etc.)
     */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    /**
     * Resource being acted upon
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    /**
     * Request details
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "endpoint", length = 500)
    private String endpoint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Result of the action
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Additional context (JSON format)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Timestamp
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * Execution time in milliseconds
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    public enum Status {
        SUCCESS,
        FAILURE,
        PARTIAL_SUCCESS,
        UNAUTHORIZED,
        FORBIDDEN
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
