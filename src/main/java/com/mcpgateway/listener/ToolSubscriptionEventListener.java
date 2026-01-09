package com.mcpgateway.listener;

import com.mcpgateway.event.ToolSubscribedEvent;
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
 * Listener for tool subscription domain events
 *
 * Handles:
 * - Confirmation email notifications
 * - Tool access provisioning
 * - Subscription analytics
 * - Webhook notifications
 * - Audit logging
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolSubscriptionEventListener {

    private final WebhookService webhookService;
    private final AuditLogService auditLogService;
    // private final EmailService emailService; // Future enhancement

    /**
     * Handle tool subscription event asynchronously
     */
    @Async
    @EventListener
    public void onToolSubscribed(ToolSubscribedEvent event) {
        log.info("Processing ToolSubscribedEvent: subscriptionId={}, userId={}, toolId={}, toolName={}",
                event.getSubscriptionId(), event.getUserId(), event.getToolId(), event.getToolName());

        try {
            // Send confirmation email (future enhancement)
            // sendConfirmationEmail(event);

            // Grant tool access (already done in service, this is for additional logic)
            grantToolAccess(event);

            // Send webhook notification
            sendWebhookNotification(event);

            // Log audit trail
            logAuditTrail(event);

            // Track subscription in analytics (future enhancement)
            // analyticsService.trackToolSubscription(event);

            log.info("Successfully processed ToolSubscribedEvent: subscriptionId={}", event.getSubscriptionId());
        } catch (Exception e) {
            log.error("Error processing ToolSubscribedEvent: subscriptionId={}", event.getSubscriptionId(), e);
            // Don't throw - event processing should not fail the main transaction
        }
    }

    /**
     * Future enhancement: Send confirmation email
     */
    private void sendConfirmationEmail(ToolSubscribedEvent event) {
        log.debug("Confirmation email would be sent for tool subscription: {}", event.getToolName());
        // emailService.sendToolSubscriptionConfirmation(event.getUserId(), event.getToolName());
    }

    /**
     * Grant additional tool access if needed
     */
    private void grantToolAccess(ToolSubscribedEvent event) {
        log.debug("Tool access granted for user {} to tool {}", event.getUserId(), event.getToolName());
        // Additional access provisioning logic if needed
        // e.g., create API keys, set up permissions, etc.
    }

    private void sendWebhookNotification(ToolSubscribedEvent event) {
        try {
            Map<String, Object> payload = Map.of(
                    "event_type", "tool.subscribed",
                    "subscription_id", event.getSubscriptionId().toString(),
                    "user_id", event.getUserId().toString(),
                    "tool_id", event.getToolId().toString(),
                    "tool_name", event.getToolName(),
                    "status", event.getStatus().toString(),
                    "timestamp", event.getOccurredAt().toString()
            );

            webhookService.sendWebhook(event.getUserId(), "tool.subscribed", payload);
            log.debug("Webhook notification sent for tool subscription: {}", event.getSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to send webhook for tool subscription: {}", event.getSubscriptionId(), e);
        }
    }

    private void logAuditTrail(ToolSubscribedEvent event) {
        try {
            auditLogService.log(
                    event.getUserId(),
                    null, // username not available in event
                    "TOOL_SUBSCRIBED",
                    "subscription",
                    event.getSubscriptionId().toString(),
                    AuditLog.Status.SUCCESS,
                    Map.of(
                            "tool_id", event.getToolId().toString(),
                            "tool_name", event.getToolName(),
                            "status", event.getStatus().toString()
                    )
            );
            log.debug("Audit log created for tool subscription: {}", event.getSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to create audit log for tool subscription: {}", event.getSubscriptionId(), e);
        }
    }
}
