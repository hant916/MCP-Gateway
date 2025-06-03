package com.mcpgateway.controller;

import com.mcpgateway.dto.api.ApiSpecificationDTO;
import com.mcpgateway.dto.api.CreateApiSpecificationRequest;
import com.mcpgateway.service.ApiSpecificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api-specifications")
@RequiredArgsConstructor
@Tag(name = "API Specifications", description = "API specification management APIs")
@SecurityRequirement(name = "bearerAuth")
public class ApiSpecificationController {

    private final ApiSpecificationService apiSpecificationService;

    @PostMapping
    @Operation(summary = "Create a new API specification")
    public ResponseEntity<ApiSpecificationDTO> createApiSpecification(
            @Valid @RequestBody CreateApiSpecificationRequest request
    ) {
        return ResponseEntity.ok(apiSpecificationService.createApiSpecification(request));
    }

    @GetMapping
    @Operation(summary = "Get all API specifications for the current user")
    public ResponseEntity<List<ApiSpecificationDTO>> getUserApiSpecifications() {
        return ResponseEntity.ok(apiSpecificationService.getUserApiSpecifications());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an API specification by ID")
    public ResponseEntity<ApiSpecificationDTO> getApiSpecification(@PathVariable UUID id) {
        return ResponseEntity.ok(apiSpecificationService.getApiSpecification(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an API specification")
    public ResponseEntity<Void> deleteApiSpecification(@PathVariable UUID id) {
        apiSpecificationService.deleteApiSpecification(id);
        return ResponseEntity.ok().build();
    }
} 