package com.mcpgateway.service;

import com.mcpgateway.domain.*;
import com.mcpgateway.dto.marketplace.*;
import com.mcpgateway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for marketplace operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final McpToolRepository toolRepository;
    private final ToolCategoryRepository categoryRepository;
    private final ToolReviewRepository reviewRepository;
    private final ToolSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Browse tools in marketplace with filters
     */
    @Transactional(readOnly = true)
    public Page<ToolListingDTO> browseTools(
            UUID categoryId,
            McpTool.PricingModel pricingModel,
            BigDecimal minRating,
            String sortBy,
            int page,
            int size) {

        Pageable pageable = createPageable(page, size, sortBy);

        Page<McpTool> tools;
        if (categoryId != null || pricingModel != null || minRating != null) {
            tools = toolRepository.searchTools(
                    McpTool.ToolStatus.PUBLISHED,
                    categoryId,
                    pricingModel,
                    minRating,
                    pageable
            );
        } else {
            tools = toolRepository.findByStatus(McpTool.ToolStatus.PUBLISHED, pageable);
        }

        return tools.map(ToolListingDTO::from);
    }

    /**
     * Search tools by keyword
     */
    @Transactional(readOnly = true)
    public Page<ToolListingDTO> searchTools(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<McpTool> tools = toolRepository.searchByKeyword(keyword, pageable);
        return tools.map(ToolListingDTO::from);
    }

    /**
     * Get tool detail
     */
    @Transactional(readOnly = true)
    public ToolDetailDTO getToolDetail(UUID toolId) {
        McpTool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new RuntimeException("Tool not found: " + toolId));

        ToolDetailDTO dto = ToolDetailDTO.from(tool);

        // Get recent reviews
        Page<ToolReview> reviews = reviewRepository.findByToolIdAndStatus(
                toolId,
                ToolReview.ReviewStatus.APPROVED,
                PageRequest.of(0, 5, Sort.by("createdAt").descending())
        );
        dto.setRecentReviews(reviews.stream()
                .map(ReviewSummaryDTO::from)
                .collect(Collectors.toList()));

        // Add pricing tiers (example)
        dto.setPricingTiers(generatePricingTiers(tool));

        return dto;
    }

    /**
     * Subscribe to a tool
     */
    @Transactional
    public ToolSubscription subscribeTool(UUID userId, SubscribeToolRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        McpTool tool = toolRepository.findById(request.getToolId())
                .orElseThrow(() -> new RuntimeException("Tool not found"));

        // Check if already subscribed
        boolean alreadySubscribed = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(
                        userId,
                        request.getToolId(),
                        ToolSubscription.SubscriptionStatus.ACTIVE
                )
                .isPresent();

        if (alreadySubscribed) {
            throw new IllegalStateException("Already subscribed to this tool");
        }

        // Create subscription
        ToolSubscription subscription = new ToolSubscription();
        subscription.setTool(tool);
        subscription.setClientId(userId);
        subscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        subscription.setMonthlyQuota(request.getMonthlyQuota());
        subscription.setRemainingQuota(request.getMonthlyQuota());

        ToolSubscription saved = subscriptionRepository.save(subscription);

        // Update subscriber count
        tool.setSubscriberCount(tool.getSubscriberCount() + 1);
        toolRepository.save(tool);

        log.info("User {} subscribed to tool {}", userId, request.getToolId());

        return saved;
    }

    /**
     * Unsubscribe from a tool
     */
    @Transactional
    public void unsubscribeTool(UUID userId, UUID toolId) {
        ToolSubscription subscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(
                        userId,
                        toolId,
                        ToolSubscription.SubscriptionStatus.ACTIVE
                )
                .orElseThrow(() -> new RuntimeException("Active subscription not found"));

        subscription.setStatus(ToolSubscription.SubscriptionStatus.INACTIVE);
        subscriptionRepository.save(subscription);

        // Update subscriber count
        McpTool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new RuntimeException("Tool not found"));
        tool.setSubscriberCount(Math.max(0, tool.getSubscriberCount() - 1));
        toolRepository.save(tool);

        log.info("User {} unsubscribed from tool {}", userId, toolId);
    }

    /**
     * Create a review for a tool
     */
    @Transactional
    public ReviewDTO createReview(UUID userId, UUID toolId, CreateReviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        McpTool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new RuntimeException("Tool not found"));

        // Check if already reviewed
        reviewRepository.findByToolIdAndUserId(toolId, userId).ifPresent(r -> {
            throw new IllegalStateException("You have already reviewed this tool");
        });

        // Check if user has an active subscription (verified purchase)
        boolean hasSubscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE)
                .isPresent();

        ToolReview review = new ToolReview();
        review.setTool(tool);
        review.setUser(user);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        review.setIsVerifiedPurchase(hasSubscription);
        review.setStatus(ToolReview.ReviewStatus.PENDING);

        ToolReview saved = reviewRepository.save(review);

        // Update tool average rating (will be recalculated when review is approved)
        updateToolRating(toolId);

        log.info("User {} created review for tool {}", userId, toolId);

        return ReviewDTO.from(saved);
    }

    /**
     * Get reviews for a tool
     */
    @Transactional(readOnly = true)
    public Page<ReviewDTO> getToolReviews(UUID toolId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ToolReview> reviews = reviewRepository.findByToolIdAndStatus(
                toolId,
                ToolReview.ReviewStatus.APPROVED,
                pageable
        );
        return reviews.map(ReviewDTO::from);
    }

    /**
     * Get all categories
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories() {
        List<ToolCategory> categories = categoryRepository.findByParentIsNullAndIsActiveTrue();
        return categories.stream()
                .map(CategoryDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Get popular tools
     */
    @Transactional(readOnly = true)
    public List<ToolListingDTO> getPopularTools(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<McpTool> tools = toolRepository.findTopPopularTools(pageable);
        return tools.stream()
                .map(ToolListingDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Get top rated tools
     */
    @Transactional(readOnly = true)
    public List<ToolListingDTO> getTopRatedTools(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<McpTool> tools = toolRepository.findTopRatedTools(pageable);
        return tools.stream()
                .map(ToolListingDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Get recent tools
     */
    @Transactional(readOnly = true)
    public List<ToolListingDTO> getRecentTools(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<McpTool> tools = toolRepository.findRecentTools(pageable);
        return tools.stream()
                .map(ToolListingDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Update tool average rating
     */
    private void updateToolRating(UUID toolId) {
        Double averageRating = reviewRepository.getAverageRatingByToolId(toolId);
        Long reviewCount = reviewRepository.getReviewCountByToolId(toolId);

        McpTool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new RuntimeException("Tool not found"));

        tool.setAverageRating(averageRating != null ?
                BigDecimal.valueOf(averageRating) : BigDecimal.ZERO);
        tool.setReviewCount(reviewCount != null ? reviewCount.intValue() : 0);

        toolRepository.save(tool);
    }

    /**
     * Create pageable with sorting
     */
    private Pageable createPageable(int page, int size, String sortBy) {
        Sort sort = switch (sortBy != null ? sortBy : "createdAt") {
            case "popular" -> Sort.by("subscriberCount").descending();
            case "rating" -> Sort.by("averageRating").descending().and(Sort.by("reviewCount").descending());
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            case "name" -> Sort.by("name").ascending();
            default -> Sort.by("createdAt").descending();
        };

        return PageRequest.of(page, size, sort);
    }

    /**
     * Generate example pricing tiers
     */
    private List<ToolDetailDTO.PricingTier> generatePricingTiers(McpTool tool) {
        return switch (tool.getPricingModel()) {
            case MONTHLY -> List.of(
                    ToolDetailDTO.PricingTier.builder()
                            .name("Basic")
                            .price(new BigDecimal("9.99"))
                            .quota(1000)
                            .description("1,000 calls per month")
                            .build(),
                    ToolDetailDTO.PricingTier.builder()
                            .name("Pro")
                            .price(new BigDecimal("49.99"))
                            .quota(10000)
                            .description("10,000 calls per month")
                            .build(),
                    ToolDetailDTO.PricingTier.builder()
                            .name("Enterprise")
                            .price(new BigDecimal("199.99"))
                            .quota(null)
                            .description("Unlimited calls")
                            .build()
            );
            case PAY_AS_YOU_GO -> List.of(
                    ToolDetailDTO.PricingTier.builder()
                            .name("Pay as you go")
                            .price(tool.getPrice())
                            .quota(null)
                            .description("Per call pricing")
                            .build()
            );
            case FREE_TIER -> List.of(
                    ToolDetailDTO.PricingTier.builder()
                            .name("Free")
                            .price(BigDecimal.ZERO)
                            .quota(100)
                            .description("100 calls per month")
                            .build()
            );
        };
    }
}
