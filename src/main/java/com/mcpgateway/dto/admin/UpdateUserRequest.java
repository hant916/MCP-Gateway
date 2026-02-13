package com.mcpgateway.dto.admin;

import com.mcpgateway.ratelimit.SubscriptionQuotaService;
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
}
