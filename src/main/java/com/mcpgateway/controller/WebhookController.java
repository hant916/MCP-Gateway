package com.mcpgateway.controller;

import com.mcpgateway.domain.User;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.dto.webhook.CreateWebhookRequest;
import com.mcpgateway.dto.webhook.WebhookDTO;
import com.mcpgateway.ratelimit.RateLimit;
import com.mcpgateway.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for webhook management
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Webhook management APIs")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping
    @Operation(summary = "Get all webhooks for current user")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<WebhookDTO>> getUserWebhooks(
            @AuthenticationPrincipal User user) {

        List<WebhookConfig> webhooks = webhookService.getUserWebhooks(user.getId());
        List<WebhookDTO> dtos = webhooks.stream()
                .map(WebhookDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Operation(summary = "Create a new webhook")
    @RateLimit(limit = 20, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<WebhookDTO> createWebhook(
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal User user) {

        WebhookConfig config = WebhookConfig.builder()
                .userId(user.getId())
                .url(request.getUrl())
                .events(String.join(",", request.getEvents()))
                .description(request.getDescription())
                .retryCount(request.getRetryCount())
                .timeoutSeconds(request.getTimeoutSeconds())
                .build();

        WebhookConfig created = webhookService.createWebhook(config);
        return ResponseEntity.ok(WebhookDTO.from(created));
    }

    @PutMapping("/{webhookId}")
    @Operation(summary = "Update a webhook")
    @RateLimit(limit = 50, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<WebhookDTO> updateWebhook(
            @PathVariable UUID webhookId,
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal User user) {

        WebhookConfig updates = WebhookConfig.builder()
                .url(request.getUrl())
                .events(request.getEvents() != null ? String.join(",", request.getEvents()) : null)
                .description(request.getDescription())
                .retryCount(request.getRetryCount())
                .timeoutSeconds(request.getTimeoutSeconds())
                .build();

        WebhookConfig updated = webhookService.updateWebhook(webhookId, updates);
        return ResponseEntity.ok(WebhookDTO.from(updated));
    }

    @DeleteMapping("/{webhookId}")
    @Operation(summary = "Delete a webhook")
    @RateLimit(limit = 50, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal User user) {

        webhookService.deleteWebhook(webhookId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{webhookId}/reactivate")
    @Operation(summary = "Reactivate a suspended webhook")
    @RateLimit(limit = 20, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<WebhookDTO> reactivateWebhook(
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal User user) {

        webhookService.reactivateWebhook(webhookId);
        // Fetch and return the updated webhook
        List<WebhookConfig> webhooks = webhookService.getUserWebhooks(user.getId());
        WebhookConfig reactivated = webhooks.stream()
                .filter(w -> w.getId().equals(webhookId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Webhook not found"));

        return ResponseEntity.ok(WebhookDTO.from(reactivated));
    }

    @GetMapping("/{webhookId}/logs")
    @Operation(summary = "Get webhook delivery logs")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<WebhookLog>> getWebhookLogs(
            @PathVariable UUID webhookId,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal User user) {

        List<WebhookLog> logs = webhookService.getWebhookLogs(webhookId, limit);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/events")
    @Operation(summary = "Get available webhook event types")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<String>> getAvailableEvents() {
        List<String> events = List.of(
                WebhookConfig.EVENT_PAYMENT_SUCCESS,
                WebhookConfig.EVENT_PAYMENT_FAILURE,
                WebhookConfig.EVENT_SUBSCRIPTION_CREATED,
                WebhookConfig.EVENT_SUBSCRIPTION_CANCELLED,
                WebhookConfig.EVENT_QUOTA_EXCEEDED,
                WebhookConfig.EVENT_TOOL_EXECUTED,
                WebhookConfig.EVENT_SESSION_STARTED,
                WebhookConfig.EVENT_SESSION_ENDED
        );

        return ResponseEntity.ok(events);
    }
}
