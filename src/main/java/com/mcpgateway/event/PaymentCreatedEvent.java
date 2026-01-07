package com.mcpgateway.event;

import com.mcpgateway.domain.Payment;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published when a payment is created
 *
 * Listeners can:
 * - Send webhook notifications
 * - Update analytics
 * - Send email notifications
 * - Update user quota
 */
@Getter
public class PaymentCreatedEvent extends DomainEvent {

    private final UUID paymentId;
    private final UUID userId;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentIntentId;
    private final Payment.PaymentStatus status;

    public PaymentCreatedEvent(Payment payment) {
        super();
        this.paymentId = payment.getId();
        this.userId = payment.getUserId();
        this.amount = payment.getAmount();
        this.currency = payment.getCurrency();
        this.paymentIntentId = payment.getPaymentIntentId();
        this.status = payment.getStatus();
    }

    @Override
    public UUID getAggregateId() {
        return paymentId;
    }

    @Override
    public String getEventData() {
        return String.format("Payment %s created: %s %s by user %s (status: %s)",
                paymentId, amount, currency, userId, status);
    }
}
