package com.mcpgateway.dto.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * Request DTO for creating a webhook
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookRequest {

    @NotBlank(message = "URL is required")
    @URL(message = "Invalid URL format")
    private String url;

    @NotEmpty(message = "At least one event is required")
    private List<String> events;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Integer retryCount;

    private Integer timeoutSeconds;
}
