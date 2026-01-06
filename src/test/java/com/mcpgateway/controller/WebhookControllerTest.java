package com.mcpgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.dto.webhook.CreateWebhookRequest;
import com.mcpgateway.dto.webhook.WebhookDTO;
import com.mcpgateway.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

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

    private WebhookConfig testWebhook;
    private WebhookLog testLog;

    @BeforeEach
    void setUp() {
        UUID webhookId = UUID.randomUUID();

        testWebhook = WebhookConfig.builder()
                .id(webhookId)
                .userId(UUID.randomUUID())
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

    @Test
    @WithMockUser
    void getUserWebhooks_ShouldReturnWebhookList() throws Exception {
        // Arrange
        when(webhookService.getUserWebhooks(any(UUID.class)))
                .thenReturn(Arrays.asList(testWebhook));

        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].url").value("https://example.com/webhook"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/webhook"))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events[0]").value("payment.success"));
    }

    @Test
    @WithMockUser
    void createWebhook_WithInvalidUrl_ShouldReturnBadRequest() throws Exception {
        // Arrange
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("invalid-url")
                .events(Arrays.asList("payment.success"))
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void createWebhook_WithNoEvents_ShouldReturnBadRequest() throws Exception {
        // Arrange
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("https://example.com/webhook")
                .events(Arrays.asList())
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void updateWebhook_WithValidRequest_ShouldReturnUpdatedWebhook() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .url("https://newurl.com/webhook")
                .events(Arrays.asList("payment.success"))
                .description("Updated webhook")
                .build();

        when(webhookService.updateWebhook(eq(webhookId), any(WebhookConfig.class)))
                .thenReturn(testWebhook);

        // Act & Assert
        mockMvc.perform(put("/api/v1/webhooks/{webhookId}", webhookId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void deleteWebhook_ShouldReturnSuccess() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        // Act & Assert
        mockMvc.perform(delete("/api/v1/webhooks/{webhookId}", webhookId)
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(webhookService).deleteWebhook(webhookId);
    }

    @Test
    @WithMockUser
    void reactivateWebhook_ShouldReturnReactivatedWebhook() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        when(webhookService.getUserWebhooks(any(UUID.class)))
                .thenReturn(Arrays.asList(testWebhook));

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/{webhookId}/reactivate", webhookId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(webhookId.toString()));

        verify(webhookService).reactivateWebhook(webhookId);
    }

    @Test
    @WithMockUser
    void getWebhookLogs_ShouldReturnLogList() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        when(webhookService.getWebhookLogs(eq(webhookId), eq(50)))
                .thenReturn(Arrays.asList(testLog));

        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/{webhookId}/logs", webhookId)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eventType").value("payment.success"))
                .andExpect(jsonPath("$[0].httpStatusCode").value(200));
    }

    @Test
    @WithMockUser
    void getAvailableEvents_ShouldReturnEventList() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$[?(@=='payment.success')]").exists())
                .andExpect(jsonPath("$[?(@=='payment.failure')]").exists())
                .andExpect(jsonPath("$[?(@=='subscription.created')]").exists());
    }

    @Test
    @WithMockUser
    void getWebhookLogs_WithCustomLimit_ShouldUseProvidedLimit() throws Exception {
        // Arrange
        UUID webhookId = testWebhook.getId();

        when(webhookService.getWebhookLogs(eq(webhookId), eq(100)))
                .thenReturn(Arrays.asList(testLog));

        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/{webhookId}/logs", webhookId)
                        .param("limit", "100"))
                .andExpect(status().isOk());

        verify(webhookService).getWebhookLogs(webhookId, 100);
    }
}
