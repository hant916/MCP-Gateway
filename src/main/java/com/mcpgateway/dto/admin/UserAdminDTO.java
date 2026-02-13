package com.mcpgateway.dto.admin;

import com.mcpgateway.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
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
    // Note: User entity doesn't have these fields yet - placeholders for future implementation
    private String fullName;
    private String subscriptionTierName; // Simplified from nested class reference
    private Boolean isActive;
    private Boolean emailVerified;
    private Timestamp createdAt; // Changed to match User entity type
    private Timestamp lastLoginAt;

    // Statistics
    private Integer totalSubscriptions;
    private Integer activeSubscriptions;
    private Long totalRequests;
    private String totalSpent;

    /**
     * Convert from User entity
     * Note: Some fields (fullName, subscriptionTierName, etc.) are not yet in User entity
     */
    public static UserAdminDTO from(User user) {
        return UserAdminDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                // Commented out fields not yet present in User entity:
                // .fullName(user.getFullName())
                // .subscriptionTierName(user.getSubscriptionTierName())
                // .isActive(user.getIsActive())
                // .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                // .lastLoginAt(user.getLastLoginAt())
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
