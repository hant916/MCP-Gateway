package com.mcpgateway.controller;

import com.mcpgateway.domain.ApiKey;
import com.mcpgateway.dto.ApiKeyDTO;
import com.mcpgateway.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    @PostMapping("/generate")
    //@PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiKeyDTO> generateApiKey(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        ApiKey apiKey = apiKeyService.generateApiKeyByUsername(userDetails.getUsername());
        return ResponseEntity.ok(convertToDTO(apiKey));
    }

    private ApiKeyDTO convertToDTO(ApiKey apiKey) {
        return ApiKeyDTO.builder()
                .keyValue(apiKey.getKeyValue())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
} 