package com.mcpgateway.listener;

import com.mcpgateway.event.UserRegisteredEvent;
import com.mcpgateway.service.AuditLogService;
import com.mcpgateway.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener for user-related domain events
 *
 * Handles:
 * - Welcome email (future with email service)
 * - User quota initialization
 * - Default subscriptions setup
 * - Analytics tracking
 * - Audit logging
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final AuditLogService auditLogService;
    // private final EmailService emailService; // Future enhancement
    // private final QuotaService quotaService; // Future enhancement

    /**
     * Handle user registration event asynchronously
     */
    @Async
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Processing UserRegisteredEvent: userId={}, username={}, email={}",
                event.getUserId(), event.getUsername(), event.getEmail());

        try {
            // Send welcome email (future enhancement)
            // sendWelcomeEmail(event);

            // Initialize user quota (future enhancement)
            // initializeUserQuota(event);

            // Log audit trail
            logAuditTrail(event);

            // Track registration in analytics (future enhancement)
            // analyticsService.trackUserRegistration(event);

            log.info("Successfully processed UserRegisteredEvent: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Error processing UserRegisteredEvent: userId={}", event.getUserId(), e);
            // Don't throw - event processing should not fail the main transaction
        }
    }

    /**
     * Future enhancement: Send welcome email
     */
    private void sendWelcomeEmail(UserRegisteredEvent event) {
        log.debug("Welcome email would be sent to: {}", event.getEmail());
        // emailService.sendWelcomeEmail(event.getEmail(), event.getUsername());
    }

    /**
     * Future enhancement: Initialize user quota
     */
    private void initializeUserQuota(UserRegisteredEvent event) {
        log.debug("User quota would be initialized for: {}", event.getUserId());
        // Default quota: 1000 API calls/month, 10 GB storage
        // quotaService.initializeQuota(event.getUserId(), 1000, 10_000_000_000L);
    }

    private void logAuditTrail(UserRegisteredEvent event) {
        try {
            auditLogService.logUserManagement(
                    event.getUserId(),
                    event.getUsername(),
                    "USER_REGISTERED",
                    event.getUserId().toString(),
                    AuditLog.Status.SUCCESS,
                    Map.of(
                            "email", event.getEmail(),
                            "role", event.getRole().toString(),
                            "registrationMethod", event.getRegistrationMethod()
                    )
            );
            log.debug("Audit log created for user registration: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to create audit log for user registration: {}", event.getUserId(), e);
        }
    }
}
