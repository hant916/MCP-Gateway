package com.mcpgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.User;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.dto.webhook.CreateWebhookRequest;
import com.mcpgateway.security.ApiKeyAuthFilter;
import com.mcpgateway.security.JwtAuthenticationFilter;
import com.mcpgateway.service.WebhookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private ApiKeyAuthFilter apiKeyAuthFilter;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private WebhookConfig testWebhook;
    private WebhookLog testLog;
    private User authUser;

    @BeforeEach
    void setUp() {
        UUID webhookId = UUID.randomUUID();
        authUser = new User();
        authUser.setId(UUID.randomUUID());
        authUser.setUsername("webhook-user");
        authUser.setPassword("encoded");
        authUser.setEmail("webhook@example.com");
        authUser.setRole(User.UserRole.USER);
        authUser.setIsActive(true);

        testWebhook = WebhookConfig.builder()
                .id(webhookId)
                .userId(authUser.getId())
                .url("https://example.com/webhook")
                .secret("secret")
                .events("payment.success,subscription.created")
                .status(WebhookConfig.WebhookStatus.ACTIVE)
                .isActive(true)
                .retryCount(3)
                .timeoutSeconds(30)
                .successCount(10)
                .failureCount(0)
                .build();

        testLog = WebhookLog.builder()
                .id(UUID.randomUUID())
                .webhookConfig(testWebhook)
                .eventType("payment.success")
                .payload("{\"test\":\"data\"}")
                .httpStatusCode(200)
                .status(WebhookLog.DeliveryStatus.SUCCESS)
                .durationMs(150L)
                .build();
    }

    private RequestPostProcessor authenticatedUser() {
        return request -> {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authUser,
                    null,
                    authUser.getAuthorities()
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserWebhooks_ShouldReturnWebhookList() throws Exception {
        // Arrange
        when(webhookService.getUserWebhooks(authUser.getId()))
                .thenReturn(Arrays.asList(testWebhook));

        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].url").value("https://example.com/webhook"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void createWebhook_WithValidRequest_ShouldReturnCreatedWebhook() throws Exception {
        // Arrange
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("https://example.com/webhook")
                .events(Arrays.asList("payment.success", "subscription.created"))
                .description("Test webhook")
                .retryCount(3)
                .timeoutSeconds(30)
                .build();

        when(webhookService.createWebhook(any(WebhookConfig.class)))
                .thenReturn(testWebhook);

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/webhook"))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events[0]").value("payment.success"));
    }

    @Test
    void createWebhook_WithInvalidUrl_ShouldReturnBadRequest() throws Exception {
        // Arrange
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("invalid-url")
                .events(Arrays.asList("payment.success"))
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWebhook_WithNoEvents_ShouldReturnBadRequest() throws Exception {
        // Arrange
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("https://example.com/webhook")
                .events(Arrays.asList())
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWebhook_WithValidRequest_ShouldReturnUpdatedWebhook() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("https://newurl.com/webhook")
                .events(Arrays.asList("payment.success"))
                .description("Updated webhook")
                .build();

        when(webhookService.updateWebhook(eq(webhookId), eq(authUser.getId()), any(WebhookConfig.class)))
                .thenReturn(testWebhook);

        // Act & Assert
        mockMvc.perform(put("/api/v1/webhooks/{webhookId}", webhookId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteWebhook_ShouldReturnSuccess() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        // Act & Assert
        mockMvc.perform(delete("/api/v1/webhooks/{webhookId}", webhookId)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(webhookService).deleteWebhook(webhookId, authUser.getId());
    }

    @Test
    void reactivateWebhook_ShouldReturnReactivatedWebhook() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        when(webhookService.reactivateWebhook(webhookId, authUser.getId()))
                .thenReturn(testWebhook);

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/{webhookId}/reactivate", webhookId)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(webhookId.toString()));

        verify(webhookService).reactivateWebhook(webhookId, authUser.getId());
    }

    @Test
    void getWebhookLogs_ShouldReturnLogList() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        when(webhookService.getWebhookLogs(eq(webhookId), eq(authUser.getId()), eq(50)))
                .thenReturn(Arrays.asList(testLog));

        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/{webhookId}/logs", webhookId)
                        .with(authenticatedUser())
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eventType").value("payment.success"))
                .andExpect(jsonPath("$[0].httpStatusCode").value(200));
    }

    @Test
    void getAvailableEvents_ShouldReturnEventList() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/events").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$[?(@=='payment.success')]").exists())
                .andExpect(jsonPath("$[?(@=='payment.failure')]").exists())
                .andExpect(jsonPath("$[?(@=='subscription.created')]").exists());
    }

    @Test
    void getWebhookLogs_WithCustomLimit_ShouldUseProvidedLimit() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        when(webhookService.getWebhookLogs(eq(webhookId), eq(authUser.getId()), eq(100)))
                .thenReturn(Arrays.asList(testLog));

        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/{webhookId}/logs", webhookId)
                        .with(authenticatedUser())
                        .param("limit", "100"))
                .andExpect(status().isOk());

        verify(webhookService).getWebhookLogs(webhookId, authUser.getId(), 100);
    }

    @Test
    void updateWebhook_WithWrongOwner_ShouldReturnForbidden() throws Exception {
        UUID webhookId = testWebhook.getId();
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("https://newurl.com/webhook")
                .events(Arrays.asList("payment.success"))
                .description("Updated webhook")
                .build();

        when(webhookService.updateWebhook(eq(webhookId), eq(authUser.getId()), any(WebhookConfig.class)))
                .thenThrow(new AccessDeniedException("Webhook access denied"));

        mockMvc.perform(put("/api/v1/webhooks/{webhookId}", webhookId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
