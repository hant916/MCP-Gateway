package com.mcpgateway.service;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.Payment;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.dto.payment.CreatePaymentIntentRequest;
import com.mcpgateway.dto.payment.PaymentHistoryDTO;
import com.mcpgateway.dto.payment.PaymentIntentResponse;
import com.mcpgateway.repository.McpToolRepository;
import com.mcpgateway.repository.PaymentRepository;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for payment processing via Stripe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final McpToolRepository toolRepository;
    private final ToolSubscriptionRepository subscriptionRepository;

    /**
     * Create a payment intent
     */
    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            UUID userId,
            CreatePaymentIntentRequest request) throws StripeException {

        // Convert amount to cents (Stripe uses smallest currency unit)
        long amountInCents = request.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // Create metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        if (request.getToolId() != null) {
            metadata.put("toolId", request.getToolId().toString());
        }

        // Build Stripe payment intent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(request.getCurrency().toLowerCase())
                .setDescription(request.getDescription())
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        // Create payment intent in Stripe
        PaymentIntent paymentIntent = PaymentIntent.create(params);

        // Save payment record
        Payment payment = Payment.builder()
                .userId(userId)
                .paymentIntentId(paymentIntent.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(Payment.PaymentStatus.PENDING)
                .description(request.getDescription())
                .build();

        // Link to tool if specified
        if (request.getToolId() != null) {
            McpTool tool = toolRepository.findById(request.getToolId())
                    .orElseThrow(() -> new RuntimeException("Tool not found"));
            payment.setTool(tool);
        }

        paymentRepository.save(payment);

        log.info("Created payment intent {} for user {} amount {} {}",
                paymentIntent.getId(), userId, request.getAmount(), request.getCurrency());

        return PaymentIntentResponse.builder()
                .paymentIntentId(paymentIntent.getId())
                .clientSecret(paymentIntent.getClientSecret())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(paymentIntent.getStatus())
                .description(request.getDescription())
                .build();
    }

    /**
     * Handle successful payment (called by webhook)
     */
    @Transactional
    public void handleSuccessfulPayment(String paymentIntentId, String chargeId) {
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentIntentId));

        payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
        payment.setStripeChargeId(chargeId);
        paymentRepository.save(payment);

        // If payment is for a tool subscription, activate it
        if (payment.getTool() != null) {
            activateSubscriptionForPayment(payment);
        }

        log.info("Payment succeeded: {}", paymentIntentId);
    }

    /**
     * Handle failed payment (called by webhook)
     */
    @Transactional
    public void handleFailedPayment(String paymentIntentId, String failureReason) {
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentIntentId));

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);

        log.warn("Payment failed: {} - {}", paymentIntentId, failureReason);
    }

    /**
     * Get payment history for a user
     */
    @Transactional(readOnly = true)
    public Page<PaymentHistoryDTO> getUserPaymentHistory(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Payment> payments = paymentRepository.findByUserId(userId, pageable);

        return payments.map(payment -> PaymentHistoryDTO.builder()
                .paymentId(payment.getId().toString())
                .paymentIntentId(payment.getPaymentIntentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name().toLowerCase())
                .description(payment.getDescription())
                .toolName(payment.getTool() != null ? payment.getTool().getName() : null)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build());
    }

    /**
     * Get payment by ID
     */
    @Transactional(readOnly = true)
    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
    }

    /**
     * Activate subscription after successful payment
     */
    private void activateSubscriptionForPayment(Payment payment) {
        // Find or create subscription for this payment
        ToolSubscription subscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(
                        payment.getUserId(),
                        payment.getTool().getId(),
                        ToolSubscription.SubscriptionStatus.ACTIVE
                )
                .orElseGet(() -> {
                    // Create new subscription
                    ToolSubscription newSub = new ToolSubscription();
                    newSub.setTool(payment.getTool());
                    newSub.setClientId(payment.getUserId());
                    newSub.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);

                    // Set default quota based on pricing model
                    McpTool tool = payment.getTool();
                    int defaultQuota = switch (tool.getPricingModel()) {
                        case FREE_TIER -> 100;
                        case MONTHLY -> 10000;
                        case PAY_AS_YOU_GO -> 1000;
                    };

                    newSub.setMonthlyQuota(defaultQuota);
                    newSub.setRemainingQuota(defaultQuota);

                    return subscriptionRepository.save(newSub);
                });

        // Link payment to subscription
        payment.setSubscription(subscription);
        paymentRepository.save(payment);

        log.info("Activated subscription {} for payment {}", subscription.getId(), payment.getId());
    }
}
