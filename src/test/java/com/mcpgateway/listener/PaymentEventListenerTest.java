package com.mcpgateway.listener;

import com.mcpgateway.domain.AuditLog;
import com.mcpgateway.domain.Payment;
import com.mcpgateway.event.PaymentCreatedEvent;
import com.mcpgateway.service.AuditLogService;
import com.mcpgateway.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentEventListener
 *
 * Tests async event processing for payment operations
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private AuditLogService auditLogService;

    private PaymentEventListener paymentEventListener;

    private Payment testPayment;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        paymentEventListener = new PaymentEventListener(webhookService, auditLogService);

        testUserId = UUID.randomUUID();
        testPayment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(Payment.PaymentStatus.SUCCEEDED)
                .paymentIntentId("pi_test123")
                .build();
    }

    @Test
    void onPaymentCreated_Success_SendsWebhookAndLogsAudit() {
        // Given
        PaymentCreatedEvent event = new PaymentCreatedEvent(testPayment);

        doNothing().when(webhookService).sendWebhook(any(UUID.class), anyString(), anyMap());
        doNothing().when(auditLogService).logPaymentOperation(
                any(UUID.class), anyString(), anyString(), anyString(), any(AuditLog.Status.class), anyMap()
        );

        // When
        paymentEventListener.onPaymentCreated(event);

        // Then
        // Verify webhook was sent
        ArgumentCaptor<Map<String, Object>> webhookPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webhookService, timeout(1000)).sendWebhook(
                eq(testUserId),
                eq("payment.created"),
                webhookPayloadCaptor.capture()
        );

        Map<String, Object> webhookPayload = webhookPayloadCaptor.getValue();
        assertThat(webhookPayload).containsEntry("event_type", "payment.created");
        assertThat(webhookPayload).containsKey("payment_id");
        assertThat(webhookPayload).containsKey("user_id");
        assertThat(webhookPayload).containsEntry("amount", testPayment.getAmount().toString());
        assertThat(webhookPayload).containsEntry("currency", "USD");

        // Verify audit log was created
        verify(auditLogService, timeout(1000)).logPaymentOperation(
                eq(testUserId),
                eq("PAYMENT_CREATED"),
                eq(testPayment.getId().toString()),
                eq(AuditLog.Status.SUCCESS),
                anyMap()
        );
    }

    @Test
    void onPaymentCreated_WebhookFailure_StillLogsAudit() {
        // Given
        PaymentCreatedEvent event = new PaymentCreatedEvent(testPayment);

        // Webhook throws exception
        doThrow(new RuntimeException("Webhook service unavailable"))
                .when(webhookService).sendWebhook(any(UUID.class), anyString(), anyMap());

        doNothing().when(auditLogService).logPaymentOperation(
                any(UUID.class), anyString(), anyString(), anyString(), any(AuditLog.Status.class), anyMap()
        );

        // When
        paymentEventListener.onPaymentCreated(event);

        // Then
        // Webhook was attempted
        verify(webhookService, timeout(1000)).sendWebhook(any(), anyString(), anyMap());

        // Audit log still created (error handling in listener)
        verify(auditLogService, timeout(1000)).logPaymentOperation(
                any(UUID.class), anyString(), anyString(), anyString(), any(AuditLog.Status.class), anyMap()
        );
    }

    @Test
    void onPaymentCreated_AuditLogFailure_DoesNotThrow() {
        // Given
        PaymentCreatedEvent event = new PaymentCreatedEvent(testPayment);

        doNothing().when(webhookService).sendWebhook(any(UUID.class), anyString(), anyMap());

        // Audit log throws exception
        doThrow(new RuntimeException("Database unavailable"))
                .when(auditLogService).logPaymentOperation(
                any(UUID.class), anyString(), anyString(), anyString(), any(AuditLog.Status.class), anyMap()
        );

        // When
        paymentEventListener.onPaymentCreated(event);

        // Then
        // Webhook still sent
        verify(webhookService, timeout(1000)).sendWebhook(any(), anyString(), anyMap());

        // Audit log was attempted
        verify(auditLogService, timeout(1000)).logPaymentOperation(
                any(), anyString(), anyString(), anyString(), any(), anyMap()
        );

        // No exception propagated (listener catches it)
    }

    @Test
    void onPaymentCreated_VerifyWebhookPayloadStructure() {
        // Given
        PaymentCreatedEvent event = new PaymentCreatedEvent(testPayment);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        paymentEventListener.onPaymentCreated(event);

        // Then
        verify(webhookService, timeout(1000)).sendWebhook(
                any(UUID.class),
                eq("payment.created"),
                payloadCaptor.capture()
        );

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).isNotNull();
        assertThat(payload).containsKeys(
                "event_type", "payment_id", "user_id", "amount", "currency", "status", "timestamp"
        );
        assertThat(payload.get("event_type")).isEqualTo("payment.created");
        assertThat(payload.get("status")).isEqualTo(testPayment.getStatus().toString());
    }

    @Test
    void onPaymentCreated_MultipleEvents_ProcessedIndependently() {
        // Given
        Payment payment1 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(Payment.PaymentStatus.PENDING)
                .build();

        Payment payment2 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("150.00"))
                .currency("GBP")
                .status(Payment.PaymentStatus.SUCCEEDED)
                .build();

        PaymentCreatedEvent event1 = new PaymentCreatedEvent(payment1);
        PaymentCreatedEvent event2 = new PaymentCreatedEvent(payment2);

        // When
        paymentEventListener.onPaymentCreated(event1);
        paymentEventListener.onPaymentCreated(event2);

        // Then
        // Both events processed
        verify(webhookService, timeout(1000).times(2)).sendWebhook(any(), eq("payment.created"), anyMap());
        verify(auditLogService, timeout(1000).times(2)).logPaymentOperation(
                any(), eq("PAYMENT_CREATED"), anyString(), any(), anyMap()
        );
    }
}
