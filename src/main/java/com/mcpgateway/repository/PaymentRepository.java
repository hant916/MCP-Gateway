package com.mcpgateway.repository;

import com.mcpgateway.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find payment by Stripe payment intent ID
     */
    Optional<Payment> findByPaymentIntentId(String paymentIntentId);

    /**
     * Find all payments for a user
     */
    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find all payments for a tool
     */
    Page<Payment> findByToolId(UUID toolId, Pageable pageable);

    /**
     * Find all payments by status
     */
    Page<Payment> findByStatus(Payment.PaymentStatus status, Pageable pageable);
}
