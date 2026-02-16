package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcBudgetPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcBudgetPolicyRepository extends JpaRepository<AcBudgetPolicy, UUID> {

    List<AcBudgetPolicy> findByAppIdAndEnvOrderByUpdatedTsDesc(String appId, String env);

    List<AcBudgetPolicy> findByIsEnabledTrueOrderByUpdatedTsDesc();

    @Query("""
        SELECT p FROM AcBudgetPolicy p
        WHERE p.appId = :appId
          AND p.env = :env
          AND (
            (:route IS NULL AND p.route IS NULL) OR p.route = :route
          )
        ORDER BY p.updatedTs DESC
    """)
    Optional<AcBudgetPolicy> findExactPolicy(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route
    );

    @Query("""
        SELECT p FROM AcBudgetPolicy p
        WHERE p.isEnabled = true
          AND (:appId IS NULL OR p.appId = :appId)
          AND (:env IS NULL OR p.env = :env)
          AND (:route IS NULL OR p.route = :route OR p.route IS NULL)
        ORDER BY p.updatedTs DESC
    """)
    List<AcBudgetPolicy> findEnabledPolicies(
        @Param("appId") String appId,
        @Param("env") String env,
        @Param("route") String route
    );
}
