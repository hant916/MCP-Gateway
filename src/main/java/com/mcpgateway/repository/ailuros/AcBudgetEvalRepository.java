package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcBudgetEval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AcBudgetEvalRepository extends JpaRepository<AcBudgetEval, UUID> {

    @Query("""
        SELECT e FROM AcBudgetEval e
        WHERE (:appId IS NULL OR e.appId = :appId)
          AND (:env IS NULL OR e.env = :env)
          AND (:route IS NULL OR e.route = :route)
          AND e.createdTs BETWEEN :from AND :to
        ORDER BY e.createdTs DESC
    """)
    List<AcBudgetEval> findInWindow(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        SELECT COUNT(e) FROM AcBudgetEval e
        WHERE e.appId = :appId
          AND (:env IS NULL OR e.env = :env)
          AND e.createdTs BETWEEN :from AND :to
          AND e.status IN :statuses
    """)
    long countByStatusInWindow(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("statuses") List<AcBudgetEval.Status> statuses
    );
}
