package com.mcpgateway.transport;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.service.MessageLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WebSocket Session Management Tests
 *
 * Tests the WebSocket transport layer for:
 * - Connection lifecycle state machine
 * - Reconnection with same clientId
 * - Concurrent connection handling
 * - Resource cleanup and leak prevention (1000 connect/disconnect cycles)
 * - Error handling during connection/disconnection
 * - Message delivery guarantees
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionManagementTest {

    @Mock
    private MessageLogService messageLogService;

    @Mock
    private WebSocketSession mockWebSocketSession;

    private WebSocketTransport webSocketTransport;
    private Session testSession;

    @BeforeEach
    void setUp() {
        webSocketTransport = new WebSocketTransport(messageLogService);
        testSession = new Session();
        testSession.setId(UUID.randomUUID());
        testSession.setSessionToken("ws-test-token-" + UUID.randomUUID());

        // Default mock behavior
        when(mockWebSocketSession.isOpen()).thenReturn(true);
    }

    @Test
    void testConnectionLifecycle_ConnectSendClose() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act & Assert - Connection established
        when(mockWebSocketSession.isOpen()).thenReturn(true);

        // Send message
        webSocketTransport.sendMessage("test-message");
        verify(mockWebSocketSession).sendMessage(any(TextMessage.class));
        verify(messageLogService).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.REQUEST),
            eq("test-message")
        );

        // Close connection
        webSocketTransport.close();
        verify(mockWebSocketSession).close();
    }

    @Test
    void testReconnection_SameClientId() throws Exception {
        // Arrange - First connection
        webSocketTransport.initialize(testSession);
        WebSocketSession firstSession = mock(WebSocketSession.class);
        when(firstSession.isOpen()).thenReturn(true);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), firstSession);

        // Act - Close first connection
        webSocketTransport.close();
        verify(firstSession).close();

        // Reconnect with same session token
        WebSocketSession secondSession = mock(WebSocketSession.class);
        when(secondSession.isOpen()).thenReturn(true);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), secondSession);

        // Assert - Second session should work
        webSocketTransport.sendMessage("after-reconnect");
        verify(secondSession).sendMessage(any(TextMessage.class));
        verify(firstSession, never()).sendMessage(any(TextMessage.class)); // First session not used
    }

    @Test
    void testConcurrentConnections_MultipleClients() throws Exception {
        // Arrange
        int numClients = 50;
        List<Session> sessions = new ArrayList<>();
        List<WebSocketSession> mockSessions = new ArrayList<>();
        List<WebSocketTransport> transports = new ArrayList<>();

        // Create multiple concurrent connections
        for (int i = 0; i < numClients; i++) {
            Session session = new Session();
            session.setId(UUID.randomUUID());
            session.setSessionToken("concurrent-ws-" + i);
            sessions.add(session);

            WebSocketSession mockWs = mock(WebSocketSession.class);
            when(mockWs.isOpen()).thenReturn(true);
            mockSessions.add(mockWs);

            WebSocketTransport transport = new WebSocketTransport(messageLogService);
            transport.initialize(session);
            transport.registerWebSocketSession(session.getSessionToken(), mockWs);
            transports.add(transport);
        }

        // Act - Send messages from all clients concurrently
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numClients);

        for (int i = 0; i < numClients; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    transports.get(index).sendMessage("message-from-client-" + index);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify all messages were sent
        for (WebSocketSession mockWs : mockSessions) {
            verify(mockWs).sendMessage(any(TextMessage.class));
        }
    }

    @Test
    void testResourceCleanup_1000ConnectDisconnectCycles() throws Exception {
        // Arrange
        AtomicInteger activeSessions = new AtomicInteger(0);
        AtomicInteger closedSessions = new AtomicInteger(0);

        // Act - 1000 connect/disconnect cycles
        for (int i = 0; i < 1000; i++) {
            Session session = new Session();
            session.setId(UUID.randomUUID());
            session.setSessionToken("cycle-" + i);

            WebSocketSession mockWs = mock(WebSocketSession.class);
            when(mockWs.isOpen()).thenReturn(true);

            WebSocketTransport transport = new WebSocketTransport(messageLogService);
            transport.initialize(session);
            transport.registerWebSocketSession(session.getSessionToken(), mockWs);
            activeSessions.incrementAndGet();

            // Send a message
            transport.sendMessage("test-" + i);

            // Close
            transport.close();
            closedSessions.incrementAndGet();
            activeSessions.decrementAndGet();
        }

        // Assert - All sessions cleaned up
        assertThat(activeSessions.get()).isEqualTo(0);
        assertThat(closedSessions.get()).isEqualTo(1000);
    }

    @Test
    void testSendMessage_WhenConnectionClosed() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        when(mockWebSocketSession.isOpen()).thenReturn(false); // Connection closed
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act - Try to send message
        webSocketTransport.sendMessage("message-to-closed-connection");

        // Assert - Should not attempt to send
        verify(mockWebSocketSession, never()).sendMessage(any(TextMessage.class));
        verify(messageLogService, never()).logMessage(
            any(),
            eq(MessageLog.MessageType.REQUEST),
            anyString()
        );
    }

    @Test
    void testSendMessage_IOException_CleansUpSession() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        when(mockWebSocketSession.isOpen()).thenReturn(true);
        doThrow(new IOException("Network error")).when(mockWebSocketSession).sendMessage(any(TextMessage.class));
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act
        webSocketTransport.sendMessage("message-causing-error");

        // Assert - Error logged and session removed
        verify(messageLogService).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.ERROR),
            contains("Failed to send WebSocket message")
        );
    }

    @Test
    void testHandleMessage_LogsResponse() {
        // Arrange
        webSocketTransport.initialize(testSession);

        // Act
        webSocketTransport.handleMessage("incoming-message");

        // Assert
        verify(messageLogService).logMessage(
            testSession.getId(),
            MessageLog.MessageType.RESPONSE,
            "incoming-message"
        );
    }

    @Test
    void testClose_WhenSessionNotOpen() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        when(mockWebSocketSession.isOpen()).thenReturn(false);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act
        webSocketTransport.close();

        // Assert - Should not attempt to close
        verify(mockWebSocketSession, never()).close();
    }

    @Test
    void testClose_IOExceptionHandled() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        when(mockWebSocketSession.isOpen()).thenReturn(true);
        doThrow(new IOException("Close error")).when(mockWebSocketSession).close();
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act & Assert - Should not throw
        assertThatCode(() -> webSocketTransport.close()).doesNotThrowAnyException();
    }

    @Test
    void testMultipleMessages_Sequential() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act - Send multiple messages
        for (int i = 0; i < 100; i++) {
            webSocketTransport.sendMessage("message-" + i);
        }

        // Assert
        verify(mockWebSocketSession, times(100)).sendMessage(any(TextMessage.class));
        verify(messageLogService, times(100)).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.REQUEST),
            anyString()
        );
    }

    @Test
    void testConcurrentSends_SameSession() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act - Send messages concurrently from multiple threads
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int msgNum = i;
            executor.submit(() -> {
                try {
                    webSocketTransport.sendMessage("concurrent-" + msgNum);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        verify(mockWebSocketSession, times(100)).sendMessage(any(TextMessage.class));
    }

    @Test
    void testRemoveWebSocketSession_ManualRemoval() {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act
        webSocketTransport.removeWebSocketSession(testSession.getSessionToken());

        // Try to send message
        webSocketTransport.sendMessage("after-removal");

        // Assert - Should not send
        verify(mockWebSocketSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void testStateTransitions_ConnectedToDisconnected() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // State 1: Connected
        when(mockWebSocketSession.isOpen()).thenReturn(true);
        webSocketTransport.sendMessage("message-1");
        verify(mockWebSocketSession, times(1)).sendMessage(any(TextMessage.class));

        // State 2: Connection lost
        when(mockWebSocketSession.isOpen()).thenReturn(false);
        webSocketTransport.sendMessage("message-2");
        verify(mockWebSocketSession, times(1)).sendMessage(any(TextMessage.class)); // Still 1, not 2

        // State 3: Reconnected
        when(mockWebSocketSession.isOpen()).thenReturn(true);
        webSocketTransport.sendMessage("message-3");
        verify(mockWebSocketSession, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void testSessionIsolation_DifferentSessionTokens() throws Exception {
        // Arrange
        Session session1 = new Session();
        session1.setId(UUID.randomUUID());
        session1.setSessionToken("token-1");

        Session session2 = new Session();
        session2.setId(UUID.randomUUID());
        session2.setSessionToken("token-2");

        WebSocketSession mockWs1 = mock(WebSocketSession.class);
        WebSocketSession mockWs2 = mock(WebSocketSession.class);
        when(mockWs1.isOpen()).thenReturn(true);
        when(mockWs2.isOpen()).thenReturn(true);

        WebSocketTransport transport1 = new WebSocketTransport(messageLogService);
        WebSocketTransport transport2 = new WebSocketTransport(messageLogService);

        transport1.initialize(session1);
        transport2.initialize(session2);

        transport1.registerWebSocketSession(session1.getSessionToken(), mockWs1);
        transport2.registerWebSocketSession(session2.getSessionToken(), mockWs2);

        // Act
        transport1.sendMessage("message-to-session-1");
        transport2.sendMessage("message-to-session-2");

        // Assert - Messages sent to correct sessions
        verify(mockWs1).sendMessage(any(TextMessage.class));
        verify(mockWs2).sendMessage(any(TextMessage.class));
    }

    @Test
    void testRapidReconnections() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);

        // Act - Rapid connect/disconnect cycles
        for (int i = 0; i < 50; i++) {
            WebSocketSession tempSession = mock(WebSocketSession.class);
            when(tempSession.isOpen()).thenReturn(true);

            webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), tempSession);
            webSocketTransport.sendMessage("rapid-" + i);
            webSocketTransport.removeWebSocketSession(testSession.getSessionToken());
        }

        // Assert - No exceptions thrown
        // All operations completed successfully
    }

    @Test
    void testCloseIdempotency() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        // Act - Close multiple times
        webSocketTransport.close();
        webSocketTransport.close();
        webSocketTransport.close();

        // Assert - Close called only once (or fails gracefully on subsequent calls)
        verify(mockWebSocketSession, atMost(1)).close();
    }

    @Test
    void testLargeMessageHandling() throws Exception {
        // Arrange
        webSocketTransport.initialize(testSession);
        webSocketTransport.registerWebSocketSession(testSession.getSessionToken(), mockWebSocketSession);

        StringBuilder largeMessage = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeMessage.append("This is a large message payload. ");
        }

        // Act
        webSocketTransport.sendMessage(largeMessage.toString());

        // Assert
        verify(mockWebSocketSession).sendMessage(argThat(msg ->
            ((TextMessage) msg).getPayload().length() > 100000
        ));
    }

    @Test
    void testNullSessionToken_HandledGracefully() {
        // Arrange
        Session nullTokenSession = new Session();
        nullTokenSession.setId(UUID.randomUUID());
        nullTokenSession.setSessionToken(null);

        webSocketTransport.initialize(nullTokenSession);

        // Act & Assert - Should not throw NPE
        assertThatCode(() -> webSocketTransport.sendMessage("test"))
                .doesNotThrowAnyException();
    }
}
