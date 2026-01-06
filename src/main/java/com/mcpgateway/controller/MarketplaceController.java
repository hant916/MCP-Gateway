package com.mcpgateway.controller;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.marketplace.*;
import com.mcpgateway.ratelimit.RateLimit;
import com.mcpgateway.service.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Controller for marketplace operations
 */
@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
@Tag(name = "Marketplace", description = "Tool marketplace APIs")
@SecurityRequirement(name = "bearerAuth")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/tools")
    @Operation(summary = "Browse tools in marketplace")
    @RateLimit(limit = 1000, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Page<ToolListingDTO>> browseTools(
            @Parameter(description = "Category ID filter") @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Pricing model filter") @RequestParam(required = false) McpTool.PricingModel pricingModel,
            @Parameter(description = "Minimum rating filter") @RequestParam(required = false) BigDecimal minRating,
            @Parameter(description = "Sort by: createdAt, popular, rating, price_asc, price_desc, name") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Page<ToolListingDTO> tools = marketplaceService.browseTools(
                categoryId, pricingModel, minRating, sortBy, page, size
        );

        return ResponseEntity.ok(tools);
    }

    @GetMapping("/tools/search")
    @Operation(summary = "Search tools by keyword")
    @RateLimit(limit = 500, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Page<ToolListingDTO>> searchTools(
            @Parameter(description = "Search keyword") @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ToolListingDTO> tools = marketplaceService.searchTools(keyword, page, size);
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/tools/{toolId}")
    @Operation(summary = "Get tool details")
    @RateLimit(limit = 500, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<ToolDetailDTO> getToolDetail(@PathVariable UUID toolId) {
        ToolDetailDTO tool = marketplaceService.getToolDetail(toolId);
        return ResponseEntity.ok(tool);
    }

    @PostMapping("/tools/{toolId}/subscribe")
    @Operation(summary = "Subscribe to a tool")
    @RateLimit(limit = 50, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<ToolSubscription> subscribeTool(
            @PathVariable UUID toolId,
            @Valid @RequestBody SubscribeToolRequest request,
            @AuthenticationPrincipal User user) {

        request.setToolId(toolId);
        ToolSubscription subscription = marketplaceService.subscribeTool(user.getId(), request);
        return ResponseEntity.ok(subscription);
    }

    @DeleteMapping("/tools/{toolId}/subscribe")
    @Operation(summary = "Unsubscribe from a tool")
    @RateLimit(limit = 50, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Void> unsubscribeTool(
            @PathVariable UUID toolId,
            @AuthenticationPrincipal User user) {

        marketplaceService.unsubscribeTool(user.getId(), toolId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tools/{toolId}/reviews")
    @Operation(summary = "Create a review for a tool")
    @RateLimit(limit = 20, window = 3600, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<ReviewDTO> createReview(
            @PathVariable UUID toolId,
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal User user) {

        ReviewDTO review = marketplaceService.createReview(user.getId(), toolId, request);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/tools/{toolId}/reviews")
    @Operation(summary = "Get reviews for a tool")
    @RateLimit(limit = 500, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Page<ReviewDTO>> getToolReviews(
            @PathVariable UUID toolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ReviewDTO> reviews = marketplaceService.getToolReviews(toolId, page, size);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/categories")
    @Operation(summary = "Get all categories")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<CategoryDTO>> getCategories() {
        List<CategoryDTO> categories = marketplaceService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/tools/popular")
    @Operation(summary = "Get popular tools")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<ToolListingDTO>> getPopularTools(
            @RequestParam(defaultValue = "10") int limit) {

        List<ToolListingDTO> tools = marketplaceService.getPopularTools(limit);
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/tools/top-rated")
    @Operation(summary = "Get top rated tools")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<ToolListingDTO>> getTopRatedTools(
            @RequestParam(defaultValue = "10") int limit) {

        List<ToolListingDTO> tools = marketplaceService.getTopRatedTools(limit);
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/tools/recent")
    @Operation(summary = "Get recently added tools")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<ToolListingDTO>> getRecentTools(
            @RequestParam(defaultValue = "10") int limit) {

        List<ToolListingDTO> tools = marketplaceService.getRecentTools(limit);
        return ResponseEntity.ok(tools);
    }
}
