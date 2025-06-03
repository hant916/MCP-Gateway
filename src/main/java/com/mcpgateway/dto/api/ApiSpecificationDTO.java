package com.mcpgateway.dto.api;

import com.mcpgateway.domain.ApiSpecification;
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
public class ApiSpecificationDTO {
    private UUID id;
    private String name;
    private String description;
    private ApiSpecification.SpecType specType;
    private String content;
    private String version;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private UUID createdById;
} 