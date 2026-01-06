package com.mcpgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.admin.QuotaManagementDTO;
import com.mcpgateway.dto.admin.UpdateQuotaRequest;
import com.mcpgateway.dto.admin.UpdateUserRequest;
import com.mcpgateway.dto.admin.UserAdminDTO;
import com.mcpgateway.service.AdminService;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    private UserAdminDTO testUserDTO;
    private QuotaManagementDTO testQuotaDTO;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();

        testUserDTO = UserAdminDTO.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .subscriptionTier(User.SubscriptionTier.BASIC)
                .isActive(true)
                .emailVerified(true)
                .totalSubscriptions(2)
                .activeSubscriptions(1)
                .totalRequests(100L)
                .totalSpent("50.00")
                .build();

        testQuotaDTO = QuotaManagementDTO.builder()
                .subscriptionId(UUID.randomUUID())
                .userId(userId)
                .username("testuser")
                .toolId(UUID.randomUUID())
                .toolName("Test Tool")
                .monthlyQuota(1000)
                .remainingQuota(500)
                .usedQuota(500)
                .usagePercentage(50.0)
                .status(ToolSubscription.SubscriptionStatus.ACTIVE)
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_ShouldReturnUserList() throws Exception {
        // Arrange
        Page<UserAdminDTO> userPage = new PageImpl<>(Arrays.asList(testUserDTO));
        when(adminService.getAllUsers(anyInt(), anyInt(), anyString()))
                .thenReturn(userPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].username").value("testuser"))
                .andExpect(jsonPath("$.content[0].totalSubscriptions").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_ShouldReturnUserDetails() throws Exception {
        // Arrange
        UUID userId = testUserDTO.getId();
        when(adminService.getUserById(userId))
                .thenReturn(testUserDTO);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.totalSpent").value("50.00"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_WithValidRequest_ShouldReturnUpdatedUser() throws Exception {
        // Arrange
        UUID userId = testUserDTO.getId();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("newemail@example.com");
        request.setSubscriptionTier(User.SubscriptionTier.PRO);

        UserAdminDTO updatedUser = UserAdminDTO.builder()
                .id(userId)
                .username("testuser")
                .email("newemail@example.com")
                .subscriptionTier(User.SubscriptionTier.PRO)
                .build();

        when(adminService.updateUser(eq(userId), any(UpdateUserRequest.class)))
                .thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/api/v1/admin/users/{userId}", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newemail@example.com"))
                .andExpect(jsonPath("$.subscriptionTier").value("PRO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_ShouldReturnSuccess() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(delete("/api/v1/admin/users/{userId}", userId)
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(adminService).deleteUser(userId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllQuotas_ShouldReturnQuotaList() throws Exception {
        // Arrange
        Page<QuotaManagementDTO> quotaPage = new PageImpl<>(Arrays.asList(testQuotaDTO));
        when(adminService.getAllQuotas(anyInt(), anyInt(), anyString()))
                .thenReturn(quotaPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/quotas")
                        .param("page", "0")
                        .param("size", "20")
                        .param("filterBy", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].monthlyQuota").value(1000))
                .andExpect(jsonPath("$.content[0].usagePercentage").value(50.0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getQuotaById_ShouldReturnQuotaDetails() throws Exception {
        // Arrange
        UUID subscriptionId = testQuotaDTO.getSubscriptionId();
        when(adminService.getQuotaById(subscriptionId))
                .thenReturn(testQuotaDTO);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/quotas/{subscriptionId}", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyQuota").value(1000))
                .andExpect(jsonPath("$.remainingQuota").value(500));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getQuotasByUserId_ShouldReturnUserQuotas() throws Exception {
        // Arrange
        UUID userId = testQuotaDTO.getUserId();
        when(adminService.getQuotasByUserId(userId))
                .thenReturn(Arrays.asList(testQuotaDTO));

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users/{userId}/quotas", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateQuota_WithValidRequest_ShouldReturnUpdatedQuota() throws Exception {
        // Arrange
        UUID subscriptionId = testQuotaDTO.getSubscriptionId();
        UpdateQuotaRequest request = new UpdateQuotaRequest();
        request.setMonthlyQuota(2000);
        request.setResetRemainingQuota(true);

        QuotaManagementDTO updatedQuota = QuotaManagementDTO.builder()
                .subscriptionId(subscriptionId)
                .monthlyQuota(2000)
                .remainingQuota(2000)
                .build();

        when(adminService.updateQuota(eq(subscriptionId), any(UpdateQuotaRequest.class)))
                .thenReturn(updatedQuota);

        // Act & Assert
        mockMvc.perform(put("/api/v1/admin/quotas/{subscriptionId}", subscriptionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyQuota").value(2000))
                .andExpect(jsonPath("$.remainingQuota").value(2000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetQuota_ShouldReturnResetQuota() throws Exception {
        // Arrange
        UUID subscriptionId = testQuotaDTO.getSubscriptionId();
        QuotaManagementDTO resetQuota = QuotaManagementDTO.builder()
                .subscriptionId(subscriptionId)
                .monthlyQuota(1000)
                .remainingQuota(1000)
                .build();

        when(adminService.resetQuota(subscriptionId))
                .thenReturn(resetQuota);

        // Act & Assert
        mockMvc.perform(post("/api/v1/admin/quotas/{subscriptionId}/reset", subscriptionId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuota").value(1000));
    }
}
