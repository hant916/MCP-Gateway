package com.mcpgateway.repository;

import com.mcpgateway.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs by user ID
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    /**
     * Find audit logs by action
     */
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    /**
     * Find audit logs for a specific resource
     */
    Page<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(
            String resourceType, String resourceId, Pageable pageable
    );

    /**
     * Find audit logs within time range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * Find failed operations
     */
    Page<AuditLog> findByStatusOrderByTimestampDesc(AuditLog.Status status, Pageable pageable);

    /**
     * Count operations by user within time range
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId AND a.timestamp > :since")
    long countByUserIdSince(UUID userId, LocalDateTime since);

    /**
     * Find unauthorized access attempts
     */
    @Query("SELECT a FROM AuditLog a WHERE a.status IN ('UNAUTHORIZED', 'FORBIDDEN') AND a.timestamp > :since ORDER BY a.timestamp DESC")
    List<AuditLog> findSecurityEvents(LocalDateTime since);
}
