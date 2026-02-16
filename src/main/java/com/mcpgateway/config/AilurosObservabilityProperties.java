package com.mcpgateway.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Observability config for gateway -> AILUROS event emission.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ailuros.observability")
public class AilurosObservabilityProperties {

    /**
     * Master switch for event emission.
     */
    private boolean enabled = true;

    /**
     * Ingest endpoint for call_event v1.
     */
    @NotBlank
    private String ingestUrl = "http://localhost:8080/api/ailuros/ingest";

    /**
     * Sampling rate [0,1].
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double sampleRate = 1.0d;

    /**
     * Max number of pending events in memory.
     */
    @Min(1)
    private int maxQueue = 5000;

    /**
     * Timeout for emitting one event.
     */
    @Min(50)
    private int emitTimeoutMs = 300;
}
