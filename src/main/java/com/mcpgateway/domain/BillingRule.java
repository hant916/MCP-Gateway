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
@Table(name = "billing_rules", indexes = {
    @Index(name = "idx_api_pattern", columnList = "api_pattern"),
    @Index(name = "idx_is_active", columnList = "is_active"),
    @Index(name = "idx_rule_type", columnList = "rule_type")
})
public class BillingRule {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "api_pattern", nullable = false, length = 255)
    private String apiPattern; // 支持通配符，如 "/api/v1/sse/*"

    @Column(name = "http_method", length = 10)
    private String httpMethod; // GET, POST, PUT, DELETE, null表示所有方法

    @Column(name = "cost_per_call", precision = 10, scale = 4, nullable = false)
    private BigDecimal costPerCall;

    @Column(name = "cost_per_kb", precision = 10, scale = 6)
    private BigDecimal costPerKb; // 按数据量计费

    @Column(name = "cost_per_second", precision = 10, scale = 6)
    private BigDecimal costPerSecond; // 按处理时间计费

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0; // 优先级，数字越大优先级越高

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    // 计费类型枚举
    public enum RuleType {
        PER_CALL,       // 按调用次数
        PER_DATA,       // 按数据量
        PER_TIME,       // 按处理时间
        COMBINED        // 组合计费
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType = RuleType.PER_CALL;

    // 只对成功调用计费还是所有调用都计费
    @Column(name = "bill_failed_calls")
    private Boolean billFailedCalls = false;

    // 最小费用
    @Column(name = "minimum_cost", precision = 10, scale = 4)
    private BigDecimal minimumCost;

    // 最大费用
    @Column(name = "maximum_cost", precision = 10, scale = 4)
    private BigDecimal maximumCost;

    @PrePersist
    protected void onCreate() {
        createdAt = new Timestamp(System.currentTimeMillis());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    // 便捷构造方法
    public BillingRule(String ruleName, String apiPattern, BigDecimal costPerCall) {
        this.ruleName = ruleName;
        this.apiPattern = apiPattern;
        this.costPerCall = costPerCall;
        this.ruleType = RuleType.PER_CALL;
        this.isActive = true;
        this.priority = 0;
    }

    // 检查API路径是否匹配
    public boolean matches(String apiPath, String method) {
        // 检查HTTP方法
        if (this.httpMethod != null && !this.httpMethod.equalsIgnoreCase(method)) {
            return false;
        }

        // 简单的通配符匹配
        String pattern = this.apiPattern;
        if (pattern.endsWith("*")) {
            pattern = pattern.substring(0, pattern.length() - 1);
            return apiPath.startsWith(pattern);
        } else {
            return apiPath.equals(pattern);
        }
    }

    // 计算费用
    public BigDecimal calculateCost(Long requestSize, Long responseSize, Integer processingMs) {
        BigDecimal cost = BigDecimal.ZERO;

        switch (ruleType) {
            case PER_CALL:
                cost = costPerCall != null ? costPerCall : BigDecimal.ZERO;
                break;

            case PER_DATA:
                if (costPerKb != null && requestSize != null && responseSize != null) {
                    BigDecimal totalBytes = new BigDecimal(requestSize + responseSize);
                    BigDecimal totalKb = totalBytes.divide(new BigDecimal(1024), 6, BigDecimal.ROUND_HALF_UP);
                    cost = totalKb.multiply(costPerKb);
                }
                break;

            case PER_TIME:
                if (costPerSecond != null && processingMs != null) {
                    BigDecimal seconds = new BigDecimal(processingMs).divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
                    cost = seconds.multiply(costPerSecond);
                }
                break;

            case COMBINED:
                BigDecimal callCost = costPerCall != null ? costPerCall : BigDecimal.ZERO;
                BigDecimal dataCost = BigDecimal.ZERO;
                BigDecimal timeCost = BigDecimal.ZERO;

                if (costPerKb != null && requestSize != null && responseSize != null) {
                    BigDecimal totalBytes = new BigDecimal(requestSize + responseSize);
                    BigDecimal totalKb = totalBytes.divide(new BigDecimal(1024), 6, BigDecimal.ROUND_HALF_UP);
                    dataCost = totalKb.multiply(costPerKb);
                }

                if (costPerSecond != null && processingMs != null) {
                    BigDecimal seconds = new BigDecimal(processingMs).divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
                    timeCost = seconds.multiply(costPerSecond);
                }

                cost = callCost.add(dataCost).add(timeCost);
                break;
        }

        // 应用最小和最大费用限制
        if (minimumCost != null && cost.compareTo(minimumCost) < 0) {
            cost = minimumCost;
        }
        if (maximumCost != null && cost.compareTo(maximumCost) > 0) {
            cost = maximumCost;
        }

        return cost;
    }
} 