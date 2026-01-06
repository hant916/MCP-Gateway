package com.mcpgateway.dto.admin;

import com.mcpgateway.domain.ToolSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for quota management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaManagementDTO {

    private UUID subscriptionId;
    private UUID userId;
    private String username;
    private UUID toolId;
    private String toolName;
    private Integer monthlyQuota;
    private Integer remainingQuota;
    private Integer usedQuota;
    private Double usagePercentage;
    private LocalDateTime quotaResetAt;
    private ToolSubscription.SubscriptionStatus status;

    /**
     * Convert from ToolSubscription entity
     */
    public static QuotaManagementDTO from(ToolSubscription subscription) {
        Integer used = subscription.getMonthlyQuota() - subscription.getRemainingQuota();
        Double percentage = subscription.getMonthlyQuota() > 0 ?
                (used * 100.0 / subscription.getMonthlyQuota()) : 0.0;

        return QuotaManagementDTO.builder()
                .subscriptionId(subscription.getId())
                .userId(subscription.getClientId())
                .toolId(subscription.getTool().getId())
                .toolName(subscription.getTool().getName())
                .monthlyQuota(subscription.getMonthlyQuota())
                .remainingQuota(subscription.getRemainingQuota())
                .usedQuota(used)
                .usagePercentage(percentage)
                .quotaResetAt(subscription.getQuotaResetAt())
                .status(subscription.getStatus())
                .build();
    }
}
