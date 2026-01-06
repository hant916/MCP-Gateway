package com.mcpgateway.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for payment intent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {

    private String paymentIntentId;
    private String clientSecret;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String description;
}
