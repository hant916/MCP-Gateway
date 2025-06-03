package com.mcpgateway.transport;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.service.MessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketTransport implements McpTransport {
    private final MessageLogService messageLogService;
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private Session session;

    @Override
    public void initialize(Session session) {
        this.session = session;
    }

    @Override
    public void sendMessage(String message) {
        WebSocketSession wsSession = sessions.get(session.getSessionToken());
        if (wsSession != null && wsSession.isOpen()) {
            try {
                wsSession.sendMessage(new TextMessage(message));
                messageLogService.logMessage(session.getId(), MessageLog.MessageType.REQUEST, message);
            } catch (IOException e) {
                log.error("Failed to send WebSocket message", e);
                messageLogService.logMessage(session.getId(), MessageLog.MessageType.ERROR,
                        "Failed to send WebSocket message: " + e.getMessage());
                sessions.remove(session.getSessionToken());
            }
        }
    }

    @Override
    public void handleMessage(String message) {
        messageLogService.logMessage(session.getId(), MessageLog.MessageType.RESPONSE, message);
    }

    @Override
    public void close() {
        WebSocketSession wsSession = sessions.remove(session.getSessionToken());
        if (wsSession != null && wsSession.isOpen()) {
            try {
                wsSession.close();
            } catch (IOException e) {
                log.error("Error closing WebSocket session", e);
            }
        }
    }

    public void registerWebSocketSession(String sessionToken, WebSocketSession webSocketSession) {
        sessions.put(sessionToken, webSocketSession);
    }

    public void removeWebSocketSession(String sessionToken) {
        sessions.remove(sessionToken);
    }
} 