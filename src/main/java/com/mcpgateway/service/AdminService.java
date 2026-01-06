package com.mcpgateway.service;

import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.admin.QuotaManagementDTO;
import com.mcpgateway.dto.admin.UpdateQuotaRequest;
import com.mcpgateway.dto.admin.UpdateUserRequest;
import com.mcpgateway.dto.admin.UserAdminDTO;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import com.mcpgateway.repository.ToolUsageRecordRepository;
import com.mcpgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for admin operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ToolSubscriptionRepository subscriptionRepository;
    private final ToolUsageRecordRepository usageRecordRepository;

    /**
     * Get all users with pagination
     */
    @Transactional(readOnly = true)
    public Page<UserAdminDTO> getAllUsers(int page, int size, String sortBy) {
        Sort sort = switch (sortBy != null ? sortBy : "createdAt") {
            case "username" -> Sort.by("username").ascending();
            case "email" -> Sort.by("email").ascending();
            case "subscriptionTier" -> Sort.by("subscriptionTier").descending();
            case "lastLoginAt" -> Sort.by("lastLoginAt").descending();
            default -> Sort.by("createdAt").descending();
        };

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> users = userRepository.findAll(pageable);

        return users.map(UserAdminDTO::from);
    }

    /**
     * Get user by ID with detailed statistics
     */
    @Transactional(readOnly = true)
    public UserAdminDTO getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Get user statistics
        List<ToolSubscription> allSubscriptions = subscriptionRepository
                .findAll()
                .stream()
                .filter(s -> s.getClientId().equals(userId))
                .collect(Collectors.toList());

        Integer totalSubscriptions = allSubscriptions.size();
        Integer activeSubscriptions = (int) allSubscriptions.stream()
                .filter(s -> s.getStatus() == ToolSubscription.SubscriptionStatus.ACTIVE)
                .count();

        // Get usage statistics
        LocalDateTime monthStart = LocalDateTime.now().minusMonths(1);
        Long totalRequests = usageRecordRepository.countByCreatedAtBetween(monthStart, LocalDateTime.now());

        BigDecimal totalSpent = usageRecordRepository.getTotalRevenue(monthStart, LocalDateTime.now());

        return UserAdminDTO.fromWithStats(
                user,
                totalSubscriptions,
                activeSubscriptions,
                totalRequests != null ? totalRequests : 0L,
                totalSpent != null ? totalSpent.toString() : "0.00"
        );
    }

    /**
     * Update user details
     */
    @Transactional
    public UserAdminDTO updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (request.getEmail() != null) {
            // Check if email is already taken by another user
            userRepository.findByEmail(request.getEmail())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(userId)) {
                            throw new IllegalArgumentException("Email already in use");
                        }
                    });
            user.setEmail(request.getEmail());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getSubscriptionTier() != null) {
            user.setSubscriptionTier(request.getSubscriptionTier());
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        if (request.getEmailVerified() != null) {
            user.setEmailVerified(request.getEmailVerified());
        }

        User updated = userRepository.save(user);

        log.info("Admin updated user {}: {}", userId, request);

        return UserAdminDTO.from(updated);
    }

    /**
     * Delete user (soft delete by deactivating)
     */
    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setIsActive(false);
        userRepository.save(user);

        // Deactivate all subscriptions
        List<ToolSubscription> subscriptions = subscriptionRepository
                .findAll()
                .stream()
                .filter(s -> s.getClientId().equals(userId))
                .collect(Collectors.toList());

        subscriptions.forEach(s -> {
            s.setStatus(ToolSubscription.SubscriptionStatus.INACTIVE);
            subscriptionRepository.save(s);
        });

        log.info("Admin deleted user {}", userId);
    }

    /**
     * Get all quotas with pagination
     */
    @Transactional(readOnly = true)
    public Page<QuotaManagementDTO> getAllQuotas(int page, int size, String filterBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<ToolSubscription> subscriptions;
        if ("active".equals(filterBy)) {
            subscriptions = subscriptionRepository
                    .findAll(pageable)
                    .map(s -> s.getStatus() == ToolSubscription.SubscriptionStatus.ACTIVE ? s : null);
        } else if ("exceeded".equals(filterBy)) {
            subscriptions = subscriptionRepository
                    .findAll(pageable)
                    .map(s -> s.getRemainingQuota() <= 0 ? s : null);
        } else {
            subscriptions = subscriptionRepository.findAll(pageable);
        }

        return subscriptions.map(QuotaManagementDTO::from);
    }

    /**
     * Get quota by subscription ID
     */
    @Transactional(readOnly = true)
    public QuotaManagementDTO getQuotaById(UUID subscriptionId) {
        ToolSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        return QuotaManagementDTO.from(subscription);
    }

    /**
     * Update quota for a subscription
     */
    @Transactional
    public QuotaManagementDTO updateQuota(UUID subscriptionId, UpdateQuotaRequest request) {
        ToolSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        subscription.setMonthlyQuota(request.getMonthlyQuota());

        if (Boolean.TRUE.equals(request.getResetRemainingQuota())) {
            subscription.setRemainingQuota(request.getMonthlyQuota());
        }

        ToolSubscription updated = subscriptionRepository.save(subscription);

        log.info("Admin updated quota for subscription {}: {}", subscriptionId, request);

        return QuotaManagementDTO.from(updated);
    }

    /**
     * Reset quota for a subscription
     */
    @Transactional
    public QuotaManagementDTO resetQuota(UUID subscriptionId) {
        ToolSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        subscription.setRemainingQuota(subscription.getMonthlyQuota());
        subscription.setQuotaResetAt(LocalDateTime.now().plusMonths(1));

        ToolSubscription updated = subscriptionRepository.save(subscription);

        log.info("Admin reset quota for subscription {}", subscriptionId);

        return QuotaManagementDTO.from(updated);
    }

    /**
     * Get quotas for a specific user
     */
    @Transactional(readOnly = true)
    public List<QuotaManagementDTO> getQuotasByUserId(UUID userId) {
        return subscriptionRepository
                .findAll()
                .stream()
                .filter(s -> s.getClientId().equals(userId))
                .map(QuotaManagementDTO::from)
                .collect(Collectors.toList());
    }
}
