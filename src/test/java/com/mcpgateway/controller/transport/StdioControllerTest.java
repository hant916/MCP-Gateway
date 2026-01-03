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
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StdioControllerTest {

    @Mock
    private McpServerConnectionService connectionService;

    @Mock
    private SessionService sessionService;

    @Mock
    private UsageBillingService billingService;

    @InjectMocks
    private StdioController controller;

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
        testServer.setServiceEndpoint("/usr/local/bin/mcp-server");

        testSession = new Session();
        testSession.setId(sessionId);
        testSession.setMcpServer(testServer);
        testSession.setTransportType("stdio");

        messageRequest = new MessageRequest();
        messageRequest.setMethod("tools/call");
        messageRequest.setParams(new Object());
    }

    @Test
    void establishConnection_WithValidSession_ShouldReturnStreamingResponse() {
        // Arrange
        when(sessionService.getSession(sessionId)).thenReturn(testSession);
        doNothing().when(connectionService).establishUpstreamStdioConnection(
            any(UUID.class), any(McpServer.class), any(BlockingQueue.class)
        );
        doNothing().when(billingService).recordUsageAsync(
            any(UUID.class), anyString(), anyString(), anyInt(), anyLong(), any(), any(), anyInt()
        );

        // Act
        ResponseEntity<StreamingResponseBody> response = controller.establishConnection(sessionId);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        verify(sessionService).getSession(sessionId);
        verify(connectionService).establishUpstreamStdioConnection(
            eq(sessionId), eq(testServer), any(BlockingQueue.class)
        );
        verify(billingService).recordUsageAsync(
            eq(sessionId),
            contains("/stdio"),
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
        verify(connectionService, never()).establishUpstreamStdioConnection(any(), any(), any());
    }

    @Test
    void sendMessage_WithValidRequest_ShouldSendToUpstream() {
        // Arrange
        doNothing().when(connectionService).sendMessageToStdioUpstream(
            any(UUID.class), any(MessageRequest.class)
        );
        doNothing().when(billingService).recordUsageAsync(
            any(UUID.class), anyString(), anyString(), anyInt(), anyLong(), any(), any(), anyInt()
        );

        // Act
        ResponseEntity<String> response = controller.sendMessage(sessionId, messageRequest);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Message sent to STDIO process"));
        verify(connectionService).sendMessageToStdioUpstream(sessionId, messageRequest);
        verify(billingService).recordUsageAsync(
            eq(sessionId),
            contains("/stdio/message"),
            eq("POST"),
            eq(200),
            anyLong(),
            isNull(),
            isNull(),
            eq(50)
        );
    }

    @Test
    void sendMessage_WithConnectionServiceException_ShouldPropagateException() {
        // Arrange
        doThrow(new RuntimeException("Connection error"))
            .when(connectionService).sendMessageToStdioUpstream(any(), any());
        doNothing().when(billingService).recordUsageAsync(
            any(UUID.class), anyString(), anyString(), anyInt(), anyLong(), any(), any(), anyInt()
        );

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> controller.sendMessage(sessionId, messageRequest)
        );
    }

    @Test
    void closeConnection_WithValidSession_ShouldCloseUpstream() {
        // Arrange
        doNothing().when(connectionService).closeUpstreamConnectionByType(
            any(UUID.class), any(Session.TransportType.class)
        );

        // Act
        ResponseEntity<String> response = controller.closeConnection(sessionId);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("STDIO connection closed"));
        verify(connectionService).closeUpstreamConnectionByType(sessionId, Session.TransportType.STDIO);
    }

    @Test
    void closeConnection_WithConnectionServiceException_ShouldPropagateException() {
        // Arrange
        doThrow(new RuntimeException("Close error"))
            .when(connectionService).closeUpstreamConnectionByType(any(), any());

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> controller.closeConnection(sessionId)
        );
    }

    @Test
    void closeConnection_MultipleCloses_ShouldNotFail() {
        // Arrange
        doNothing().when(connectionService).closeUpstreamConnectionByType(
            any(UUID.class), any(Session.TransportType.class)
        );

        // Act
        ResponseEntity<String> response1 = controller.closeConnection(sessionId);
        ResponseEntity<String> response2 = controller.closeConnection(sessionId);

        // Assert
        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals(200, response1.getStatusCodeValue());
        assertEquals(200, response2.getStatusCodeValue());
        verify(connectionService, times(2)).closeUpstreamConnectionByType(
            sessionId, Session.TransportType.STDIO
        );
    }
}
