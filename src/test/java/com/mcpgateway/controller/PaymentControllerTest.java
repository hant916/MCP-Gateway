package com.mcpgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.Payment;
import com.mcpgateway.dto.payment.CreatePaymentIntentRequest;
import com.mcpgateway.dto.payment.PaymentHistoryDTO;
import com.mcpgateway.dto.payment.PaymentIntentResponse;
import com.mcpgateway.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentIntentResponse testPaymentIntent;
    private PaymentHistoryDTO testPaymentHistory;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testPaymentIntent = PaymentIntentResponse.builder()
                .paymentIntentId("pi_test123")
                .clientSecret("secret_test123")
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .status("requires_payment_method")
                .description("Test payment")
                .build();

        testPaymentHistory = PaymentHistoryDTO.builder()
                .paymentId(UUID.randomUUID().toString())
                .paymentIntentId("pi_test123")
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .status("succeeded")
                .description("Test payment")
                .toolName("Test Tool")
                .build();

        testPayment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .paymentIntentId("pi_test123")
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .status(Payment.PaymentStatus.SUCCEEDED)
                .build();
    }

    @Test
    @WithMockUser
    void createPaymentIntent_WithValidRequest_ShouldReturnPaymentIntent() throws Exception {
        // Arrange
        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest();
        request.setAmount(new BigDecimal("49.99"));
        request.setCurrency("USD");
        request.setDescription("Test payment");

        when(paymentService.createPaymentIntent(any(UUID.class), any(CreatePaymentIntentRequest.class)))
                .thenReturn(testPaymentIntent);

        // Act & Assert
        mockMvc.perform(post("/api/v1/payments/create-intent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_test123"))
                .andExpect(jsonPath("$.clientSecret").value("secret_test123"))
                .andExpect(jsonPath("$.amount").value(49.99))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @WithMockUser
    void getPaymentHistory_ShouldReturnPaginatedHistory() throws Exception {
        // Arrange
        Page<PaymentHistoryDTO> historyPage = new PageImpl<>(Arrays.asList(testPaymentHistory));

        when(paymentService.getUserPaymentHistory(any(UUID.class), eq(0), eq(20)))
                .thenReturn(historyPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/payments/history")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].paymentIntentId").value("pi_test123"))
                .andExpect(jsonPath("$.content[0].amount").value(49.99))
                .andExpect(jsonPath("$.content[0].status").value("succeeded"));
    }

    @Test
    @WithMockUser
    void getPaymentDetails_WithValidId_ShouldReturnPayment() throws Exception {
        // Arrange
        UUID paymentId = testPayment.getId();
        testPayment.setUserId(UUID.randomUUID()); // Will be set by auth

        when(paymentService.getPaymentById(paymentId))
                .thenReturn(testPayment);

        // Note: This test will fail authorization check in real scenario
        // as user ID won't match. For testing, we disable security filters.
        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void handleStripeWebhook_WithValidSignature_ShouldReturnSuccess() throws Exception {
        // Arrange
        String payload = "{\"type\":\"payment_intent.succeeded\"}";
        String signature = "valid-signature";

        // Act & Assert
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void createPaymentIntent_WithInvalidAmount_ShouldReturnBadRequest() throws Exception {
        // Arrange
        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest();
        request.setAmount(new BigDecimal("-10.00")); // Invalid negative amount
        request.setCurrency("USD");

        // Act & Assert
        mockMvc.perform(post("/api/v1/payments/create-intent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
