package com.mcpgateway.dto.admin;

import com.mcpgateway.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for user administration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminDTO {

    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private User.SubscriptionTier subscriptionTier;
    private Boolean isActive;
    private Boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    // Statistics
    private Integer totalSubscriptions;
    private Integer activeSubscriptions;
    private Long totalRequests;
    private String totalSpent;

    /**
     * Convert from User entity
     */
    public static UserAdminDTO from(User user) {
        return UserAdminDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .subscriptionTier(user.getSubscriptionTier())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /**
     * Convert from User entity with statistics
     */
    public static UserAdminDTO fromWithStats(
            User user,
            Integer totalSubscriptions,
            Integer activeSubscriptions,
            Long totalRequests,
            String totalSpent) {

        UserAdminDTO dto = from(user);
        dto.setTotalSubscriptions(totalSubscriptions);
        dto.setActiveSubscriptions(activeSubscriptions);
        dto.setTotalRequests(totalRequests);
        dto.setTotalSpent(totalSpent);
        return dto;
    }
}
