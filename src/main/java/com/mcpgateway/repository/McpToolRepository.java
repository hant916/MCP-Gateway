package com.mcpgateway.repository;

import com.mcpgateway.domain.McpTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpToolRepository extends JpaRepository<McpTool, UUID> {
    List<McpTool> findByApiSpecificationId(UUID apiSpecificationId);
    Optional<McpTool> findByNameAndApiSpecificationId(String name, UUID apiSpecificationId);
} 