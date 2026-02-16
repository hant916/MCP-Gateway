package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "tool_subscriptions")
@EqualsAndHashCode(of = "id")
public class ToolSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id")
    private McpTool tool;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;
    
    @Column(name = "start_date")
    private LocalDateTime startDate;
    
    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;
    
    @Column(name = "remaining_quota")
    private Integer remainingQuota;

    @Column(name = "monthly_quota")
    private Integer monthlyQuota;

    @Column(name = "quota_reset_at")
    private LocalDateTime quotaResetAt;

    public enum SubscriptionStatus {
        ACTIVE, EXPIRED, CANCELLED, INACTIVE, TRIAL
    }

    /**
     * Helper method to set client by UUID
     */
    public void setClientId(UUID userId) {
        if (userId != null) {
            User user = new User();
            user.setId(userId);
            this.client = user;
        }
    }

    /**
     * Helper method to get client ID directly
     */
    public UUID getClientId() {
        return client != null ? client.getId() : null;
    }

    /**
     * Helper method to get User entity
     */
    public User getUser() {
        return this.client;
    }
} 
