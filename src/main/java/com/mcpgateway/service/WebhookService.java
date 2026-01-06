package com.mcpgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.metrics.McpGatewayMetrics;
import com.mcpgateway.repository.WebhookConfigRepository;
import com.mcpgateway.repository.WebhookLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for webhook delivery and management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final McpGatewayMetrics metrics;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Trigger webhooks for a specific event
     */
    @Async
    public void triggerWebhooks(String eventType, Map<String, Object> payload) {
        List<WebhookConfig> webhooks = webhookConfigRepository.findActiveWebhooksByEvent(eventType);

        log.info("Triggering {} webhooks for event: {}", webhooks.size(), eventType);

        for (WebhookConfig webhook : webhooks) {
            deliverWebhook(webhook, eventType, payload);
        }
    }

    /**
     * Deliver a webhook to a specific endpoint
     */
    @Transactional
    public void deliverWebhook(WebhookConfig webhook, String eventType, Map<String, Object> payload) {
        long startTime = System.currentTimeMillis();
        WebhookLog logEntry = WebhookLog.builder()
                .webhookConfig(webhook)
                .eventType(eventType)
                .status(WebhookLog.DeliveryStatus.PENDING)
                .retryCount(0)
                .build();

        try {
            // Prepare payload
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("event", eventType);
            webhookPayload.put("timestamp", System.currentTimeMillis());
            webhookPayload.put("data", payload);

            String payloadJson = objectMapper.writeValueAsString(webhookPayload);
            logEntry.setPayload(payloadJson);

            // Generate HMAC signature
            String signature = generateHmacSignature(payloadJson, webhook.getSecret());

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", signature);
            headers.set("X-Webhook-Event", eventType);
            headers.set("User-Agent", "MCP-Gateway-Webhook/1.0");

            HttpEntity<String> requestEntity = new HttpEntity<>(payloadJson, headers);

            // Send webhook
            ResponseEntity<String> response = restTemplate.exchange(
                    webhook.getUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            long duration = System.currentTimeMillis() - startTime;

            // Log success
            logEntry.setHttpStatusCode(response.getStatusCode().value());
            logEntry.setResponseBody(response.getBody());
            logEntry.setStatus(WebhookLog.DeliveryStatus.SUCCESS);
            logEntry.setDurationMs(duration);

            // Update webhook stats
            webhook.setLastTriggeredAt(LocalDateTime.now());
            webhook.setSuccessCount(webhook.getSuccessCount() + 1);
            webhook.setFailureCount(0); // Reset failure count on success
            webhookConfigRepository.save(webhook);

            // Record metrics
            metrics.recordWebhookDelivery(eventType, true, duration);

            log.info("Webhook delivered successfully to {} for event {}", webhook.getUrl(), eventType);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // Log failure
            logEntry.setStatus(WebhookLog.DeliveryStatus.FAILURE);
            logEntry.setErrorMessage(e.getMessage());
            logEntry.setDurationMs(duration);

            // Update webhook stats
            webhook.setFailureCount(webhook.getFailureCount() + 1);

            // Suspend webhook if too many failures
            if (webhook.getFailureCount() >= 10) {
                webhook.setStatus(WebhookConfig.WebhookStatus.SUSPENDED);
                log.warn("Webhook {} suspended due to {} consecutive failures", webhook.getId(), webhook.getFailureCount());
            }

            webhookConfigRepository.save(webhook);

            // Record metrics
            metrics.recordWebhookDelivery(eventType, false, duration);

            log.error("Failed to deliver webhook to {} for event {}: {}", webhook.getUrl(), eventType, e.getMessage());

            // Retry if configured
            if (logEntry.getRetryCount() < webhook.getRetryCount()) {
                scheduleRetry(webhook, eventType, payload, logEntry.getRetryCount() + 1);
            }
        } finally {
            webhookLogRepository.save(logEntry);
        }
    }

    /**
     * Schedule a retry for failed webhook delivery
     */
    @Async
    public void scheduleRetry(WebhookConfig webhook, String eventType, Map<String, Object> payload, int retryCount) {
        try {
            // Exponential backoff: 2^retryCount seconds
            long delaySeconds = (long) Math.pow(2, retryCount);
            Thread.sleep(delaySeconds * 1000);

            log.info("Retrying webhook delivery (attempt {}/{}) for event {}",
                     retryCount, webhook.getRetryCount(), eventType);

            deliverWebhook(webhook, eventType, payload);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Webhook retry interrupted", e);
        }
    }

    /**
     * Generate HMAC signature for webhook payload
     */
    private String generateHmacSignature(String payload, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            String expectedSignature = generateHmacSignature(payload, secret);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    /**
     * Create a new webhook configuration
     */
    @Transactional
    public WebhookConfig createWebhook(WebhookConfig config) {
        // Generate secret if not provided
        if (config.getSecret() == null || config.getSecret().isEmpty()) {
            config.setSecret(UUID.randomUUID().toString());
        }

        // Set defaults
        if (config.getRetryCount() == null) {
            config.setRetryCount(3);
        }
        if (config.getTimeoutSeconds() == null) {
            config.setTimeoutSeconds(30);
        }
        if (config.getStatus() == null) {
            config.setStatus(WebhookConfig.WebhookStatus.ACTIVE);
        }
        if (config.getIsActive() == null) {
            config.setIsActive(true);
        }

        return webhookConfigRepository.save(config);
    }

    /**
     * Update webhook configuration
     */
    @Transactional
    public WebhookConfig updateWebhook(UUID webhookId, WebhookConfig updates) {
        WebhookConfig existing = webhookConfigRepository.findById(webhookId)
                .orElseThrow(() -> new RuntimeException("Webhook not found: " + webhookId));

        if (updates.getUrl() != null) {
            existing.setUrl(updates.getUrl());
        }
        if (updates.getEvents() != null) {
            existing.setEvents(updates.getEvents());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getIsActive() != null) {
            existing.setIsActive(updates.getIsActive());
        }
        if (updates.getRetryCount() != null) {
            existing.setRetryCount(updates.getRetryCount());
        }
        if (updates.getTimeoutSeconds() != null) {
            existing.setTimeoutSeconds(updates.getTimeoutSeconds());
        }

        return webhookConfigRepository.save(existing);
    }

    /**
     * Delete webhook configuration
     */
    @Transactional
    public void deleteWebhook(UUID webhookId) {
        WebhookConfig webhook = webhookConfigRepository.findById(webhookId)
                .orElseThrow(() -> new RuntimeException("Webhook not found: " + webhookId));

        webhook.setStatus(WebhookConfig.WebhookStatus.DELETED);
        webhook.setIsActive(false);
        webhookConfigRepository.save(webhook);
    }

    /**
     * Get webhook logs
     */
    @Transactional(readOnly = true)
    public List<WebhookLog> getWebhookLogs(UUID webhookId, int limit) {
        return webhookLogRepository.findByWebhookConfigId(
                webhookId,
                org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * Get all webhooks for a user
     */
    @Transactional(readOnly = true)
    public List<WebhookConfig> getUserWebhooks(UUID userId) {
        return webhookConfigRepository.findByUserId(userId);
    }

    /**
     * Reactivate a suspended webhook
     */
    @Transactional
    public void reactivateWebhook(UUID webhookId) {
        WebhookConfig webhook = webhookConfigRepository.findById(webhookId)
                .orElseThrow(() -> new RuntimeException("Webhook not found: " + webhookId));

        webhook.setStatus(WebhookConfig.WebhookStatus.ACTIVE);
        webhook.setFailureCount(0);
        webhook.setIsActive(true);

        webhookConfigRepository.save(webhook);

        log.info("Webhook {} reactivated", webhookId);
    }
}
