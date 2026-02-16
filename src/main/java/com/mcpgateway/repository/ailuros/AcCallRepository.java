package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for LLM call audit logs with advanced filtering
 */
@Repository
public interface AcCallRepository extends JpaRepository<AcCall, UUID>, JpaSpecificationExecutor<AcCall> {

    /**
     * Find call by trace ID
     */
    Optional<AcCall> findByTraceId(String traceId);

    /**
     * Find calls by project within time range
     */
    Page<AcCall> findByProjectKeyAndCreatedAtBetween(
        String projectKey, Instant from, Instant to, Pageable pageable);

    /**
     * Find calls by model
     */
    Page<AcCall> findByModel(String model, Pageable pageable);

    /**
     * Find calls by status
     */
    Page<AcCall> findByStatus(String status, Pageable pageable);

    /**
     * Calculate total cost for a project within time range
     */
    @Query("SELECT COALESCE(SUM(c.costEstimateUsd), 0) FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to")
    BigDecimal calculateTotalCost(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Calculate total cost by model within time range
     */
    @Query("SELECT c.model, COALESCE(SUM(c.costEstimateUsd), 0) FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "GROUP BY c.model " +
           "ORDER BY SUM(c.costEstimateUsd) DESC")
    List<Object[]> calculateCostByModel(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Calculate daily cost aggregates
     */
    @Query("SELECT CAST(c.createdAt AS date), " +
           "COALESCE(SUM(c.costEstimateUsd), 0), " +
           "COUNT(c), " +
           "SUM(CASE WHEN c.status = 'error' THEN 1 ELSE 0 END) " +
           "FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "GROUP BY CAST(c.createdAt AS date) " +
           "ORDER BY CAST(c.createdAt AS date)")
    List<Object[]> calculateDailyCost(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Calculate error rate within time range
     */
    @Query("SELECT COUNT(c) FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "AND c.status = 'error'")
    Long countErrors(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Calculate total calls within time range
     */
    @Query("SELECT COUNT(c) FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to")
    Long countTotal(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Get latency values (sorted ascending) for percentile calculations
     */
    @Query("SELECT c.latencyMs FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "AND c.status = 'ok' " +
           "AND c.latencyMs IS NOT NULL " +
           "ORDER BY c.latencyMs ASC")
    List<Integer> findLatencyValues(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Count calls by model in a window (descending).
     */
    @Query("SELECT c.model, COUNT(c) FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "GROUP BY c.model " +
           "ORDER BY COUNT(c) DESC")
    List<Object[]> countByModelInWindow(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Count calls by prompt ref in a window (descending).
     */
    @Query("SELECT c.promptRef, COUNT(c) FROM AcCall c " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "AND c.promptRef IS NOT NULL " +
           "GROUP BY c.promptRef " +
           "ORDER BY COUNT(c) DESC")
    List<Object[]> countByPromptRefInWindow(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * Find flagged calls
     */
    @Query("SELECT DISTINCT c FROM AcCall c JOIN AcCallFlag f ON c.id = f.call.id " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "ORDER BY c.createdAt DESC")
    Page<AcCall> findFlaggedCalls(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable);

    /**
     * Count flagged calls
     */
    @Query("SELECT COUNT(DISTINCT c.id) FROM AcCall c JOIN AcCallFlag f ON c.id = f.call.id " +
           "WHERE c.projectKey = :projectKey " +
           "AND c.createdAt BETWEEN :from AND :to")
    Long countFlaggedCalls(
        @Param("projectKey") String projectKey,
        @Param("from") Instant from,
        @Param("to") Instant to);
}
