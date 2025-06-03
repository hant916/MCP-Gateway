package com.mcpgateway.controller;

import com.mcpgateway.domain.PaymentTransaction;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.dto.BillingDTO;
import com.mcpgateway.dto.SubscriptionDTO;
import com.mcpgateway.dto.SubscriptionRequest;
import com.mcpgateway.service.BillingService;
import com.mcpgateway.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/client/subscriptions")
@RequiredArgsConstructor
public class ClientSubscriptionController {
    private final SubscriptionService subscriptionService;
    private final BillingService billingService;
    
    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<SubscriptionDTO> subscribe(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SubscriptionRequest request
    ) {
        ToolSubscription subscription = subscriptionService.subscribe(
                UUID.fromString(userDetails.getUsername()),
                request.getToolId(),
                request.getPricingModel()
        );
        return ResponseEntity.ok(convertToDTO(subscription));
    }
    
    @GetMapping("/bills")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<BillingDTO>> getBillingHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        UUID clientId = UUID.fromString(userDetails.getUsername());
        List<PaymentTransaction> transactions = billingService.getTransactionHistory(clientId, startDate, endDate);
        
        List<BillingDTO> billingDTOs = transactions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(billingDTOs);
    }
    
    private SubscriptionDTO convertToDTO(ToolSubscription subscription) {
        return SubscriptionDTO.builder()
                .id(subscription.getId())
                .toolId(subscription.getTool().getId())
                .clientId(subscription.getClient().getId())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus())
                .remainingQuota(subscription.getRemainingQuota())
                .build();
    }
    
    private BillingDTO convertToDTO(PaymentTransaction transaction) {
        return BillingDTO.builder()
                .transactionId(transaction.getId())
                .amount(transaction.getAmount())
                .transactionTime(transaction.getTransactionTime())
                .status(transaction.getStatus())
                .paymentMethod(transaction.getPaymentMethod())
                .build();
    }
} 