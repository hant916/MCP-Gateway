package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for daily drift count
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyDriftDTO {
    private LocalDate date;
    private Long driftCount;
}
