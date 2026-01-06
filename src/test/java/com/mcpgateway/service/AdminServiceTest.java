package com.mcpgateway.service;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.admin.QuotaManagementDTO;
import com.mcpgateway.dto.admin.UpdateQuotaRequest;
import com.mcpgateway.dto.admin.UpdateUserRequest;
import com.mcpgateway.dto.admin.UserAdminDTO;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import com.mcpgateway.repository.ToolUsageRecordRepository;
import com.mcpgateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ToolSubscriptionRepository subscriptionRepository;

    @Mock
    private ToolUsageRecordRepository usageRecordRepository;

    @InjectMocks
    private AdminService adminService;

    private User testUser;
    private ToolSubscription testSubscription;
    private McpTool testTool;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setSubscriptionTier(User.SubscriptionTier.BASIC);
        testUser.setIsActive(true);
        testUser.setEmailVerified(true);

        testTool = new McpTool();
        testTool.setId(UUID.randomUUID());
        testTool.setName("Test Tool");
        testTool.setPricingModel(McpTool.PricingModel.MONTHLY);

        testSubscription = new ToolSubscription();
        testSubscription.setId(UUID.randomUUID());
        testSubscription.setTool(testTool);
        testSubscription.setClientId(testUser.getId());
        testSubscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setMonthlyQuota(1000);
        testSubscription.setRemainingQuota(500);
        testSubscription.setQuotaResetAt(LocalDateTime.now().plusMonths(1));
    }

    @Test
    void getAllUsers_ShouldReturnPagedUsers() {
        // Arrange
        Page<User> userPage = new PageImpl<>(Arrays.asList(testUser));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

        // Act
        Page<UserAdminDTO> result = adminService.getAllUsers(0, 20, "createdAt");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("testuser");
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    void getUserById_ShouldReturnUserWithStatistics() {
        // Arrange
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(subscriptionRepository.findAll()).thenReturn(Arrays.asList(testSubscription));
        when(usageRecordRepository.countByCreatedAtBetween(any(), any()))
                .thenReturn(100L);
        when(usageRecordRepository.getTotalRevenue(any(), any()))
                .thenReturn(new BigDecimal("50.00"));

        // Act
        UserAdminDTO result = adminService.getUserById(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getTotalSubscriptions()).isEqualTo(1);
        assertThat(result.getActiveSubscriptions()).isEqualTo(1);
        assertThat(result.getTotalRequests()).isEqualTo(100L);
        assertThat(result.getTotalSpent()).isEqualTo("50.00");
    }

    @Test
    void getUserById_WithInvalidId_ShouldThrowException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(userRepository.findById(invalidId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> adminService.getUserById(invalidId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateUser_ShouldUpdateUserFields() {
        // Arrange
        UUID userId = testUser.getId();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("newemail@example.com");
        request.setFullName("New Name");
        request.setSubscriptionTier(User.SubscriptionTier.PRO);
        request.setIsActive(false);
        request.setEmailVerified(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("newemail@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        UserAdminDTO result = adminService.updateUser(userId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("newemail@example.com") &&
                user.getFullName().equals("New Name") &&
                user.getSubscriptionTier() == User.SubscriptionTier.PRO &&
                !user.getIsActive() &&
                !user.getEmailVerified()
        ));
    }

    @Test
    void updateUser_WithDuplicateEmail_ShouldThrowException() {
        // Arrange
        UUID userId = testUser.getId();
        UUID otherUserId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setEmail("existing@example.com");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("existing@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(otherUser));

        // Act & Assert
        assertThatThrownBy(() -> adminService.updateUser(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void updateUser_WithSameEmailForSameUser_ShouldNotThrowException() {
        // Arrange
        UUID userId = testUser.getId();
        testUser.setEmail("test@example.com");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("test@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        UserAdminDTO result = adminService.updateUser(userId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void deleteUser_ShouldDeactivateUserAndSubscriptions() {
        // Arrange
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(subscriptionRepository.findAll()).thenReturn(Arrays.asList(testSubscription));
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);

        // Act
        adminService.deleteUser(userId);

        // Assert
        verify(userRepository).save(argThat(user -> !user.getIsActive()));
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getStatus() == ToolSubscription.SubscriptionStatus.INACTIVE
        ));
    }

    @Test
    void getAllQuotas_WithoutFilter_ShouldReturnAllQuotas() {
        // Arrange
        Page<ToolSubscription> subPage = new PageImpl<>(Arrays.asList(testSubscription));
        when(subscriptionRepository.findAll(any(Pageable.class))).thenReturn(subPage);

        // Act
        Page<QuotaManagementDTO> result = adminService.getAllQuotas(0, 20, "all");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(subscriptionRepository).findAll(any(Pageable.class));
    }

    @Test
    void getQuotaById_ShouldReturnQuotaDetails() {
        // Arrange
        UUID subscriptionId = testSubscription.getId();
        when(subscriptionRepository.findById(subscriptionId))
                .thenReturn(Optional.of(testSubscription));

        // Act
        QuotaManagementDTO result = adminService.getQuotaById(subscriptionId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.getMonthlyQuota()).isEqualTo(1000);
        assertThat(result.getRemainingQuota()).isEqualTo(500);
        assertThat(result.getUsedQuota()).isEqualTo(500);
        assertThat(result.getUsagePercentage()).isEqualTo(50.0);
    }

    @Test
    void getQuotaById_WithInvalidId_ShouldThrowException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(subscriptionRepository.findById(invalidId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> adminService.getQuotaById(invalidId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Subscription not found");
    }

    @Test
    void updateQuota_WithoutReset_ShouldUpdateMonthlyQuotaOnly() {
        // Arrange
        UUID subscriptionId = testSubscription.getId();
        UpdateQuotaRequest request = new UpdateQuotaRequest();
        request.setMonthlyQuota(2000);
        request.setResetRemainingQuota(false);

        when(subscriptionRepository.findById(subscriptionId))
                .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);

        // Act
        QuotaManagementDTO result = adminService.updateQuota(subscriptionId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getMonthlyQuota() == 2000 &&
                sub.getRemainingQuota() == 500 // Should not change
        ));
    }

    @Test
    void updateQuota_WithReset_ShouldUpdateBothQuotas() {
        // Arrange
        UUID subscriptionId = testSubscription.getId();
        UpdateQuotaRequest request = new UpdateQuotaRequest();
        request.setMonthlyQuota(2000);
        request.setResetRemainingQuota(true);

        when(subscriptionRepository.findById(subscriptionId))
                .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);

        // Act
        QuotaManagementDTO result = adminService.updateQuota(subscriptionId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getMonthlyQuota() == 2000 &&
                sub.getRemainingQuota() == 2000 // Should be reset
        ));
    }

    @Test
    void resetQuota_ShouldResetRemainingQuotaAndResetDate() {
        // Arrange
        UUID subscriptionId = testSubscription.getId();
        when(subscriptionRepository.findById(subscriptionId))
                .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);

        // Act
        QuotaManagementDTO result = adminService.resetQuota(subscriptionId);

        // Assert
        assertThat(result).isNotNull();
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getRemainingQuota().equals(sub.getMonthlyQuota()) &&
                sub.getQuotaResetAt() != null
        ));
    }

    @Test
    void getQuotasByUserId_ShouldReturnUserQuotas() {
        // Arrange
        UUID userId = testUser.getId();
        when(subscriptionRepository.findAll())
                .thenReturn(Arrays.asList(testSubscription));

        // Act
        var result = adminService.getQuotasByUserId(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void getQuotasByUserId_WithNoSubscriptions_ShouldReturnEmptyList() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findAll())
                .thenReturn(Collections.emptyList());

        // Act
        var result = adminService.getQuotasByUserId(userId);

        // Assert
        assertThat(result).isEmpty();
    }
}
