package com.mcpgateway.dto.api;

import com.mcpgateway.domain.ApiSpecification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiSpecificationRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Spec type is required")
    private ApiSpecification.SpecType specType;
    
    @NotBlank(message = "Content is required")
    private String content;
    
    @NotBlank(message = "Version is required")
    private String version;
} 