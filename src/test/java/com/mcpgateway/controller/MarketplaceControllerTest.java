package com.mcpgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.marketplace.*;
import com.mcpgateway.service.MarketplaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketplaceController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security for testing
class MarketplaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MarketplaceService marketplaceService;

    private ToolListingDTO testToolListing;
    private ToolDetailDTO testToolDetail;
    private ReviewDTO testReview;
    private CategoryDTO testCategory;

    @BeforeEach
    void setUp() {
        UUID toolId = UUID.randomUUID();

        testToolListing = ToolListingDTO.builder()
                .id(toolId)
                .name("Test Tool")
                .description("Test description")
                .pricingModel(McpTool.PricingModel.MONTHLY)
                .price(new BigDecimal("9.99"))
                .averageRating(new BigDecimal("4.5"))
                .reviewCount(10)
                .subscriberCount(100)
                .build();

        testToolDetail = ToolDetailDTO.builder()
                .id(toolId)
                .name("Test Tool")
                .description("Test description")
                .longDescription("Long test description")
                .pricingModel(McpTool.PricingModel.MONTHLY)
                .price(new BigDecimal("9.99"))
                .averageRating(new BigDecimal("4.5"))
                .reviewCount(10)
                .subscriberCount(100)
                .recentReviews(Collections.emptyList())
                .pricingTiers(Collections.emptyList())
                .build();

        testReview = ReviewDTO.builder()
                .id(UUID.randomUUID())
                .toolId(toolId)
                .toolName("Test Tool")
                .userId(UUID.randomUUID())
                .username("testuser")
                .rating(5)
                .title("Great tool!")
                .comment("Works perfectly")
                .isVerifiedPurchase(true)
                .status(com.mcpgateway.domain.ToolReview.ReviewStatus.APPROVED)
                .build();

        testCategory = CategoryDTO.builder()
                .id(UUID.randomUUID())
                .name("Test Category")
                .slug("test-category")
                .description("Test category description")
                .build();
    }

    @Test
    @WithMockUser
    void browseTools_ShouldReturnToolList() throws Exception {
        // Arrange
        Page<ToolListingDTO> toolPage = new PageImpl<>(Arrays.asList(testToolListing));
        when(marketplaceService.browseTools(any(), any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(toolPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Test Tool"))
                .andExpect(jsonPath("$.content[0].price").value(9.99));
    }

    @Test
    @WithMockUser
    void searchTools_ShouldReturnMatchingTools() throws Exception {
        // Arrange
        Page<ToolListingDTO> toolPage = new PageImpl<>(Arrays.asList(testToolListing));
        when(marketplaceService.searchTools(eq("test"), anyInt(), anyInt()))
                .thenReturn(toolPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools/search")
                        .param("keyword", "test")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Test Tool"));
    }

    @Test
    @WithMockUser
    void getToolDetail_ShouldReturnToolDetails() throws Exception {
        // Arrange
        UUID toolId = testToolDetail.getId();
        when(marketplaceService.getToolDetail(toolId))
                .thenReturn(testToolDetail);

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools/{toolId}", toolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Tool"))
                .andExpect(jsonPath("$.longDescription").value("Long test description"));
    }

    @Test
    @WithMockUser
    void subscribeTool_WithValidRequest_ShouldReturnSubscription() throws Exception {
        // Arrange
        UUID toolId = UUID.randomUUID();
        SubscribeToolRequest request = new SubscribeToolRequest();
        request.setMonthlyQuota(1000);

        ToolSubscription subscription = new ToolSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setMonthlyQuota(1000);

        when(marketplaceService.subscribeTool(any(UUID.class), any(SubscribeToolRequest.class)))
                .thenReturn(subscription);

        // Act & Assert
        mockMvc.perform(post("/api/v1/marketplace/tools/{toolId}/subscribe", toolId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyQuota").value(1000));
    }

    @Test
    @WithMockUser
    void unsubscribeTool_ShouldReturnSuccess() throws Exception {
        // Arrange
        UUID toolId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(delete("/api/v1/marketplace/tools/{toolId}/subscribe", toolId)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void createReview_WithValidRequest_ShouldReturnReview() throws Exception {
        // Arrange
        UUID toolId = UUID.randomUUID();
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(5);
        request.setTitle("Great!");
        request.setComment("Excellent tool");

        when(marketplaceService.createReview(any(UUID.class), eq(toolId), any(CreateReviewRequest.class)))
                .thenReturn(testReview);

        // Act & Assert
        mockMvc.perform(post("/api/v1/marketplace/tools/{toolId}/reviews", toolId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.title").value("Great tool!"));
    }

    @Test
    @WithMockUser
    void getToolReviews_ShouldReturnReviewList() throws Exception {
        // Arrange
        UUID toolId = UUID.randomUUID();
        Page<ReviewDTO> reviewPage = new PageImpl<>(Arrays.asList(testReview));

        when(marketplaceService.getToolReviews(eq(toolId), anyInt(), anyInt()))
                .thenReturn(reviewPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools/{toolId}/reviews", toolId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].rating").value(5));
    }

    @Test
    @WithMockUser
    void getCategories_ShouldReturnCategoryList() throws Exception {
        // Arrange
        when(marketplaceService.getAllCategories())
                .thenReturn(Arrays.asList(testCategory));

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Test Category"));
    }

    @Test
    @WithMockUser
    void getPopularTools_ShouldReturnPopularToolsList() throws Exception {
        // Arrange
        when(marketplaceService.getPopularTools(anyInt()))
                .thenReturn(Arrays.asList(testToolListing));

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools/popular")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].subscriberCount").value(100));
    }

    @Test
    @WithMockUser
    void getTopRatedTools_ShouldReturnTopRatedList() throws Exception {
        // Arrange
        when(marketplaceService.getTopRatedTools(anyInt()))
                .thenReturn(Arrays.asList(testToolListing));

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools/top-rated")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].averageRating").value(4.5));
    }

    @Test
    @WithMockUser
    void getRecentTools_ShouldReturnRecentToolsList() throws Exception {
        // Arrange
        when(marketplaceService.getRecentTools(anyInt()))
                .thenReturn(Arrays.asList(testToolListing));

        // Act & Assert
        mockMvc.perform(get("/api/v1/marketplace/tools/recent")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Test Tool"));
    }
}
