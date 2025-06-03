package com.mcpgateway.repository;

import com.mcpgateway.domain.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface McpServerRepository extends JpaRepository<McpServer, UUID> {
    List<McpServer> findByUserId(UUID userId);
} 