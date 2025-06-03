package com.mcpgateway.dto;

import com.mcpgateway.domain.PaymentTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BillingDTO {
    private UUID transactionId;
    private BigDecimal amount;
    private LocalDateTime transactionTime;
    private PaymentTransaction.PaymentStatus status;
    private String paymentMethod;
} 