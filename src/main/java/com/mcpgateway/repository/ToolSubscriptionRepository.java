package com.mcpgateway.repository;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolSubscriptionRepository extends JpaRepository<ToolSubscription, UUID> {
    Optional<ToolSubscription> findByClientIdAndToolIdAndStatus(
            UUID clientId,
            UUID toolId,
            ToolSubscription.SubscriptionStatus status
    );
    
    List<ToolSubscription> findByTool(McpTool tool);
} 