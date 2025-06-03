package com.mcpgateway.websocket;

import com.mcpgateway.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class McpWebSocketHandler extends TextWebSocketHandler {
    private final ApiKeyService apiKeyService;
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final UriTemplate toolIdTemplate = new UriTemplate("/mcp/ws/{toolId}");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从URL中提取toolId
        Map<String, String> variables = toolIdTemplate.match(session.getUri().getPath());
        UUID toolId = UUID.fromString(variables.get("toolId"));
        
        // 验证API Key
        String apiKey = session.getHandshakeHeaders().getFirst("X-API-KEY");
        if (apiKey == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing API Key"));
            return;
        }
        
        try {
            apiKeyService.validateApiKey(apiKey);
            sessions.put(toolId, session);
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid API Key"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 处理接收到的消息
        String payload = message.getPayload();
        // 这里添加你的消息处理逻辑
        
        // 发送响应
        session.sendMessage(new TextMessage("Received: " + payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Map<String, String> variables = toolIdTemplate.match(session.getUri().getPath());
        UUID toolId = UUID.fromString(variables.get("toolId"));
        sessions.remove(toolId);
    }

    /**
     * 向指定的工具会话发送消息
     */
    public void sendMessage(UUID toolId, String message) throws Exception {
        WebSocketSession session = sessions.get(toolId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }
} 