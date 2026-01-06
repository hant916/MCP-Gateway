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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private McpToolRepository toolRepository;

    @Mock
    private ToolSubscriptionRepository subscriptionRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;
    private Payment testPayment;
    private McpTool testTool;
    private ToolSubscription testSubscription;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        testTool = new McpTool();
        testTool.setId(UUID.randomUUID());
        testTool.setName("Test Tool");
        testTool.setPricingModel(McpTool.PricingModel.MONTHLY);
        testTool.setPrice(new BigDecimal("9.99"));

        testPayment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .paymentIntentId("pi_test123")
                .amount(new BigDecimal("9.99"))
                .currency("USD")
                .status(Payment.PaymentStatus.PENDING)
                .description("Test payment")
                .tool(testTool)
                .build();

        testSubscription = new ToolSubscription();
        testSubscription.setId(UUID.randomUUID());
        testSubscription.setTool(testTool);
        testSubscription.setClientId(userId);
        testSubscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setMonthlyQuota(1000);
        testSubscription.setRemainingQuota(1000);
    }

    @Test
    void createPaymentIntent_WithValidRequest_ShouldCreatePayment() throws StripeException {
        // Arrange
        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest();
        request.setAmount(new BigDecimal("9.99"));
        request.setCurrency("USD");
        request.setDescription("Test payment");
        request.setToolId(testTool.getId());

        when(toolRepository.findById(testTool.getId()))
                .thenReturn(Optional.of(testTool));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);

        // Mock Stripe PaymentIntent
        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn("pi_test123");
        when(mockPaymentIntent.getClientSecret()).thenReturn("secret_test123");
        when(mockPaymentIntent.getStatus()).thenReturn("requires_payment_method");

        // Act & Assert with mocked static
        try (MockedStatic<PaymentIntent> mockedStatic = mockStatic(PaymentIntent.class)) {
            mockedStatic.when(() -> PaymentIntent.create(any()))
                    .thenReturn(mockPaymentIntent);

            PaymentIntentResponse result = paymentService.createPaymentIntent(userId, request);

            assertThat(result).isNotNull();
            assertThat(result.getPaymentIntentId()).isEqualTo("pi_test123");
            assertThat(result.getClientSecret()).isEqualTo("secret_test123");
            assertThat(result.getAmount()).isEqualTo(new BigDecimal("9.99"));
            assertThat(result.getCurrency()).isEqualTo("USD");

            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Test
    void createPaymentIntent_WithoutTool_ShouldCreatePaymentWithoutTool() throws StripeException {
        // Arrange
        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("USD");
        request.setDescription("General payment");

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);

        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn("pi_test456");
        when(mockPaymentIntent.getClientSecret()).thenReturn("secret_test456");
        when(mockPaymentIntent.getStatus()).thenReturn("requires_payment_method");

        // Act & Assert
        try (MockedStatic<PaymentIntent> mockedStatic = mockStatic(PaymentIntent.class)) {
            mockedStatic.when(() -> PaymentIntent.create(any()))
                    .thenReturn(mockPaymentIntent);

            PaymentIntentResponse result = paymentService.createPaymentIntent(userId, request);

            assertThat(result).isNotNull();
            verify(paymentRepository).save(argThat(payment ->
                    payment.getTool() == null
            ));
        }
    }

    @Test
    void handleSuccessfulPayment_ShouldUpdatePaymentStatus() {
        // Arrange
        String paymentIntentId = "pi_test123";
        String chargeId = "ch_test123";

        when(paymentRepository.findByPaymentIntentId(paymentIntentId))
                .thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                eq(userId),
                eq(testTool.getId()),
                eq(ToolSubscription.SubscriptionStatus.ACTIVE)
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);

        // Act
        paymentService.handleSuccessfulPayment(paymentIntentId, chargeId);

        // Assert
        verify(paymentRepository, times(2)).save(argThat(payment ->
                payment.getStatus() == Payment.PaymentStatus.SUCCEEDED &&
                payment.getStripeChargeId().equals(chargeId)
        ));
    }

    @Test
    void handleSuccessfulPayment_WithNonexistentPayment_ShouldThrowException() {
        // Arrange
        String paymentIntentId = "pi_nonexistent";
        when(paymentRepository.findByPaymentIntentId(paymentIntentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
                paymentService.handleSuccessfulPayment(paymentIntentId, "ch_test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void handleFailedPayment_ShouldUpdatePaymentStatusWithReason() {
        // Arrange
        String paymentIntentId = "pi_test123";
        String failureReason = "Insufficient funds";

        when(paymentRepository.findByPaymentIntentId(paymentIntentId))
                .thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);

        // Act
        paymentService.handleFailedPayment(paymentIntentId, failureReason);

        // Assert
        verify(paymentRepository).save(argThat(payment ->
                payment.getStatus() == Payment.PaymentStatus.FAILED &&
                payment.getFailureReason().equals(failureReason)
        ));
    }

    @Test
    void getUserPaymentHistory_ShouldReturnPagedHistory() {
        // Arrange
        Page<Payment> paymentPage = new PageImpl<>(Arrays.asList(testPayment));
        when(paymentRepository.findByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(paymentPage);

        // Act
        Page<PaymentHistoryDTO> result = paymentService.getUserPaymentHistory(userId, 0, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        PaymentHistoryDTO dto = result.getContent().get(0);
        assertThat(dto.getPaymentIntentId()).isEqualTo("pi_test123");
        assertThat(dto.getAmount()).isEqualTo(new BigDecimal("9.99"));
        assertThat(dto.getCurrency()).isEqualTo("USD");
        assertThat(dto.getStatus()).isEqualTo("pending");
        assertThat(dto.getToolName()).isEqualTo("Test Tool");
    }

    @Test
    void getPaymentById_WithValidId_ShouldReturnPayment() {
        // Arrange
        UUID paymentId = testPayment.getId();
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(testPayment));

        // Act
        Payment result = paymentService.getPaymentById(paymentId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(paymentId);
        assertThat(result.getPaymentIntentId()).isEqualTo("pi_test123");
    }

    @Test
    void getPaymentById_WithInvalidId_ShouldThrowException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(paymentRepository.findById(invalidId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> paymentService.getPaymentById(invalidId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void handleSuccessfulPayment_WithExistingSubscription_ShouldLinkPayment() {
        // Arrange
        String paymentIntentId = "pi_test123";
        String chargeId = "ch_test123";

        when(paymentRepository.findByPaymentIntentId(paymentIntentId))
                .thenReturn(Optional.of(testPayment));
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                eq(userId),
                eq(testTool.getId()),
                eq(ToolSubscription.SubscriptionStatus.ACTIVE)
        )).thenReturn(Optional.of(testSubscription));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);

        // Act
        paymentService.handleSuccessfulPayment(paymentIntentId, chargeId);

        // Assert
        verify(paymentRepository).save(argThat(payment ->
                payment.getSubscription() != null &&
                payment.getSubscription().getId().equals(testSubscription.getId())
        ));
    }

    @Test
    void handleSuccessfulPayment_WithMonthlyTool_ShouldCreateSubscriptionWithCorrectQuota() {
        // Arrange
        testTool.setPricingModel(McpTool.PricingModel.MONTHLY);
        testPayment.setTool(testTool);

        when(paymentRepository.findByPaymentIntentId("pi_test123"))
                .thenReturn(Optional.of(testPayment));
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                any(), any(), any()
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenAnswer(invocation -> {
                    ToolSubscription sub = invocation.getArgument(0);
                    sub.setId(UUID.randomUUID());
                    return sub;
                });
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);

        // Act
        paymentService.handleSuccessfulPayment("pi_test123", "ch_test123");

        // Assert
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getMonthlyQuota() == 10000 &&
                sub.getRemainingQuota() == 10000
        ));
    }

    @Test
    void handleSuccessfulPayment_WithFreeTierTool_ShouldCreateSubscriptionWithCorrectQuota() {
        // Arrange
        testTool.setPricingModel(McpTool.PricingModel.FREE_TIER);
        testPayment.setTool(testTool);

        when(paymentRepository.findByPaymentIntentId("pi_test123"))
                .thenReturn(Optional.of(testPayment));
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                any(), any(), any()
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenAnswer(invocation -> {
                    ToolSubscription sub = invocation.getArgument(0);
                    sub.setId(UUID.randomUUID());
                    return sub;
                });
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(testPayment);

        // Act
        paymentService.handleSuccessfulPayment("pi_test123", "ch_test123");

        // Assert
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getMonthlyQuota() == 100 &&
                sub.getRemainingQuota() == 100
        ));
    }
}
