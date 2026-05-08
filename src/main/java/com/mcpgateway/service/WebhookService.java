package com.mcpgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.metrics.McpGatewayMetrics;
import com.mcpgateway.repository.WebhookConfigRepository;
import com.mcpgateway.repository.WebhookLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for webhook delivery and management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;
    private static final String DEFAULT_CLAIMED_BY = "unknown-instance";

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final McpGatewayMetrics metrics;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;
    @Value("${webhook.retry.batch-size:100}")
    private int retryBatchSize;
    @Value("${webhook.retry.claimed-by:${HOSTNAME:${COMPUTERNAME:unknown-instance}}}")
    private String retryClaimedBy = DEFAULT_CLAIMED_BY;

    /**
     * Trigger webhooks for a specific event.
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
     * Backward-compatible wrapper used by event listeners/tests.
     */
    @Async
    public void sendWebhook(UUID userId, String eventType, Map<String, Object> payload) {
        Map<String, Object> enrichedPayload = new HashMap<>();
        if (payload != null) {
            enrichedPayload.putAll(payload);
        }
        if (userId != null) {
            enrichedPayload.putIfAbsent("user_id", userId.toString());
        }
        triggerWebhooks(eventType, enrichedPayload);
    }

    /**
     * Deliver a webhook to a specific endpoint.
     */
    @Transactional
    public void deliverWebhook(WebhookConfig webhook, String eventType, Map<String, Object> payload) {
        Map<String, Object> webhookPayload = new HashMap<>();
        webhookPayload.put("event", eventType);
        webhookPayload.put("timestamp", System.currentTimeMillis());
        webhookPayload.put("data", payload);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(webhookPayload);
        } catch (Exception ex) {
            log.error("Failed to serialize webhook payload for event {}", eventType, ex);
            return;
        }

        WebhookLog logEntry = WebhookLog.builder()
                .webhookConfig(webhook)
                .eventType(eventType)
                .payload(payloadJson)
                .status(WebhookLog.DeliveryStatus.PENDING)
                .retryCount(0)
                .attemptCount(0)
                .build();
        logEntry = webhookLogRepository.save(logEntry);

        attemptDelivery(logEntry, payloadJson);
    }

    /**
     * Poll due retry records and deliver them without recursion/sleep.
     */
    @Scheduled(fixedDelayString = "${webhook.retry.poll-interval-ms:5000}")
    @Transactional
    public void processPendingRetries() {
        LocalDateTime now = LocalDateTime.now();
        String claimedBy = resolvedClaimedBy();
        int batchSize = Math.max(retryBatchSize, 1);
        List<WebhookLog> dueRetries = webhookLogRepository
                .findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        WebhookLog.DeliveryStatus.RETRYING,
                        now,
                        PageRequest.of(0, batchSize)
                )
                .getContent();

        for (WebhookLog retryLog : dueRetries) {
            int claimedRows = webhookLogRepository.claimDueRetry(
                    retryLog.getId(),
                    WebhookLog.DeliveryStatus.RETRYING,
                    WebhookLog.DeliveryStatus.IN_PROGRESS,
                    now,
                    claimedBy,
                    now
            );
            if (claimedRows == 0) {
                continue;
            }
            metrics.recordWebhookRetryClaimed(claimedBy);

            WebhookLog claimedLog = webhookLogRepository.findById(retryLog.getId()).orElse(null);
            if (claimedLog == null) {
                continue;
            }

            WebhookConfig webhook = claimedLog.getWebhookConfig();
            if (webhook == null ||
                    webhook.getStatus() != WebhookConfig.WebhookStatus.ACTIVE ||
                    !Boolean.TRUE.equals(webhook.getIsActive())) {
                String errorMessage = "Webhook inactive; retry cancelled";
                claimedLog.setStatus(WebhookLog.DeliveryStatus.FAILURE);
                claimedLog.setErrorMessage(errorMessage);
                claimedLog.setLastErrorCode("WEBHOOK_INACTIVE");
                claimedLog.setLastErrorMessage(errorMessage);
                claimedLog.setNextRetryAt(null);
                webhookLogRepository.save(claimedLog);
                continue;
            }

            attemptDelivery(claimedLog, claimedLog.getPayload());
        }
    }

    private void attemptDelivery(WebhookLog logEntry, String payloadJson) {
        WebhookConfig webhook = logEntry.getWebhookConfig();
        String requestPayload = payloadJson != null ? payloadJson : "{}";
        String eventType = logEntry.getEventType();
        long startTime = System.currentTimeMillis();
        logEntry.setStatus(WebhookLog.DeliveryStatus.IN_PROGRESS);

        try {
            String signature = generateHmacSignature(requestPayload, webhook.getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", signature);
            headers.set("X-Webhook-Event", eventType);
            headers.set("User-Agent", "MCP-Gateway-Webhook/1.0");

            HttpEntity<String> requestEntity = new HttpEntity<>(requestPayload, headers);
            ResponseEntity<String> response = createRestTemplate(webhook).exchange(
                    webhook.getUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            long duration = System.currentTimeMillis() - startTime;
            markSuccess(webhook, logEntry, response, duration);
            metrics.recordWebhookDelivery(eventType, true, duration);
            metrics.recordWebhookDeliverySuccess(eventType);
            metrics.recordWebhookDeliveryLatency(eventType, duration);

            log.info("Webhook delivered successfully to {} for event {}", webhook.getUrl(), eventType);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            String errorCode = markFailure(webhook, logEntry, ex, duration);
            metrics.recordWebhookDelivery(eventType, false, duration);
            metrics.recordWebhookDeliveryFailure(eventType, errorCode);
            metrics.recordWebhookDeliveryLatency(eventType, duration);

            log.error("Failed to deliver webhook to {} for event {}: {}",
                    webhook.getUrl(), eventType, ex.getMessage());
        } finally {
            webhookLogRepository.save(logEntry);
        }
    }

    private void markSuccess(WebhookConfig webhook, WebhookLog logEntry, ResponseEntity<String> response, long duration) {
        logEntry.setHttpStatusCode(response.getStatusCode().value());
        logEntry.setResponseBody(response.getBody());
        logEntry.setStatus(WebhookLog.DeliveryStatus.SUCCESS);
        logEntry.setDurationMs(duration);
        logEntry.setErrorMessage(null);
        logEntry.setLastErrorCode(null);
        logEntry.setLastErrorMessage(null);
        logEntry.setLastStatus(response.getStatusCode().value());
        logEntry.setNextRetryAt(null);

        webhook.setLastTriggeredAt(LocalDateTime.now());
        webhook.setSuccessCount(Optional.ofNullable(webhook.getSuccessCount()).orElse(0) + 1);
        webhook.setFailureCount(0);
        webhookConfigRepository.save(webhook);
    }

    private String markFailure(WebhookConfig webhook, WebhookLog logEntry, Exception ex, long duration) {
        FailureDetails failureDetails = classifyFailure(ex);
        String errorMessage = truncateErrorMessage(failureDetails.message);

        logEntry.setDurationMs(duration);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setLastErrorMessage(errorMessage);
        logEntry.setLastErrorCode(failureDetails.errorCode);
        logEntry.setLastStatus(failureDetails.httpStatus);
        logEntry.setAttemptCount(Optional.ofNullable(logEntry.getAttemptCount()).orElse(0) + 1);
        logEntry.setHttpStatusCode(failureDetails.httpStatus);
        if (failureDetails.responseBody != null) {
            logEntry.setResponseBody(truncateErrorMessage(failureDetails.responseBody));
        } else {
            logEntry.setResponseBody(null);
        }

        int currentFailureCount = Optional.ofNullable(webhook.getFailureCount()).orElse(0) + 1;
        webhook.setFailureCount(currentFailureCount);

        if (currentFailureCount >= 10) {
            webhook.setStatus(WebhookConfig.WebhookStatus.SUSPENDED);
            webhook.setIsActive(false);
            log.warn("Webhook {} suspended due to {} consecutive failures", webhook.getId(), currentFailureCount);
        }
        webhookConfigRepository.save(webhook);

        int configuredRetries = Math.max(effectiveRetryCount(webhook), 0);
        int currentRetryCount = Optional.ofNullable(logEntry.getRetryCount()).orElse(0);
        int nextRetryCount = currentRetryCount + 1;

        if (nextRetryCount <= configuredRetries
                && webhook.getStatus() == WebhookConfig.WebhookStatus.ACTIVE
                && Boolean.TRUE.equals(webhook.getIsActive())) {
            long delaySeconds = (long) Math.pow(2, nextRetryCount);
            logEntry.setStatus(WebhookLog.DeliveryStatus.RETRYING);
            logEntry.setRetryCount(nextRetryCount);
            logEntry.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        } else {
            logEntry.setStatus(WebhookLog.DeliveryStatus.FAILURE);
            logEntry.setNextRetryAt(null);
        }
        return failureDetails.errorCode;
    }

    private RestTemplate createRestTemplate(WebhookConfig webhook) {
        int timeoutSeconds = effectiveTimeoutSeconds(webhook);
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    private int effectiveRetryCount(WebhookConfig webhook) {
        Integer retryCount = webhook.getRetryCount();
        return retryCount != null ? retryCount : DEFAULT_RETRY_COUNT;
    }

    private int effectiveTimeoutSeconds(WebhookConfig webhook) {
        Integer timeout = webhook.getTimeoutSeconds();
        if (timeout == null || timeout <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return timeout;
    }

    private String resolvedClaimedBy() {
        return (retryClaimedBy == null || retryClaimedBy.isBlank())
                ? DEFAULT_CLAIMED_BY
                : retryClaimedBy.trim();
    }

    private FailureDetails classifyFailure(Exception ex) {
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getRawStatusCode();
            return new FailureDetails(
                    "HTTP_" + status,
                    status,
                    defaultIfBlank(responseException.getMessage(), ex.getClass().getSimpleName()),
                    responseException.getResponseBodyAsString()
            );
        }

        Throwable rootCause = rootCause(ex);
        if (rootCause instanceof SocketTimeoutException || containsIgnoreCase(ex.getMessage(), "timed out")) {
            return new FailureDetails("TIMEOUT", null, defaultIfBlank(ex.getMessage(), "Request timed out"), null);
        }
        if (rootCause instanceof UnknownHostException) {
            return new FailureDetails("DNS", null, defaultIfBlank(ex.getMessage(), "Unknown host"), null);
        }
        if (rootCause instanceof SSLException) {
            return new FailureDetails("SSL", null, defaultIfBlank(ex.getMessage(), "TLS/SSL failure"), null);
        }
        return new FailureDetails("DELIVERY_ERROR", null, defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()), null);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    /**
     * Generate HMAC signature for webhook payload.
     */
    private String generateHmacSignature(String payload, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verify webhook signature.
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
     * Create a new webhook configuration.
     */
    @Transactional
    public WebhookConfig createWebhook(WebhookConfig config) {
        if (config.getSecret() == null || config.getSecret().isEmpty()) {
            config.setSecret(UUID.randomUUID().toString());
        }

        if (config.getRetryCount() == null) {
            config.setRetryCount(DEFAULT_RETRY_COUNT);
        }
        if (config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0) {
            config.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
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
     * Update webhook configuration scoped by owner.
     */
    @Transactional
    public WebhookConfig updateWebhook(UUID webhookId, UUID userId, WebhookConfig updates) {
        WebhookConfig existing = getWebhookForUserOrThrow(webhookId, userId);

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
     * Delete webhook configuration scoped by owner.
     */
    @Transactional
    public void deleteWebhook(UUID webhookId, UUID userId) {
        WebhookConfig webhook = getWebhookForUserOrThrow(webhookId, userId);

        webhook.setStatus(WebhookConfig.WebhookStatus.DELETED);
        webhook.setIsActive(false);
        webhookConfigRepository.save(webhook);
    }

    /**
     * Get webhook logs scoped by owner.
     */
    @Transactional(readOnly = true)
    public List<WebhookLog> getWebhookLogs(UUID webhookId, UUID userId, int limit) {
        // Enforce ownership check first to avoid IDOR.
        getWebhookForUserOrThrow(webhookId, userId);
        return webhookLogRepository.findByWebhookConfigIdAndWebhookConfigUserId(
                webhookId,
                userId,
                org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * Get all webhooks for a user.
     */
    @Transactional(readOnly = true)
    public List<WebhookConfig> getUserWebhooks(UUID userId) {
        return webhookConfigRepository.findByUserId(userId);
    }

    /**
     * Reactivate a suspended webhook scoped by owner.
     */
    @Transactional
    public WebhookConfig reactivateWebhook(UUID webhookId, UUID userId) {
        WebhookConfig webhook = getWebhookForUserOrThrow(webhookId, userId);

        webhook.setStatus(WebhookConfig.WebhookStatus.ACTIVE);
        webhook.setFailureCount(0);
        webhook.setIsActive(true);

        WebhookConfig saved = webhookConfigRepository.save(webhook);
        log.info("Webhook {} reactivated", webhookId);
        return saved;
    }

    private WebhookConfig getWebhookForUserOrThrow(UUID webhookId, UUID userId) {
        return webhookConfigRepository.findByIdAndUserId(webhookId, userId)
                .orElseThrow(() -> new AccessDeniedException("Webhook access denied"));
    }

    private static class FailureDetails {
        private final String errorCode;
        private final Integer httpStatus;
        private final String message;
        private final String responseBody;

        private FailureDetails(String errorCode, Integer httpStatus, String message, String responseBody) {
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
            this.message = message;
            this.responseBody = responseBody;
        }
    }
}
