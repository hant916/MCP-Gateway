package com.mcpgateway.controller;

import com.mcpgateway.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Stripe webhook endpoint.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Stripe Webhook", description = "Stripe webhook callback endpoint")
public class StripeWebhookController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook.secret:${STRIPE_WEBHOOK_SECRET:}}")
    private String webhookSecret;

    @PostMapping("/stripe/webhook")
    @Operation(summary = "Stripe webhook endpoint")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        log.info("Processing Stripe webhook via canonical endpoint: /stripe/webhook");

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

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    if (stripeObject instanceof com.stripe.model.PaymentIntent paymentIntent) {
                        String chargeId = paymentIntent.getLatestCharge();
                        paymentService.handleSuccessfulPayment(paymentIntent.getId(), chargeId);
                    }
                    break;
                case "payment_intent.payment_failed":
                    if (stripeObject instanceof com.stripe.model.PaymentIntent paymentIntent) {
                        String failureReason = paymentIntent.getLastPaymentError() != null
                                ? paymentIntent.getLastPaymentError().getMessage()
                                : "Unknown error";
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
