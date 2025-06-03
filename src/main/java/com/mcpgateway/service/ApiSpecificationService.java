package com.mcpgateway.service;

import com.mcpgateway.domain.ApiSpecification;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.api.ApiSpecificationDTO;
import com.mcpgateway.dto.api.CreateApiSpecificationRequest;
import com.mcpgateway.repository.ApiSpecificationRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiSpecificationService {
    private final ApiSpecificationRepository apiSpecificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public ApiSpecificationDTO createApiSpecification(CreateApiSpecificationRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        ApiSpecification apiSpec = new ApiSpecification();
        apiSpec.setName(request.getName());
        apiSpec.setDescription(request.getDescription());
        apiSpec.setSpecType(request.getSpecType());
        apiSpec.setContent(request.getContent());
        apiSpec.setVersion(request.getVersion());
        apiSpec.setCreatedBy(user);

        ApiSpecification savedSpec = apiSpecificationRepository.save(apiSpec);
        return mapToDTO(savedSpec);
    }

    @Transactional(readOnly = true)
    public List<ApiSpecificationDTO> getUserApiSpecifications() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        return apiSpecificationRepository.findByCreatedById(user.getId())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiSpecificationDTO getApiSpecification(UUID id) {
        return apiSpecificationRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new IllegalArgumentException("API Specification not found"));
    }

    @Transactional
    public void deleteApiSpecification(UUID id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        ApiSpecification apiSpec = apiSpecificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API Specification not found"));

        if (!apiSpec.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("API Specification does not belong to current user");
        }

        apiSpecificationRepository.delete(apiSpec);
    }

    private ApiSpecificationDTO mapToDTO(ApiSpecification apiSpec) {
        return ApiSpecificationDTO.builder()
                .id(apiSpec.getId())
                .name(apiSpec.getName())
                .description(apiSpec.getDescription())
                .specType(apiSpec.getSpecType())
                .content(apiSpec.getContent())
                .version(apiSpec.getVersion())
                .createdAt(TimeUtil.toZonedDateTime(apiSpec.getCreatedAt()))
                .updatedAt(TimeUtil.toZonedDateTime(apiSpec.getUpdatedAt()))
                .createdById(apiSpec.getCreatedBy().getId())
                .build();
    }
} 