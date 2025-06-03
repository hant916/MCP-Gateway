package com.mcpgateway.repository;

import com.mcpgateway.domain.ToolUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ToolUsageRecordRepository extends JpaRepository<ToolUsageRecord, UUID> {
    List<ToolUsageRecord> findBySubscriptionClientIdAndUsageTimeBetween(
            UUID clientId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
} 