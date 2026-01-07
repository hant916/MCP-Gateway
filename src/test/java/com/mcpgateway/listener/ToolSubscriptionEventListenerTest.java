package com.mcpgateway.listener;

import com.mcpgateway.domain.AuditLog;
import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.domain.User;
import com.mcpgateway.event.ToolSubscribedEvent;
import com.mcpgateway.service.AuditLogService;
import com.mcpgateway.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ToolSubscriptionEventListener
 *
 * Tests tool subscription event processing
 */
@ExtendWith(MockitoExtension.class)
class ToolSubscriptionEventListenerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private AuditLogService auditLogService;

    private ToolSubscriptionEventListener subscriptionEventListener;

    private ToolSubscription testSubscription;
    private UUID testUserId;
    private UUID testToolId;

    @BeforeEach
    void setUp() {
        subscriptionEventListener = new ToolSubscriptionEventListener(webhookService, auditLogService);

        testUserId = UUID.randomUUID();
        testToolId = UUID.randomUUID();

        User testUser = new User();
        testUser.setId(testUserId);
        testUser.setUsername("testuser");

        McpTool testTool = new McpTool();
        testTool.setId(testToolId);
        testTool.setName("awesome-tool");
        testTool.setDescription("An awesome MCP tool");

        testSubscription = new ToolSubscription();
        testSubscription.setId(UUID.randomUUID());
        testSubscription.setClient(testUser);
        testSubscription.setTool(testTool);
        testSubscription.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setStartDate(LocalDateTime.now());
    }

    @Test
    void onToolSubscribed_Success_SendsWebhookAndLogsAudit() {
        // Given
        ToolSubscribedEvent event = new ToolSubscribedEvent(testSubscription);

        doNothing().when(webhookService).sendWebhook(any(UUID.class), anyString(), anyMap());
        doNothing().when(auditLogService).log(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), any(AuditLog.Status.class), anyMap()
        );

        // When
        subscriptionEventListener.onToolSubscribed(event);

        // Then
        // Verify webhook was sent
        ArgumentCaptor<Map<String, Object>> webhookPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webhookService, timeout(1000)).sendWebhook(
                eq(testUserId),
                eq("tool.subscribed"),
                webhookPayloadCaptor.capture()
        );

        Map<String, Object> webhookPayload = webhookPayloadCaptor.getValue();
        assertThat(webhookPayload).containsEntry("event_type", "tool.subscribed");
        assertThat(webhookPayload).containsKey("subscription_id");
        assertThat(webhookPayload).containsKey("user_id");
        assertThat(webhookPayload).containsKey("tool_id");
        assertThat(webhookPayload).containsEntry("tool_name", "awesome-tool");
        assertThat(webhookPayload).containsEntry("status", ToolSubscription.SubscriptionStatus.ACTIVE.toString());

        // Verify audit log was created
        verify(auditLogService, timeout(1000)).log(
                eq(testUserId),
                isNull(), // username not available in event
                eq("TOOL_SUBSCRIBED"),
                eq("subscription"),
                eq(testSubscription.getId().toString()),
                eq(AuditLog.Status.SUCCESS),
                anyMap()
        );
    }

    @Test
    void onToolSubscribed_WebhookFailure_StillLogsAudit() {
        // Given
        ToolSubscribedEvent event = new ToolSubscribedEvent(testSubscription);

        // Webhook throws exception
        doThrow(new RuntimeException("Webhook delivery failed"))
                .when(webhookService).sendWebhook(any(UUID.class), anyString(), anyMap());

        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        // When
        subscriptionEventListener.onToolSubscribed(event);

        // Then
        // Webhook was attempted
        verify(webhookService, timeout(1000)).sendWebhook(any(), anyString(), anyMap());

        // Audit log still created
        verify(auditLogService, timeout(1000)).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onToolSubscribed_VerifyAuditMetadata() {
        // Given
        ToolSubscribedEvent event = new ToolSubscribedEvent(testSubscription);

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        subscriptionEventListener.onToolSubscribed(event);

        // Then
        verify(auditLogService, timeout(1000)).log(
                any(), any(), any(), any(), any(), any(), metadataCaptor.capture()
        );

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata).containsEntry("tool_id", testToolId.toString());
        assertThat(metadata).containsEntry("tool_name", "awesome-tool");
        assertThat(metadata).containsEntry("status", ToolSubscription.SubscriptionStatus.ACTIVE.toString());
    }

    @Test
    void onToolSubscribed_MultipleSubscriptions_ProcessedIndependently() {
        // Given
        User user1 = new User();
        user1.setId(UUID.randomUUID());

        McpTool tool1 = new McpTool();
        tool1.setId(UUID.randomUUID());
        tool1.setName("tool-1");

        ToolSubscription subscription1 = new ToolSubscription();
        subscription1.setId(UUID.randomUUID());
        subscription1.setClient(user1);
        subscription1.setTool(tool1);
        subscription1.setStatus(ToolSubscription.SubscriptionStatus.ACTIVE);

        User user2 = new User();
        user2.setId(UUID.randomUUID());

        McpTool tool2 = new McpTool();
        tool2.setId(UUID.randomUUID());
        tool2.setName("tool-2");

        ToolSubscription subscription2 = new ToolSubscription();
        subscription2.setId(UUID.randomUUID());
        subscription2.setClient(user2);
        subscription2.setTool(tool2);
        subscription2.setStatus(ToolSubscription.SubscriptionStatus.TRIAL);

        ToolSubscribedEvent event1 = new ToolSubscribedEvent(subscription1);
        ToolSubscribedEvent event2 = new ToolSubscribedEvent(subscription2);

        // When
        subscriptionEventListener.onToolSubscribed(event1);
        subscriptionEventListener.onToolSubscribed(event2);

        // Then
        verify(webhookService, timeout(1000).times(2)).sendWebhook(any(), eq("tool.subscribed"), anyMap());
        verify(auditLogService, timeout(1000).times(2)).log(
                any(), any(), eq("TOOL_SUBSCRIBED"), any(), any(), any(), any()
        );
    }

    @Test
    void onToolSubscribed_VerifyWebhookPayloadStructure() {
        // Given
        ToolSubscribedEvent event = new ToolSubscribedEvent(testSubscription);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        subscriptionEventListener.onToolSubscribed(event);

        // Then
        verify(webhookService, timeout(1000)).sendWebhook(
                any(UUID.class),
                eq("tool.subscribed"),
                payloadCaptor.capture()
        );

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).isNotNull();
        assertThat(payload).containsKeys(
                "event_type", "subscription_id", "user_id", "tool_id", "tool_name", "status", "timestamp"
        );
        assertThat(payload.get("event_type")).isEqualTo("tool.subscribed");
    }

    @Test
    void onToolSubscribed_AllOperationsFailure_DoesNotThrow() {
        // Given
        ToolSubscribedEvent event = new ToolSubscribedEvent(testSubscription);

        // All operations fail
        doThrow(new RuntimeException("Webhook failed"))
                .when(webhookService).sendWebhook(any(), any(), any());
        doThrow(new RuntimeException("Audit log failed"))
                .when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        // When & Then - No exception propagated
        subscriptionEventListener.onToolSubscribed(event);

        // Both operations were attempted
        verify(webhookService, timeout(1000)).sendWebhook(any(), any(), any());
        verify(auditLogService, timeout(1000)).log(any(), any(), any(), any(), any(), any(), any());
    }
}
