package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for call detail view
 * Includes full request/response text and all metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallDetailDTO {
    private UUID id;
    private String traceId;
    private String projectKey;
    private String env;
    private String status;
    private String provider;
    private String model;
    private BigDecimal temperature;
    private BigDecimal topP;
    private String promptRef;

    // Full text content
    private String requestText;
    private String requestSha256;
    private String responseText;
    private String responseSha256;

    // Token usage
    private Integer tokensPrompt;
    private Integer tokensCompletion;
    private Integer tokensTotal;

    // Cost and performance
    private BigDecimal costEstimateUsd;
    private Integer latencyMs;

    // Metadata
    private String upstreamRequestId;
    private Instant createdAt;

    // Flags
    private List<FlagDTO> flags;
}
