package com.mcpgateway.domain.ailuros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "ac_budget_eval",
    indexes = {
        @Index(name = "idx_budget_eval_app_env_created", columnList = "app_id, env, created_ts")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcBudgetEval {

    public enum Status {
        OK,
        EXCEEDED,
        FORECAST_EXCEEDED
    }

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "env", nullable = false, length = 16)
    private String env;

    @Column(name = "route", length = 255)
    private String route;

    @Column(name = "window_start_ts", nullable = false)
    private Instant windowStartTs;

    @Column(name = "window_end_ts", nullable = false)
    private Instant windowEndTs;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "limit_usd", precision = 12, scale = 6)
    private BigDecimal limitUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    @Column(name = "forecast_monthly_usd", precision = 12, scale = 6)
    private BigDecimal forecastMonthlyUsd;

    @Column(name = "created_ts", nullable = false, updatable = false)
    private Instant createdTs;

    @PrePersist
    protected void onCreate() {
        if (createdTs == null) {
            createdTs = Instant.now();
        }
        if (status == null) {
            status = Status.OK;
        }
        if (costUsd == null) {
            costUsd = BigDecimal.ZERO;
        }
    }
}
