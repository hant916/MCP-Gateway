package com.mcpgateway.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for payment history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryDTO {

    private String paymentId;
    private String paymentIntentId;
    private BigDecimal amount;
    private String currency;
    private String status; // succeeded, pending, failed, canceled
    private String description;
    private String toolName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
