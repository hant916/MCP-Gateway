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
    
    public enum SubscriptionStatus {
        ACTIVE, EXPIRED, CANCELLED
    }
} 