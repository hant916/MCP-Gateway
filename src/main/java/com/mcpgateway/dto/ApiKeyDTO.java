package com.mcpgateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyDTO {
    private String keyValue;
    private LocalDateTime createdAt;
} 