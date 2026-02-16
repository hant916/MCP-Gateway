package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcRegressionSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcRegressionSuiteRepository extends JpaRepository<AcRegressionSuite, UUID> {

    @Query("""
        SELECT s FROM AcRegressionSuite s
        WHERE s.isActive = true
          AND s.appId = :appId
          AND s.env = :env
          AND (
            (:route IS NULL AND s.route IS NULL) OR s.route = :route
          )
        ORDER BY s.updatedTs DESC
    """)
    Optional<AcRegressionSuite> findActiveSuite(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route
    );
}
