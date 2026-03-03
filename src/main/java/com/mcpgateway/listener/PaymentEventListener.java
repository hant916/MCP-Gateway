package com.mcpgateway.listener;

import com.mcpgateway.event.PaymentCreatedEvent;
import com.mcpgateway.service.AuditLogService;
import com.mcpgateway.service.WebhookService;
import com.mcpgateway.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener for payment-related domain events
 *
 * Handles:
 * - Webhook notifications to external systems
 * - Audit logging for compliance
 * - Analytics updates
 * - Email notifications (future)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final WebhookService webhookService;
    private final AuditLogService auditLogService;

    /**
     * Handle payment creation event asynchronously
     */
    @Async
    @EventListener
    public void onPaymentCreated(PaymentCreatedEvent event) {
        log.info("Processing PaymentCreatedEvent: paymentId={}, userId={}, amount={} {}",
                event.getPaymentId(), event.getUserId(), event.getAmount(), event.getCurrency());

        try {
            // Send webhook notification to external systems
            sendWebhookNotification(event);

            // Log audit trail for compliance
            logAuditTrail(event);

            // Update payment analytics (future enhancement)
            // analyticsService.trackPayment(event);

            log.info("Successfully processed PaymentCreatedEvent: paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Error processing PaymentCreatedEvent: paymentId={}", event.getPaymentId(), e);
            // Don't throw - event processing should not fail the main transaction
        }
    }

    private void sendWebhookNotification(PaymentCreatedEvent event) {
        try {
            Map<String, Object> payload = Map.of(
                    "event_type", "payment.created",
                    "payment_id", event.getPaymentId().toString(),
                    "user_id", event.getUserId().toString(),
                    "amount", event.getAmount().toString(),
                    "currency", event.getCurrency(),
                    "status", event.getStatus().toString(),
                    "timestamp", event.getOccurredAt().toString()
            );

            webhookService.sendWebhook(event.getUserId(), "payment.created", payload);
            log.debug("Webhook notification sent for payment: {}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to send webhook for payment: {}", event.getPaymentId(), e);
        }
    }

    private void logAuditTrail(PaymentCreatedEvent event) {
        try {
            auditLogService.logPaymentOperation(
                    event.getUserId(),
                    "PAYMENT_CREATED",
                    event.getPaymentId().toString(),
                    AuditLog.Status.SUCCESS,
                    Map.of(
                            "amount", event.getAmount().toString(),
                            "currency", event.getCurrency(),
                            "status", event.getStatus().toString()
                    )
            );
            log.debug("Audit log created for payment: {}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to create audit log for payment: {}", event.getPaymentId(), e);
        }
    }
}
