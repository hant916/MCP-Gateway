package com.mcpgateway.repository;

import com.mcpgateway.domain.McpTool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpToolRepository extends JpaRepository<McpTool, UUID> {

    List<McpTool> findByApiSpecificationId(UUID apiSpecificationId);

    Optional<McpTool> findByNameAndApiSpecificationId(String name, UUID apiSpecificationId);

    // Marketplace queries
    Page<McpTool> findByStatus(McpTool.ToolStatus status, Pageable pageable);

    Page<McpTool> findByStatusAndCategoryId(McpTool.ToolStatus status, UUID categoryId, Pageable pageable);

    Page<McpTool> findByStatusAndPricingModel(McpTool.ToolStatus status, McpTool.PricingModel pricingModel, Pageable pageable);

    @Query("SELECT t FROM McpTool t WHERE t.status = :status AND " +
           "(:categoryId IS NULL OR t.category.id = :categoryId) AND " +
           "(:pricingModel IS NULL OR t.pricingModel = :pricingModel) AND " +
           "(:minRating IS NULL OR t.averageRating >= :minRating)")
    Page<McpTool> searchTools(
            @Param("status") McpTool.ToolStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("pricingModel") McpTool.PricingModel pricingModel,
            @Param("minRating") BigDecimal minRating,
            Pageable pageable
    );

    @Query("SELECT t FROM McpTool t WHERE t.status = 'PUBLISHED' AND " +
           "(LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<McpTool> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT t FROM McpTool t WHERE t.status = 'PUBLISHED' ORDER BY t.subscriberCount DESC")
    List<McpTool> findTopPopularTools(Pageable pageable);

    @Query("SELECT t FROM McpTool t WHERE t.status = 'PUBLISHED' ORDER BY t.averageRating DESC, t.reviewCount DESC")
    List<McpTool> findTopRatedTools(Pageable pageable);

    @Query("SELECT t FROM McpTool t WHERE t.status = 'PUBLISHED' ORDER BY t.createdAt DESC")
    List<McpTool> findRecentTools(Pageable pageable);
} 