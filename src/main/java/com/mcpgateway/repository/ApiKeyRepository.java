package com.mcpgateway.repository;

import com.mcpgateway.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyValue(String keyValue);
    Optional<ApiKey> findByUserId(UUID userId);
} 