package com.mcpgateway.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating subscription quota
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuotaRequest {

    @NotNull(message = "Monthly quota is required")
    @Min(value = 0, message = "Monthly quota must be non-negative")
    private Integer monthlyQuota;

    private Boolean resetRemainingQuota;
}
