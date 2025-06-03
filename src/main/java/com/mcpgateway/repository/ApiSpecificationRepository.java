package com.mcpgateway.repository;

import com.mcpgateway.domain.ApiSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiSpecificationRepository extends JpaRepository<ApiSpecification, UUID> {
    List<ApiSpecification> findByCreatedById(UUID createdById);
    Optional<ApiSpecification> findByNameAndVersion(String name, String version);
    List<ApiSpecification> findBySpecType(ApiSpecification.SpecType specType);
} 