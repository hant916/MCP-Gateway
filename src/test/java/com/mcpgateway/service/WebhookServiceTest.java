package com.mcpgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.metrics.McpGatewayMetrics;
import com.mcpgateway.repository.WebhookConfigRepository;
import com.mcpgateway.repository.WebhookLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookConfigRepository webhookConfigRepository;

    @Mock
    private WebhookLogRepository webhookLogRepository;

    @Mock
    private McpGatewayMetrics metrics;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private WebhookService webhookService;

    private WebhookConfig testWebhook;
    private UUID userId;
    private UUID webhookId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        webhookId = UUID.randomUUID();

        testWebhook = WebhookConfig.builder()
                .id(webhookId)
                .userId(userId)
                .url("https://example.com/webhook")
                .secret("test-secret-key")
                .events("payment.success,subscription.created")
                .status(WebhookConfig.WebhookStatus.ACTIVE)
                .isActive(true)
                .retryCount(3)
                .timeoutSeconds(30)
                .successCount(0)
                .failureCount(0)
                .build();

        webhookService = new WebhookService(
                webhookConfigRepository,
                webhookLogRepository,
                metrics,
                restTemplateBuilder,
                objectMapper
        );

        lenient().when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.build()).thenReturn(restTemplate);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"event\":\"payment.success\"}");

        lenient().when(webhookLogRepository.save(any(WebhookLog.class))).thenAnswer(invocation -> {
            WebhookLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
    }

    @Test
    void createWebhook_WithValidConfig_ShouldCreateWebhook() {
        WebhookConfig newWebhook = WebhookConfig.builder()
                .userId(userId)
                .url("https://example.com/webhook")
                .events("payment.success")
                .build();

        when(webhookConfigRepository.save(any(WebhookConfig.class)))
                .thenReturn(testWebhook);

        WebhookConfig result = webhookService.createWebhook(newWebhook);

        assertThat(result).isNotNull();
        verify(webhookConfigRepository).save(argThat(webhook ->
                webhook.getRetryCount() == 3 &&
                webhook.getTimeoutSeconds() == 30 &&
                webhook.getStatus() == WebhookConfig.WebhookStatus.ACTIVE &&
                webhook.getIsActive() == true
        ));
    }

    @Test
    void updateWebhook_WithValidUpdates_ShouldUpdateWebhook() {
        WebhookConfig updates = WebhookConfig.builder()
                .url("https://newurl.com/webhook")
                .events("payment.success,payment.failure")
                .description("Updated webhook")
                .isActive(false)
                .retryCount(5)
                .build();

        when(webhookConfigRepository.findByIdAndUserId(webhookId, userId))
                .thenReturn(Optional.of(testWebhook));
        when(webhookConfigRepository.save(any(WebhookConfig.class)))
                .thenReturn(testWebhook);

        WebhookConfig result = webhookService.updateWebhook(webhookId, userId, updates);

        assertThat(result).isNotNull();
        verify(webhookConfigRepository).save(argThat(webhook ->
                webhook.getUrl().equals("https://newurl.com/webhook") &&
                webhook.getEvents().equals("payment.success,payment.failure") &&
                webhook.getDescription().equals("Updated webhook") &&
                webhook.getIsActive() == false &&
                webhook.getRetryCount() == 5
        ));
    }

    @Test
    void updateWebhook_WithUnauthorizedUser_ShouldThrowAccessDenied() {
        UUID otherUserId = UUID.randomUUID();

        when(webhookConfigRepository.findByIdAndUserId(webhookId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.updateWebhook(webhookId, otherUserId, WebhookConfig.builder().build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteWebhook_ShouldMarkAsDeleted() {
        when(webhookConfigRepository.findByIdAndUserId(webhookId, userId))
                .thenReturn(Optional.of(testWebhook));

        webhookService.deleteWebhook(webhookId, userId);

        verify(webhookConfigRepository).save(argThat(webhook ->
                webhook.getStatus() == WebhookConfig.WebhookStatus.DELETED &&
                webhook.getIsActive() == false
        ));
    }

    @Test
    void getWebhookLogs_ShouldReturnPaginatedLogs() {
        WebhookLog log1 = WebhookLog.builder()
                .id(UUID.randomUUID())
                .webhookConfig(testWebhook)
                .eventType("payment.success")
                .status(WebhookLog.DeliveryStatus.SUCCESS)
                .build();

        Page<WebhookLog> logPage = new PageImpl<>(List.of(log1));

        when(webhookConfigRepository.findByIdAndUserId(webhookId, userId))
                .thenReturn(Optional.of(testWebhook));
        when(webhookLogRepository.findByWebhookConfigIdAndWebhookConfigUserId(eq(webhookId), eq(userId), any(PageRequest.class)))
                .thenReturn(logPage);

        List<WebhookLog> result = webhookService.getWebhookLogs(webhookId, userId, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("payment.success");
    }

    @Test
    void getWebhookLogs_WithUnauthorizedUser_ShouldThrowAccessDenied() {
        UUID otherUserId = UUID.randomUUID();

        when(webhookConfigRepository.findByIdAndUserId(webhookId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.getWebhookLogs(webhookId, otherUserId, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getUserWebhooks_ShouldReturnAllUserWebhooks() {
        WebhookConfig webhook2 = WebhookConfig.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .url("https://example.com/webhook2")
                .build();

        when(webhookConfigRepository.findByUserId(userId))
                .thenReturn(Arrays.asList(testWebhook, webhook2));

        List<WebhookConfig> result = webhookService.getUserWebhooks(userId);

        assertThat(result).hasSize(2);
        verify(webhookConfigRepository).findByUserId(userId);
    }

    @Test
    void reactivateWebhook_ShouldResetStatusAndFailureCount() {
        testWebhook.setStatus(WebhookConfig.WebhookStatus.SUSPENDED);
        testWebhook.setFailureCount(15);
        testWebhook.setIsActive(false);

        when(webhookConfigRepository.findByIdAndUserId(webhookId, userId))
                .thenReturn(Optional.of(testWebhook));
        when(webhookConfigRepository.save(any(WebhookConfig.class)))
                .thenReturn(testWebhook);

        WebhookConfig result = webhookService.reactivateWebhook(webhookId, userId);

        assertThat(result).isNotNull();
        verify(webhookConfigRepository).save(argThat(webhook ->
                webhook.getStatus() == WebhookConfig.WebhookStatus.ACTIVE &&
                webhook.getFailureCount() == 0 &&
                webhook.getIsActive() == true
        ));
    }

    @Test
    void verifyWebhookSignature_WithAnyInput_ShouldNotThrow() {
        String payload = "{\"event\":\"payment.success\"}";
        String secret = "test-secret";

        boolean result = webhookService.verifyWebhookSignature(payload, "dummy", secret);
        assertThat(result).isFalse();
    }

    @Test
    void triggerWebhooks_ShouldFindAndAttemptDelivery() {
        String eventType = "payment.success";
        Map<String, Object> payload = Map.of("paymentId", "pay-123");

        when(webhookConfigRepository.findActiveWebhooksByEvent(eventType))
                .thenReturn(List.of(testWebhook));
        when(restTemplate.exchange(
                eq(testWebhook.getUrl()),
                eq(HttpMethod.POST),
                any(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        webhookService.triggerWebhooks(eventType, payload);

        verify(webhookConfigRepository).findActiveWebhooksByEvent(eventType);
        verify(restTemplate).exchange(eq(testWebhook.getUrl()), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    void processPendingRetries_WhenClaimNotAcquired_ShouldSkipDelivery() {
        WebhookLog retryLog = WebhookLog.builder()
                .id(UUID.randomUUID())
                .webhookConfig(testWebhook)
                .eventType("payment.success")
                .payload("{\"event\":\"payment.success\"}")
                .status(WebhookLog.DeliveryStatus.RETRYING)
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();

        when(webhookLogRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(WebhookLog.DeliveryStatus.RETRYING),
                any(LocalDateTime.class),
                any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(retryLog)));
        when(webhookLogRepository.claimDueRetry(
                eq(retryLog.getId()),
                eq(WebhookLog.DeliveryStatus.RETRYING),
                eq(WebhookLog.DeliveryStatus.IN_PROGRESS),
                any(LocalDateTime.class),
                eq("unknown-instance"),
                any(LocalDateTime.class)))
                .thenReturn(0);

        webhookService.processPendingRetries();

        verify(webhookLogRepository, never()).findById(any(UUID.class));
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class));
    }

    @Test
    void processPendingRetries_WhenClaimed_ShouldDeliverOnce() {
        WebhookLog retryLog = WebhookLog.builder()
                .id(UUID.randomUUID())
                .webhookConfig(testWebhook)
                .eventType("payment.success")
                .payload("{\"event\":\"payment.success\"}")
                .status(WebhookLog.DeliveryStatus.RETRYING)
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();

        when(webhookLogRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(WebhookLog.DeliveryStatus.RETRYING),
                any(LocalDateTime.class),
                any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(retryLog)));
        when(webhookLogRepository.claimDueRetry(
                eq(retryLog.getId()),
                eq(WebhookLog.DeliveryStatus.RETRYING),
                eq(WebhookLog.DeliveryStatus.IN_PROGRESS),
                any(LocalDateTime.class),
                eq("unknown-instance"),
                any(LocalDateTime.class)))
                .thenReturn(1);
        when(webhookLogRepository.findById(retryLog.getId())).thenReturn(Optional.of(retryLog));
        when(restTemplate.exchange(
                eq(testWebhook.getUrl()),
                eq(HttpMethod.POST),
                any(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        webhookService.processPendingRetries();

        verify(webhookLogRepository).claimDueRetry(
                eq(retryLog.getId()),
                eq(WebhookLog.DeliveryStatus.RETRYING),
                eq(WebhookLog.DeliveryStatus.IN_PROGRESS),
                any(LocalDateTime.class),
                eq("unknown-instance"),
                any(LocalDateTime.class));
        verify(metrics).recordWebhookRetryClaimed("unknown-instance");
        verify(restTemplate, times(1)).exchange(eq(testWebhook.getUrl()), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    void triggerWebhooks_WhenDeliveryFails_ShouldIncrementAttemptAndStoreErrorCode() {
        String eventType = "payment.success";
        Map<String, Object> payload = Map.of("paymentId", "pay-123");

        when(webhookConfigRepository.findActiveWebhooksByEvent(eventType))
                .thenReturn(List.of(testWebhook));
        when(restTemplate.exchange(
                eq(testWebhook.getUrl()),
                eq(HttpMethod.POST),
                any(),
                eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        webhookService.triggerWebhooks(eventType, payload);

        verify(webhookLogRepository, atLeast(2)).save(argThat(log ->
                "payment.success".equals(log.getEventType()) &&
                        log.getAttemptCount() != null &&
                        log.getAttemptCount() == 1 &&
                        "TIMEOUT".equals(log.getLastErrorCode()) &&
                        log.getLastStatus() == null
        ));
        verify(metrics).recordWebhookDeliveryFailure("payment.success", "TIMEOUT");
        verify(metrics, atLeastOnce()).recordWebhookDeliveryLatency(eq("payment.success"), anyLong());
    }

    @Test
    void triggerWebhooks_WhenDeliveryReturnsHttpError_ShouldStoreHttpStatusAsErrorCode() {
        String eventType = "payment.success";
        Map<String, Object> payload = Map.of("paymentId", "pay-123");

        when(webhookConfigRepository.findActiveWebhooksByEvent(eventType))
                .thenReturn(List.of(testWebhook));
        when(restTemplate.exchange(
                eq(testWebhook.getUrl()),
                eq(HttpMethod.POST),
                any(),
                eq(String.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        "{\"error\":\"invalid\"}".getBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                ));

        webhookService.triggerWebhooks(eventType, payload);

        verify(webhookLogRepository, atLeast(2)).save(argThat(log ->
                "payment.success".equals(log.getEventType()) &&
                        log.getAttemptCount() != null &&
                        log.getAttemptCount() == 1 &&
                        "HTTP_400".equals(log.getLastErrorCode()) &&
                        Integer.valueOf(400).equals(log.getLastStatus())
        ));
        verify(metrics).recordWebhookDeliveryFailure("payment.success", "HTTP_400");
        verify(metrics, atLeastOnce()).recordWebhookDeliveryLatency(eq("payment.success"), anyLong());
    }
}
