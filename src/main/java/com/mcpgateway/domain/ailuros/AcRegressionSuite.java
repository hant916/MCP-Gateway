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
    name = "ac_regression_suite",
    indexes = {
        @Index(name = "idx_regression_suite_app_env_route", columnList = "app_id, env, route")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcRegressionSuite {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "env", nullable = false, length = 16)
    private String env;

    @Column(name = "route", length = 255)
    private String route;

    @Column(name = "suite_version", nullable = false, length = 128)
    private String suiteVersion;

    @Column(name = "storage_uri", length = 512)
    private String storageUri;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = Boolean.TRUE;

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
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTs = Instant.now();
    }
}
