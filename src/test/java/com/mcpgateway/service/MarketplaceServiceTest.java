package com.mcpgateway.service;

import com.mcpgateway.domain.*;
import com.mcpgateway.dto.marketplace.*;
import com.mcpgateway.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock
    private McpToolRepository toolRepository;

    @Mock
    private ToolCategoryRepository categoryRepository;

    @Mock
    private ToolReviewRepository reviewRepository;

    @Mock
    private ToolSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MarketplaceService marketplaceService;

    private McpTool testTool;
    private User testUser;
    private ToolCategory testCategory;
    private ToolReview testReview;
    private ToolSubscription testSubscription;

    @BeforeEach
    void setUp() {
        // Setup test data
        testCategory = new ToolCategory();
        testCategory.setId(UUID.randomUUID());
        testCategory.setName("Test Category");
        testCategory.setSlug("test-category");

        testTool = new McpTool();
        testTool.setId(UUID.randomUUID());
        testTool.setName("Test Tool");
        testTool.setDescription("Test Description");
        testTool.setStatus(McpTool.ToolStatus.PUBLISHED);
        testTool.setCategory(testCategory);
        testTool.setPricingModel(McpTool.PricingModel.MONTHLY);
        testTool.setPrice(new BigDecimal("9.99"));
        testTool.setAverageRating(new BigDecimal("4.5"));
        testTool.setReviewCount(10);
        testTool.setSubscriberCount(100);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        testReview = new ToolReview();
        testReview.setId(UUID.randomUUID());
        testReview.setTool(testTool);
        testReview.setUser(testUser);
        testReview.setRating(5);
        testReview.setTitle("Great tool!");
        testReview.setComment("Works perfectly");
        testReview.setStatus(ToolReview.ReviewStatus.APPROVED);
        testReview.setIsVerifiedPurchase(true);

        testSubscription = new ToolSubscription();
        testSubscription.setId(UUID.randomUUID());
        testSubscription.setTool(testTool);
        testSubscription.setClientId(testUser.getId());
        testSubscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setMonthlyQuota(1000);
        testSubscription.setRemainingQuota(500);
    }

    @Test
    void browseTools_WithoutFilters_ShouldReturnPublishedTools() {
        // Arrange
        List<McpTool> tools = Arrays.asList(testTool);
        Page<McpTool> toolPage = new PageImpl<>(tools);

        when(toolRepository.findByStatus(eq(McpTool.ToolStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(toolPage);

        // Act
        Page<ToolListingDTO> result = marketplaceService.browseTools(
                null, null, null, "createdAt", 0, 20
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Test Tool");
        verify(toolRepository).findByStatus(eq(McpTool.ToolStatus.PUBLISHED), any(Pageable.class));
    }

    @Test
    void browseTools_WithFilters_ShouldReturnFilteredTools() {
        // Arrange
        UUID categoryId = testCategory.getId();
        McpTool.PricingModel pricingModel = McpTool.PricingModel.MONTHLY;
        BigDecimal minRating = new BigDecimal("4.0");

        List<McpTool> tools = Arrays.asList(testTool);
        Page<McpTool> toolPage = new PageImpl<>(tools);

        when(toolRepository.searchTools(
                eq(McpTool.ToolStatus.PUBLISHED),
                eq(categoryId),
                eq(pricingModel),
                eq(minRating),
                any(Pageable.class)
        )).thenReturn(toolPage);

        // Act
        Page<ToolListingDTO> result = marketplaceService.browseTools(
                categoryId, pricingModel, minRating, "rating", 0, 20
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(toolRepository).searchTools(
                eq(McpTool.ToolStatus.PUBLISHED),
                eq(categoryId),
                eq(pricingModel),
                eq(minRating),
                any(Pageable.class)
        );
    }

    @Test
    void searchTools_WithKeyword_ShouldReturnMatchingTools() {
        // Arrange
        String keyword = "test";
        List<McpTool> tools = Arrays.asList(testTool);
        Page<McpTool> toolPage = new PageImpl<>(tools);

        when(toolRepository.searchByKeyword(eq(keyword), any(Pageable.class)))
                .thenReturn(toolPage);

        // Act
        Page<ToolListingDTO> result = marketplaceService.searchTools(keyword, 0, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(toolRepository).searchByKeyword(eq(keyword), any(Pageable.class));
    }

    @Test
    void getToolDetail_WithValidId_ShouldReturnToolDetail() {
        // Arrange
        UUID toolId = testTool.getId();
        List<ToolReview> reviews = Arrays.asList(testReview);
        Page<ToolReview> reviewPage = new PageImpl<>(reviews);

        when(toolRepository.findById(toolId)).thenReturn(Optional.of(testTool));
        when(reviewRepository.findByToolIdAndStatus(
                eq(toolId),
                eq(ToolReview.ReviewStatus.APPROVED),
                any(Pageable.class)
        )).thenReturn(reviewPage);

        // Act
        ToolDetailDTO result = marketplaceService.getToolDetail(toolId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Tool");
        assertThat(result.getRecentReviews()).hasSize(1);
        assertThat(result.getPricingTiers()).isNotEmpty();
        verify(toolRepository).findById(toolId);
    }

    @Test
    void getToolDetail_WithInvalidId_ShouldThrowException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(toolRepository.findById(invalidId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> marketplaceService.getToolDetail(invalidId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tool not found");
    }

    @Test
    void subscribeTool_WithValidRequest_ShouldCreateSubscription() {
        // Arrange
        UUID userId = testUser.getId();
        SubscribeToolRequest request = new SubscribeToolRequest();
        request.setToolId(testTool.getId());
        request.setMonthlyQuota(1000);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(toolRepository.findById(testTool.getId())).thenReturn(Optional.of(testTool));
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                eq(userId),
                eq(testTool.getId()),
                eq(ToolSubscription.SubscriptionStatus.ACTIVE)
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);
        when(toolRepository.save(any(McpTool.class))).thenReturn(testTool);

        // Act
        ToolSubscription result = marketplaceService.subscribeTool(userId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(subscriptionRepository).save(any(ToolSubscription.class));
        verify(toolRepository).save(any(McpTool.class));
    }

    @Test
    void subscribeTool_WhenAlreadySubscribed_ShouldThrowException() {
        // Arrange
        UUID userId = testUser.getId();
        SubscribeToolRequest request = new SubscribeToolRequest();
        request.setToolId(testTool.getId());
        request.setMonthlyQuota(1000);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(toolRepository.findById(testTool.getId())).thenReturn(Optional.of(testTool));
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                eq(userId),
                eq(testTool.getId()),
                eq(ToolSubscription.SubscriptionStatus.ACTIVE)
        )).thenReturn(Optional.of(testSubscription));

        // Act & Assert
        assertThatThrownBy(() -> marketplaceService.subscribeTool(userId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Already subscribed");
    }

    @Test
    void unsubscribeTool_WithActiveSubscription_ShouldDeactivateSubscription() {
        // Arrange
        UUID userId = testUser.getId();
        UUID toolId = testTool.getId();

        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                eq(userId),
                eq(toolId),
                eq(ToolSubscription.SubscriptionStatus.ACTIVE)
        )).thenReturn(Optional.of(testSubscription));
        when(toolRepository.findById(toolId)).thenReturn(Optional.of(testTool));
        when(subscriptionRepository.save(any(ToolSubscription.class)))
                .thenReturn(testSubscription);
        when(toolRepository.save(any(McpTool.class))).thenReturn(testTool);

        // Act
        marketplaceService.unsubscribeTool(userId, toolId);

        // Assert
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getStatus() == ToolSubscription.SubscriptionStatus.INACTIVE
        ));
    }

    @Test
    void createReview_WithValidData_ShouldCreateReview() {
        // Arrange
        UUID userId = testUser.getId();
        UUID toolId = testTool.getId();
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(5);
        request.setTitle("Great!");
        request.setComment("Excellent tool");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(toolRepository.findById(toolId)).thenReturn(Optional.of(testTool));
        when(reviewRepository.findByToolIdAndUserId(toolId, userId))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByClientIdAndToolIdAndStatus(
                eq(userId),
                eq(toolId),
                eq(ToolSubscription.SubscriptionStatus.ACTIVE)
        )).thenReturn(Optional.of(testSubscription));
        when(reviewRepository.save(any(ToolReview.class))).thenReturn(testReview);
        when(reviewRepository.getAverageRatingByToolId(toolId)).thenReturn(4.5);
        when(reviewRepository.getReviewCountByToolId(toolId)).thenReturn(11L);
        when(toolRepository.save(any(McpTool.class))).thenReturn(testTool);

        // Act
        ReviewDTO result = marketplaceService.createReview(userId, toolId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(reviewRepository).save(argThat(review ->
                review.getRating() == 5 &&
                review.getIsVerifiedPurchase() == true
        ));
    }

    @Test
    void createReview_WhenAlreadyReviewed_ShouldThrowException() {
        // Arrange
        UUID userId = testUser.getId();
        UUID toolId = testTool.getId();
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(5);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(toolRepository.findById(toolId)).thenReturn(Optional.of(testTool));
        when(reviewRepository.findByToolIdAndUserId(toolId, userId))
                .thenReturn(Optional.of(testReview));

        // Act & Assert
        assertThatThrownBy(() -> marketplaceService.createReview(userId, toolId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void getToolReviews_ShouldReturnApprovedReviews() {
        // Arrange
        UUID toolId = testTool.getId();
        List<ToolReview> reviews = Arrays.asList(testReview);
        Page<ToolReview> reviewPage = new PageImpl<>(reviews);

        when(reviewRepository.findByToolIdAndStatus(
                eq(toolId),
                eq(ToolReview.ReviewStatus.APPROVED),
                any(Pageable.class)
        )).thenReturn(reviewPage);

        // Act
        Page<ReviewDTO> result = marketplaceService.getToolReviews(toolId, 0, 10);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRating()).isEqualTo(5);
    }

    @Test
    void getAllCategories_ShouldReturnTopLevelCategories() {
        // Arrange
        List<ToolCategory> categories = Arrays.asList(testCategory);
        when(categoryRepository.findByParentIsNullAndIsActiveTrue())
                .thenReturn(categories);

        // Act
        List<CategoryDTO> result = marketplaceService.getAllCategories();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Category");
    }

    @Test
    void getPopularTools_ShouldReturnToolsBySubscriberCount() {
        // Arrange
        List<McpTool> tools = Arrays.asList(testTool);
        when(toolRepository.findTopPopularTools(any(Pageable.class)))
                .thenReturn(tools);

        // Act
        List<ToolListingDTO> result = marketplaceService.getPopularTools(10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubscriberCount()).isEqualTo(100);
    }

    @Test
    void getTopRatedTools_ShouldReturnToolsByRating() {
        // Arrange
        List<McpTool> tools = Arrays.asList(testTool);
        when(toolRepository.findTopRatedTools(any(Pageable.class)))
                .thenReturn(tools);

        // Act
        List<ToolListingDTO> result = marketplaceService.getTopRatedTools(10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAverageRating()).isEqualTo(new BigDecimal("4.5"));
    }

    @Test
    void getRecentTools_ShouldReturnToolsByCreationDate() {
        // Arrange
        List<McpTool> tools = Arrays.asList(testTool);
        when(toolRepository.findRecentTools(any(Pageable.class)))
                .thenReturn(tools);

        // Act
        List<ToolListingDTO> result = marketplaceService.getRecentTools(10);

        // Assert
        assertThat(result).hasSize(1);
        verify(toolRepository).findRecentTools(any(Pageable.class));
    }
}
