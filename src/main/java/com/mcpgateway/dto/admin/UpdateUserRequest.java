package com.mcpgateway.dto.admin;

import com.mcpgateway.ratelimit.SubscriptionQuotaService;
import com.mcpgateway.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private String subscriptionTierName; // Changed from nested class reference to simple String

    private Boolean isActive;

    private Boolean emailVerified;

    public User.SubscriptionTier getSubscriptionTier() {
        if (subscriptionTierName == null || subscriptionTierName.isBlank()) {
            return null;
        }
        try {
            return User.SubscriptionTier.valueOf(subscriptionTierName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setSubscriptionTier(User.SubscriptionTier subscriptionTier) {
        this.subscriptionTierName = subscriptionTier != null ? subscriptionTier.name() : null;
    }
}
