package com.mcpgateway.repository;

import com.mcpgateway.domain.ToolCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolCallRecordRepository extends JpaRepository<ToolCallRecord, UUID> {
    Optional<ToolCallRecord> findByRequestId(String requestId);
    
    List<ToolCallRecord> findByToolIdAndStartTimeBetween(
            UUID toolId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    List<ToolCallRecord> findByUserIdAndStartTimeBetween(
            UUID userId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
} 