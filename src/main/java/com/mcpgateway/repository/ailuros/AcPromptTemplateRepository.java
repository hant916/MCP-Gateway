package com.mcpgateway.repository.ailuros;

import com.mcpgateway.domain.ailuros.AcPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing prompt templates
 */
@Repository
public interface AcPromptTemplateRepository extends JpaRepository<AcPromptTemplate, UUID> {

    /**
     * Find a template by project, name, and version
     */
    Optional<AcPromptTemplate> findByProjectKeyAndNameAndVersion(
        String projectKey, String name, Integer version);

    /**
     * Find all versions of a template
     */
    List<AcPromptTemplate> findByProjectKeyAndNameOrderByVersionDesc(
        String projectKey, String name);

    /**
     * Find the latest version of a template
     */
    @Query("SELECT pt FROM AcPromptTemplate pt WHERE pt.projectKey = :projectKey " +
           "AND pt.name = :name ORDER BY pt.version DESC LIMIT 1")
    Optional<AcPromptTemplate> findLatestVersion(
        @Param("projectKey") String projectKey,
        @Param("name") String name);

    /**
     * Find templates by content hash (for detecting duplicates)
     */
    List<AcPromptTemplate> findByContentSha256(String contentSha256);

    /**
     * Find all templates for a project
     */
    List<AcPromptTemplate> findByProjectKeyOrderByCreatedAtDesc(String projectKey);
}
