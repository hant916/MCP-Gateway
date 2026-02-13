package com.mcpgateway.dto.ailuros;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a flag on a call
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFlagRequest {

    @NotBlank(message = "Flag type is required")
    @Pattern(regexp = "wrong|risky|review", message = "Flag type must be one of: wrong, risky, review")
    private String flagType;

    private String note;

    private String createdBy;
}
