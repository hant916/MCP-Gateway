package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcRegressionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AcRegressionRunRepository extends JpaRepository<AcRegressionRun, UUID> {

    @Query("""
        SELECT r FROM AcRegressionRun r
        WHERE r.appId = :appId
          AND (:env IS NULL OR r.env = :env)
          AND (:route IS NULL OR r.route = :route)
          AND r.createdTs BETWEEN :from AND :to
        ORDER BY r.createdTs DESC
    """)
    List<AcRegressionRun> findInWindow(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        SELECT r FROM AcRegressionRun r
        WHERE r.appId = :appId
          AND r.env = :env
          AND (:route IS NULL OR r.route = :route)
        ORDER BY r.createdTs DESC
    """)
    List<AcRegressionRun> findLatestByDimension(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route
    );

    @Query("""
        SELECT COUNT(r) FROM AcRegressionRun r
        WHERE r.appId = :appId
          AND r.env = :env
          AND (
            (:route IS NULL AND r.route IS NULL) OR r.route = :route
          )
          AND r.candidateModel = :candidateModel
          AND r.candidatePromptVersion = :candidatePromptVersion
          AND r.status IN :activeStatuses
    """)
    long countActiveForCandidate(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route,
        @Param("candidateModel") String candidateModel,
        @Param("candidatePromptVersion") String candidatePromptVersion,
        @Param("activeStatuses") List<AcRegressionRun.Status> activeStatuses
    );
}
