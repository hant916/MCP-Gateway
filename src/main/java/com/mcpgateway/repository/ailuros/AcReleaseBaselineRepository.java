package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcReleaseBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcReleaseBaselineRepository extends JpaRepository<AcReleaseBaseline, UUID> {

    @Query("""
        SELECT b FROM AcReleaseBaseline b
        WHERE b.appId = :appId
          AND b.env = :env
          AND (
            (:route IS NULL AND b.route IS NULL) OR b.route = :route
          )
        ORDER BY b.updatedTs DESC
    """)
    Optional<AcReleaseBaseline> findExactBaseline(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route
    );

    List<AcReleaseBaseline> findByAppIdAndEnvAndIsEnabledTrueOrderByUpdatedTsDesc(String appId, String env);

    List<AcReleaseBaseline> findByIsEnabledTrueOrderByUpdatedTsDesc();
}
