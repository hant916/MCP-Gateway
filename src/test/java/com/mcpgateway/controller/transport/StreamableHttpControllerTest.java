package com.mcpgateway.controller.transport;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.domain.Session;
import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.service.McpServerConnectionService;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.service.UsageBillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamableHttpControllerTest {

    @Mock
    private McpServerConnectionService connectionService;

    @Mock
    private SessionService sessionService;

    @Mock
    private UsageBillingService billingService;

    @InjectMocks
    private StreamableHttpController controller;

    private UUID sessionId;
    private Session testSession;
    private McpServer testServer;
    private MessageRequest messageRequest;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();

        testServer = new McpServer();
        testServer.setId(UUID.randomUUID());
        testServer.setServiceName("Test Server");
        testServer.setServiceEndpoint("https://example.com/streamable");

        testSession = new Session();
        testSession.setId(sessionId);
        testSession.setMcpServer(testServer);
        testSession.setTransportType("streamable-http");

        messageRequest = new MessageRequest();
        messageRequest.setMethod("tools/call");
        messageRequest.setParams(new Object());
    }

    @Test
    void establishConnection_WithValidSession_ShouldReturnStreamingResponse() {
        // Arrange
        StreamingResponseBody mockResponseBody = outputStream -> {
            outputStream.write("test".getBytes());
        };
        when(sessionService.getSession(sessionId)).thenReturn(testSession);
        when(connectionService.establishUpstreamStreamableHttpConnection(sessionId, testServer))
            .thenReturn(mockResponseBody);
        doNothing().when(billingService).recordUsageAsync(
            any(UUID.class), anyString(), anyString(), anyInt(), anyLong(), any(), any(), anyInt()
        );

        // Act
        ResponseEntity<StreamingResponseBody> response = controller.establishConnection(sessionId);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(MediaType.APPLICATION_NDJSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        verify(sessionService).getSession(sessionId);
        verify(connectionService).establishUpstreamStreamableHttpConnection(sessionId, testServer);
        verify(billingService).recordUsageAsync(
            eq(sessionId),
            contains("/streamable-http"),
            eq("GET"),
            eq(200),
            anyLong(),
            isNull(),
            isNull(),
            eq(100)
        );
    }

    @Test
    void establishConnection_WithNonExistentSession_ShouldThrowException() {
        // Arrange
        when(sessionService.getSession(sessionId))
            .thenThrow(new RuntimeException("Session not found"));

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> controller.establishConnection(sessionId)
        );
        verify(sessionService).getSession(sessionId);
        verify(connectionService, never()).establishUpstreamStreamableHttpConnection(any(), any());
    }

    @Test
    void sendMessage_WithValidRequest_ShouldSendToUpstream() {
        // Arrange
        when(sessionService.getSession(sessionId)).thenReturn(testSession);
        doNothing().when(connectionService).sendMessageToStreamableHttpUpstream(
            any(UUID.class), any(McpServer.class), any(MessageRequest.class)
        );
        doNothing().when(billingService).recordUsageAsync(
            any(UUID.class), anyString(), anyString(), anyInt(), anyLong(), any(), any(), anyInt()
        );

        // Act
        ResponseEntity<String> response = controller.sendMessage(sessionId, messageRequest);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Message sent"));
        verify(sessionService).getSession(sessionId);
        verify(connectionService).sendMessageToStreamableHttpUpstream(sessionId, testServer, messageRequest);
        verify(billingService).recordUsageAsync(
            eq(sessionId),
            contains("/streamable-http/message"),
            eq("POST"),
            eq(200),
            anyLong(),
            isNull(),
            isNull(),
            eq(50)
        );
    }

    @Test
    void sendMessage_WithNonExistentSession_ShouldThrowException() {
        // Arrange
        when(sessionService.getSession(sessionId))
            .thenThrow(new RuntimeException("Session not found"));

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> controller.sendMessage(sessionId, messageRequest)
        );
        verify(connectionService, never()).sendMessageToStreamableHttpUpstream(any(), any(), any());
    }

    @Test
    void sendMessage_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        when(sessionService.getSession(sessionId)).thenReturn(testSession);

        // Act & Assert
        // Should not throw NullPointerException, but may throw validation exception
        // depending on implementation
        assertDoesNotThrow(() -> {
            try {
                controller.sendMessage(sessionId, null);
            } catch (NullPointerException e) {
                fail("Should handle null message gracefully");
            }
        });
    }
}
