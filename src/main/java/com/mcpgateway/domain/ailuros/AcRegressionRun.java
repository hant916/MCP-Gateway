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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "ac_regression_run",
    indexes = {
        @Index(name = "idx_regression_run_app_env_created", columnList = "app_id, env, created_ts"),
        @Index(name = "idx_regression_run_status_created", columnList = "status, created_ts")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcRegressionRun {

    public enum Status {
        PENDING,
        RUNNING,
        PASS,
        FAIL,
        ERROR
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

    @Column(name = "baseline_model", length = 128)
    private String baselineModel;

    @Column(name = "candidate_model", length = 128)
    private String candidateModel;

    @Column(name = "baseline_prompt_version", length = 128)
    private String baselinePromptVersion;

    @Column(name = "candidate_prompt_version", length = 128)
    private String candidatePromptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "started_ts")
    private Instant startedTs;

    @Column(name = "ended_ts")
    private Instant endedTs;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "report_uri", length = 512)
    private String reportUri;

    @Column(name = "release_blocked", nullable = false)
    @Builder.Default
    private Boolean releaseBlocked = Boolean.FALSE;

    @Column(name = "created_ts", nullable = false, updatable = false)
    private Instant createdTs;

    @PrePersist
    protected void onCreate() {
        if (createdTs == null) {
            createdTs = Instant.now();
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (releaseBlocked == null) {
            releaseBlocked = Boolean.FALSE;
        }
    }
}
