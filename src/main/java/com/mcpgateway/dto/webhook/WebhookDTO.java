package com.mcpgateway.dto.webhook;

import com.mcpgateway.domain.WebhookConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * DTO for webhook configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDTO {

    private UUID id;
    private String url;
    private WebhookConfig.WebhookStatus status;
    private List<String> events;
    private String description;
    private Boolean isActive;
    private Integer retryCount;
    private Integer timeoutSeconds;
    private LocalDateTime lastTriggeredAt;
    private Integer failureCount;
    private Integer successCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert from WebhookConfig entity (without exposing secret)
     */
    public static WebhookDTO from(WebhookConfig config) {
        return WebhookDTO.builder()
                .id(config.getId())
                .url(config.getUrl())
                .status(config.getStatus())
                .events(config.getEvents() != null ?
                        Arrays.asList(config.getEvents().split(",")) : null)
                .description(config.getDescription())
                .isActive(config.getIsActive())
                .retryCount(config.getRetryCount())
                .timeoutSeconds(config.getTimeoutSeconds())
                .lastTriggeredAt(config.getLastTriggeredAt())
                .failureCount(config.getFailureCount())
                .successCount(config.getSuccessCount())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
