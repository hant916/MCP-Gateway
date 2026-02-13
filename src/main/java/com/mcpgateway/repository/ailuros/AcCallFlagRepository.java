package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcCallFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for call flags
 */
@Repository
public interface AcCallFlagRepository extends JpaRepository<AcCallFlag, UUID> {

    /**
     * Find all flags for a specific call
     */
    List<AcCallFlag> findByCallIdOrderByCreatedAtDesc(UUID callId);

    /**
     * Find flags by type
     */
    Page<AcCallFlag> findByFlagTypeOrderByCreatedAtDesc(String flagType, Pageable pageable);

    /**
     * Find flags by creator
     */
    List<AcCallFlag> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Count flags for a call
     */
    Long countByCallId(UUID callId);

    /**
     * Count flags by type
     */
    @Query("SELECT f.flagType, COUNT(f) FROM AcCallFlag f GROUP BY f.flagType")
    List<Object[]> countByFlagType();

    /**
     * Check if a call has any flags
     */
    boolean existsByCallId(UUID callId);
}
