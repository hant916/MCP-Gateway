package com.mcpgateway.domain.ailuros;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Ailuros Control: Prompt Template Entity
 * Stores versioned prompt templates for traceability and drift detection
 */
@Entity
@Table(
    name = "ac_prompt_template",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_prompt_template_version",
        columnNames = {"project_key", "name", "version"}
    ),
    indexes = {
        @Index(name = "idx_prompt_template_sha", columnList = "content_sha256"),
        @Index(name = "idx_prompt_template_project", columnList = "project_key, created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Generate a reference string for this template
     * Format: name@version
     */
    public String getReference() {
        return String.format("%s@%d", name, version);
    }
}
