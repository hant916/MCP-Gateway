package com.mcpgateway.controller;

import com.mcpgateway.domain.Payment;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.payment.CreatePaymentIntentRequest;
import com.mcpgateway.dto.payment.PaymentHistoryDTO;
import com.mcpgateway.dto.payment.PaymentIntentResponse;
import com.mcpgateway.ratelimit.RateLimit;
import com.mcpgateway.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Controller for payment operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing APIs")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook.secret:${STRIPE_WEBHOOK_SECRET:}}")
    private String webhookSecret;

    @PostMapping("/create-intent")
    @Operation(summary = "Create a payment intent")
    @RateLimit(limit = 50, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @AuthenticationPrincipal User user) {

        try {
            PaymentIntentResponse response = paymentService.createPaymentIntent(user.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create payment intent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/history")
    @Operation(summary = "Get payment history for current user")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Page<PaymentHistoryDTO>> getPaymentHistory(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {

        Page<PaymentHistoryDTO> history = paymentService.getUserPaymentHistory(user.getId(), page, size);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Payment> getPaymentDetails(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal User user) {

        Payment payment = paymentService.getPaymentById(paymentId);

        // Security check: ensure user can only see their own payments
        if (!payment.getUserId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(payment);
    }

    /**
     * Stripe webhook endpoint
     * This endpoint receives events from Stripe about payment status changes
     */
    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook endpoint (internal)")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Stripe webhook secret not configured");
            return ResponseEntity.ok("Webhook secret not configured");
        }

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // Handle the event
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;

        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        }

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    if (stripeObject instanceof com.stripe.model.PaymentIntent) {
                        com.stripe.model.PaymentIntent paymentIntent =
                                (com.stripe.model.PaymentIntent) stripeObject;
                        String chargeId = paymentIntent.getLatestCharge();
                        paymentService.handleSuccessfulPayment(paymentIntent.getId(), chargeId);
                    }
                    break;

                case "payment_intent.payment_failed":
                    if (stripeObject instanceof com.stripe.model.PaymentIntent) {
                        com.stripe.model.PaymentIntent paymentIntent =
                                (com.stripe.model.PaymentIntent) stripeObject;
                        String failureReason = paymentIntent.getLastPaymentError() != null ?
                                paymentIntent.getLastPaymentError().getMessage() : "Unknown error";
                        paymentService.handleFailedPayment(paymentIntent.getId(), failureReason);
                    }
                    break;

                default:
                    log.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing Stripe webhook event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok("Success");
    }
}
