package com.mcpgateway.domain.ailuros;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ailuros Control: LLM Call Audit Log
 * Comprehensive tracking of all LLM API calls with full request/response context
 */
@Entity
@Table(
    name = "ac_call",
    indexes = {
        @Index(name = "idx_call_project_time", columnList = "project_key, created_at"),
        @Index(name = "idx_call_model_time", columnList = "model, created_at"),
        @Index(name = "idx_call_prompt_ref", columnList = "prompt_ref, created_at"),
        @Index(name = "idx_call_status", columnList = "status, created_at"),
        @Index(name = "idx_call_trace", columnList = "trace_id"),
        @Index(name = "idx_call_env", columnList = "env, created_at"),
        @Index(name = "idx_call_provider", columnList = "provider, created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trace_id", nullable = false, unique = true, length = 64)
    private String traceId;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "env", nullable = false, length = 16)
    @Builder.Default
    private String env = "prod";

    @Column(name = "status", nullable = false, length = 16)
    private String status; // ok, error, timeout, cancelled

    @Column(name = "provider", nullable = false, length = 32)
    private String provider; // openai, anthropic, azure_openai, etc.

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "temperature", precision = 4, scale = 3)
    private BigDecimal temperature;

    @Column(name = "top_p", precision = 4, scale = 3)
    private BigDecimal topP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_template_id", foreignKey = @ForeignKey(name = "fk_call_prompt_template"))
    private AcPromptTemplate promptTemplate;

    @Column(name = "prompt_ref", length = 160)
    private String promptRef; // name@version or adhoc@sha

    @Column(name = "request_text", columnDefinition = "TEXT")
    private String requestText;

    @Column(name = "request_sha256", length = 64)
    private String requestSha256;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "response_sha256", length = 64)
    private String responseSha256;

    @Column(name = "tokens_prompt")
    private Integer tokensPrompt;

    @Column(name = "tokens_completion")
    private Integer tokensCompletion;

    @Column(name = "tokens_total")
    private Integer tokensTotal;

    @Column(name = "cost_estimate_usd", precision = 12, scale = 6)
    private BigDecimal costEstimateUsd;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "upstream_request_id", length = 128)
    private String upstreamRequestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Check if this call had an error
     */
    public boolean isError() {
        return "error".equalsIgnoreCase(status);
    }

    /**
     * Check if this call was successful
     */
    public boolean isSuccess() {
        return "ok".equalsIgnoreCase(status);
    }

    /**
     * Get reliability score (1.0 for success, 0.0 for error)
     */
    public double getReliabilityScore() {
        return isSuccess() ? 1.0 : 0.0;
    }
}
