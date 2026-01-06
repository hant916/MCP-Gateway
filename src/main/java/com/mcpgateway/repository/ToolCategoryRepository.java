package com.mcpgateway.repository;

import com.mcpgateway.domain.ToolCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolCategoryRepository extends JpaRepository<ToolCategory, UUID> {

    Optional<ToolCategory> findBySlug(String slug);

    List<ToolCategory> findByIsActiveTrue();

    List<ToolCategory> findByParentIsNullAndIsActiveTrue();

    @Query("SELECT c FROM ToolCategory c WHERE c.parent.id = :parentId AND c.isActive = true")
    List<ToolCategory> findByParentIdAndIsActiveTrue(UUID parentId);
}
