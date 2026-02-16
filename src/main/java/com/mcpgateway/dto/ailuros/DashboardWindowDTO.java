package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Shared dashboard window metadata for coherence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWindowDTO {
    private Instant start;
    private Instant end;
    private String timezone;
    private String granularity;
}
