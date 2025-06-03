package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "payment_transactions")
@EqualsAndHashCode(of = "id")
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private ToolSubscription subscription;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "transaction_time")
    private LocalDateTime transactionTime;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
    
    @PrePersist
    protected void onCreate() {
        if (transactionTime == null) {
            transactionTime = LocalDateTime.now();
        }
    }
} 