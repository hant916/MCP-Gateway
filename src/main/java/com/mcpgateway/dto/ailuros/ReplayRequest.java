package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for replaying a call with optional parameter overrides
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayRequest {
    private String model;
    private BigDecimal temperature;
    private BigDecimal topP;
    private String promptRef;
}
