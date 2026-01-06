package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for webhook configuration
 */
@Entity
@Table(name = "webhook_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "secret", nullable = false)
    private String secret; // For HMAC signature verification

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WebhookStatus status;

    @Column(name = "events", length = 1000)
    private String events; // Comma-separated list of events to subscribe to

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "retry_count")
    private Integer retryCount = 3;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 30;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @Column(name = "failure_count")
    private Integer failureCount = 0;

    @Column(name = "success_count")
    private Integer successCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum WebhookStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED, // Suspended due to too many failures
        DELETED
    }

    // Event types that can trigger webhooks
    public static final String EVENT_PAYMENT_SUCCESS = "payment.success";
    public static final String EVENT_PAYMENT_FAILURE = "payment.failure";
    public static final String EVENT_SUBSCRIPTION_CREATED = "subscription.created";
    public static final String EVENT_SUBSCRIPTION_CANCELLED = "subscription.cancelled";
    public static final String EVENT_QUOTA_EXCEEDED = "quota.exceeded";
    public static final String EVENT_TOOL_EXECUTED = "tool.executed";
    public static final String EVENT_SESSION_STARTED = "session.started";
    public static final String EVENT_SESSION_ENDED = "session.ended";
}
