package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for call flags
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagDTO {
    private UUID id;
    private UUID callId;
    private String flagType;
    private String note;
    private String createdBy;
    private Instant createdAt;
}
