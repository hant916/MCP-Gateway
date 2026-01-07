package com.mcpgateway.listener;

import com.mcpgateway.domain.AuditLog;
import com.mcpgateway.domain.User;
import com.mcpgateway.event.UserRegisteredEvent;
import com.mcpgateway.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserEventListener
 *
 * Tests user registration event processing
 */
@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

    @Mock
    private AuditLogService auditLogService;

    private UserEventListener userEventListener;

    private User testUser;

    @BeforeEach
    void setUp() {
        userEventListener = new UserEventListener(auditLogService);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("newuser");
        testUser.setEmail("newuser@example.com");
        testUser.setRole(User.Role.USER);
    }

    @Test
    void onUserRegistered_Success_LogsAuditTrail() {
        // Given
        UserRegisteredEvent event = new UserRegisteredEvent(testUser);

        doNothing().when(auditLogService).logUserManagement(
                any(UUID.class), anyString(), anyString(), any(UUID.class), any(AuditLog.Status.class), anyMap()
        );

        // When
        userEventListener.onUserRegistered(event);

        // Then
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService, timeout(1000)).logUserManagement(
                eq(testUser.getId()),
                eq(testUser.getUsername()),
                eq("USER_REGISTERED"),
                eq(testUser.getId()),
                eq(AuditLog.Status.SUCCESS),
                metadataCaptor.capture()
        );

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata).containsEntry("email", testUser.getEmail());
        assertThat(metadata).containsEntry("role", testUser.getRole().toString());
        assertThat(metadata).containsKey("registrationMethod");
    }

    @Test
    void onUserRegistered_AuditLogFailure_DoesNotThrow() {
        // Given
        UserRegisteredEvent event = new UserRegisteredEvent(testUser);

        doThrow(new RuntimeException("Database error"))
                .when(auditLogService).logUserManagement(any(), any(), any(), any(), any(), any());

        // When & Then - No exception thrown
        userEventListener.onUserRegistered(event);

        verify(auditLogService, timeout(1000)).logUserManagement(any(), any(), any(), any(), any(), any());
    }

    @Test
    void onUserRegistered_MultipleUsers_ProcessedIndependently() {
        // Given
        User user1 = new User();
        user1.setId(UUID.randomUUID());
        user1.setUsername("user1");
        user1.setEmail("user1@example.com");
        user1.setRole(User.Role.USER);

        User user2 = new User();
        user2.setId(UUID.randomUUID());
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");
        user2.setRole(User.Role.ADMIN);

        UserRegisteredEvent event1 = new UserRegisteredEvent(user1);
        UserRegisteredEvent event2 = new UserRegisteredEvent(user2);

        // When
        userEventListener.onUserRegistered(event1);
        userEventListener.onUserRegistered(event2);

        // Then
        verify(auditLogService, timeout(1000).times(2)).logUserManagement(
                any(), any(), eq("USER_REGISTERED"), any(), any(), any()
        );
    }

    @Test
    void onUserRegistered_VerifyMetadataContainsRegistrationDetails() {
        // Given
        UserRegisteredEvent event = new UserRegisteredEvent(testUser);

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        // When
        userEventListener.onUserRegistered(event);

        // Then
        verify(auditLogService, timeout(1000)).logUserManagement(
                any(), any(), any(), any(), any(), metadataCaptor.capture()
        );

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata).isNotNull();
        assertThat(metadata).hasSize(3); // email, role, registrationMethod
        assertThat(metadata.get("email")).isEqualTo(testUser.getEmail());
        assertThat(metadata.get("role")).isEqualTo(testUser.getRole().toString());
    }
}
