package com.mcpgateway.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDTO {
    private UUID id;
    private String name;
    private String description;
    private String parameters;
    private UUID apiSpecificationId;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
} 