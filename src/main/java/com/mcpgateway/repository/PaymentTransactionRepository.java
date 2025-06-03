package com.mcpgateway.repository;

import com.mcpgateway.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    List<PaymentTransaction> findByClientIdAndTransactionTimeBetween(
            UUID clientId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
} 