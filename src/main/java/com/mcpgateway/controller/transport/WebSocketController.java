package com.mcpgateway.controller.transport;

import com.mcpgateway.domain.Session;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.transport.WebSocketTransport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketController extends TextWebSocketHandler {

    private final WebSocketTransport webSocketTransport;
    private final SessionService sessionService;

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        String sessionId = extractSessionId(webSocketSession);
        Session session = sessionService.getSession(UUID.fromString(sessionId));
        webSocketTransport.initialize(session);
        webSocketTransport.registerWebSocketSession(session.getSessionToken(), webSocketSession);
    }

    @Override
    protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) {
        String sessionId = extractSessionId(webSocketSession);
        Session session = sessionService.getSession(UUID.fromString(sessionId));
        webSocketTransport.handleMessage(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) {
        String sessionId = extractSessionId(webSocketSession);
        Session session = sessionService.getSession(UUID.fromString(sessionId));
        webSocketTransport.removeWebSocketSession(session.getSessionToken());
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
} 