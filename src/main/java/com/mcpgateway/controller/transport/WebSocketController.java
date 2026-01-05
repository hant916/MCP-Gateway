package com.mcpgateway.controller.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.Session;
import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.service.McpServerConnectionService;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.service.UsageBillingService;
import com.mcpgateway.transport.WebSocketTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketController extends TextWebSocketHandler {

    private final McpServerConnectionService connectionService;
    private final SessionService sessionService;
    private final UsageBillingService billingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        try {
            String sessionId = extractSessionId(webSocketSession);
            log.info("WebSocket connection established for session: {}", sessionId);
            Session session = sessionService.getSession(UUID.fromString(sessionId));

            // Record connection establishment usage
            billingService.recordUsageAsync(
                UUID.fromString(sessionId),
                "/ws/sessions/" + sessionId,
                "WEBSOCKET",
                101,
                System.currentTimeMillis(),
                null,
                null,
                100
            );

            // Establish upstream WebSocket connection
            connectionService.establishUpstreamWebSocketConnection(
                UUID.fromString(sessionId),
                session.getMcpServer(),
                webSocketSession
            ).subscribe(
                null,
                error -> log.error("Failed to establish upstream WebSocket connection", error),
                () -> log.info("Upstream WebSocket connection completed")
            );

        } catch (Exception e) {
            log.error("Error establishing WebSocket connection", e);
            try {
                webSocketSession.close(CloseStatus.SERVER_ERROR);
            } catch (Exception closeError) {
                log.error("Error closing WebSocket session", closeError);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) {
        try {
            String sessionId = extractSessionId(webSocketSession);
            log.debug("Received WebSocket message for session {}: {}", sessionId, message.getPayload());

            // Parse message
            MessageRequest messageRequest = objectMapper.readValue(
                message.getPayload(),
                MessageRequest.class
            );

            // Record message usage
            billingService.recordUsageAsync(
                UUID.fromString(sessionId),
                "/ws/message",
                "WEBSOCKET",
                200,
                System.currentTimeMillis(),
                (long) message.getPayload().length(),
                null,
                50
            );

            // Forward to upstream
            connectionService.sendMessageToWebSocketUpstream(
                UUID.fromString(sessionId),
                messageRequest
            );

        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) {
        try {
            String sessionId = extractSessionId(webSocketSession);
            log.info("WebSocket connection closed for session: {}, status: {}", sessionId, status);

            // Close upstream connection
            Session session = sessionService.getSession(UUID.fromString(sessionId));
            connectionService.closeUpstreamConnectionByType(
                UUID.fromString(sessionId),
                session.getTransportType()
            );

        } catch (Exception e) {
            log.error("Error closing WebSocket connection", e);
        }
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
} 