package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.domain.Session;
import com.mcpgateway.domain.User;
import com.mcpgateway.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private SessionService sessionService;

    private Session testSession;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        testSession = new Session();
        testSession.setId(sessionId);
        testSession.setSessionToken("test-token-123");
        testSession.setTransportType(Session.TransportType.SSE);
        testSession.setStatus(Session.SessionStatus.ACTIVE);
        testSession.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        testSession.setExpiresAt(new Timestamp(System.currentTimeMillis() + 3600000));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        testSession.setUser(user);

        McpServer server = new McpServer();
        server.setId(UUID.randomUUID());
        server.setServiceName("Test MCP Server");
        testSession.setMcpServer(server);
    }

    @Test
    void getSession_WhenSessionExists_ShouldReturnSession() {
        // Arrange
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));

        // Act
        Session result = sessionService.getSession(sessionId);

        // Assert
        assertNotNull(result);
        assertEquals(sessionId, result.getId());
        assertEquals("test-token-123", result.getSessionToken());
        verify(sessionRepository, times(1)).findById(sessionId);
    }

    @Test
    void getSession_WhenSessionNotFound_ShouldThrowException() {
        // Arrange
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> sessionService.getSession(sessionId));
        verify(sessionRepository, times(1)).findById(sessionId);
    }

    @Test
    void isSessionExpired_WhenNotExpired_ShouldReturnFalse() {
        // Arrange
        Timestamp futureExpiry = new Timestamp(System.currentTimeMillis() + 3600000);
        testSession.setExpiresAt(futureExpiry);

        // Act
        boolean result = testSession.isExpired();

        // Assert
        assertFalse(result);
    }

    @Test
    void isSessionExpired_WhenExpired_ShouldReturnTrue() {
        // Arrange
        Timestamp pastExpiry = new Timestamp(System.currentTimeMillis() - 1000);
        testSession.setExpiresAt(pastExpiry);

        // Act
        boolean result = testSession.isExpired();

        // Assert
        assertTrue(result);
    }
}
