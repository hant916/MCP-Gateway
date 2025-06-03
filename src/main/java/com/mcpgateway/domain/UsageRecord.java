package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "usage_records", indexes = {
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_api_endpoint", columnList = "api_endpoint"),
    @Index(name = "idx_status_code", columnList = "status_code")
})
public class UsageRecord {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "timestamp", nullable = false)
    private Timestamp timestamp;

    @Column(name = "api_endpoint", nullable = false, length = 255)
    private String apiEndpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "request_size")
    private Long requestSize;

    @Column(name = "response_size")
    private Long responseSize;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "cost_amount", precision = 10, scale = 4)
    private BigDecimal costAmount;

    @Column(name = "message_type", length = 50)
    private String messageType; // JSON-RPC method or message type

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = new Timestamp(System.currentTimeMillis());
        }
    }

    // 计费状态枚举
    public enum BillingStatus {
        SUCCESS,    // 成功调用
        FAILED,     // 失败调用
        TIMEOUT,    // 超时
        RATE_LIMITED // 被限流
    }
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status")
    private BillingStatus billingStatus;

    // 便捷构造方法
    public UsageRecord(UUID sessionId, UUID userId, String apiEndpoint, 
                      String httpMethod, Integer statusCode) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.apiEndpoint = apiEndpoint;
        this.httpMethod = httpMethod;
        this.statusCode = statusCode;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        
        // 根据状态码设置计费状态
        if (statusCode >= 200 && statusCode < 300) {
            this.billingStatus = BillingStatus.SUCCESS;
        } else if (statusCode == 408 || statusCode == 504) {
            this.billingStatus = BillingStatus.TIMEOUT;
        } else if (statusCode == 429) {
            this.billingStatus = BillingStatus.RATE_LIMITED;
        } else {
            this.billingStatus = BillingStatus.FAILED;
        }
    }
} 