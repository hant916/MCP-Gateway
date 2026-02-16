package com.mcpgateway.domain.ailuros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "ac_release_baseline",
    indexes = {
        @Index(name = "idx_release_baseline_app_env_route", columnList = "app_id, env, route")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcReleaseBaseline {

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

    @Column(name = "baseline_prompt_version", length = 128)
    private String baselinePromptVersion;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = Boolean.TRUE;

    @Column(name = "created_ts", nullable = false, updatable = false)
    private Instant createdTs;

    @Column(name = "updated_ts", nullable = false)
    private Instant updatedTs;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdTs == null) {
            createdTs = now;
        }
        if (updatedTs == null) {
            updatedTs = now;
        }
        if (isEnabled == null) {
            isEnabled = Boolean.TRUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTs = Instant.now();
    }
}
