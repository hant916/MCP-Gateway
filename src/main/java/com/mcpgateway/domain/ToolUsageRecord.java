package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "tool_usage_records")
@EqualsAndHashCode(of = "id")
public class ToolUsageRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private ToolSubscription subscription;
    
    @Column(name = "usage_time")
    private LocalDateTime usageTime;
    
    @Column(name = "resource_consumption")
    private Long resourceConsumption;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal cost;
    
    @PrePersist
    protected void onCreate() {
        if (usageTime == null) {
            usageTime = LocalDateTime.now();
        }
    }
} 