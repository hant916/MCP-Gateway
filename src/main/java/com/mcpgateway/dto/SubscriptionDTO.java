package com.mcpgateway.dto;

import com.mcpgateway.domain.ToolSubscription;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SubscriptionDTO {
    private UUID id;
    private UUID toolId;
    private UUID clientId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ToolSubscription.SubscriptionStatus status;
    private Integer remainingQuota;
} 