package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.domain.Session;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final McpServerConnectionService connectionService;
    private final UsageBillingService usageBillingService;
    private final ConcurrentHashMap<UUID, SseEmitter> sseConnections = new ConcurrentHashMap<>();

    @Transactional
    public Session createSession(McpServer mcpServer, Session.TransportType transportType, User user) {
        Session session = new Session();
        session.setMcpServer(mcpServer);
        session.setUser(user);
        session.setTransportType(transportType);
        session.setSessionToken(generateSessionToken());
        
        // Set expiration time (1 hour from now)
        long expirationTime = System.currentTimeMillis() + (60 * 60 * 1000);
        session.setExpiresAt(new Timestamp(expirationTime));
        
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Session getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
    }

    @Transactional
    public void validateSession(UUID sessionId) {
        Session session = getSession(sessionId);
        
        if (session.isExpired()) {
            session.setStatus(Session.SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new RuntimeException("Session expired: " + sessionId);
        }
        
        session.updateLastActiveAt();
        sessionRepository.save(session);
    }

    public SseEmitter createSseConnection(UUID sessionId) {
        validateSession(sessionId);
        Session session = getSession(sessionId);
        McpServer mcpServer = session.getMcpServer();
        
        // Set timeout to 1 hour (same as session expiration)
        SseEmitter clientEmitter = new SseEmitter(3600000L);
        sseConnections.put(sessionId, clientEmitter);
        
        // Setup client emitter callbacks
        clientEmitter.onCompletion(() -> {
            log.info("Client SSE connection completed for session: {}", sessionId);
            sseConnections.remove(sessionId);
            connectionService.closeUpstreamConnection(sessionId);
        });
        
        clientEmitter.onTimeout(() -> {
            log.info("Client SSE connection timed out for session: {}", sessionId);
            sseConnections.remove(sessionId);
            connectionService.closeUpstreamConnection(sessionId);
        });
        
        clientEmitter.onError((throwable) -> {
            log.error("Client SSE error for session: " + sessionId, throwable);
            sseConnections.remove(sessionId);
            connectionService.closeUpstreamConnection(sessionId);
        });
        
        // Send initial connection message
        try {
            clientEmitter.send(SseEmitter.event()
                .id("connection")
                .name("connected")
                .data("{\"status\":\"connected\",\"sessionId\":\"" + sessionId + "\",\"message\":\"Use this sessionId as query parameter for /sse/message endpoint\"}"));
        } catch (Exception e) {
            log.error("Failed to send initial SSE message", e);
            clientEmitter.completeWithError(e);
            return clientEmitter;
        }
        
        // Establish upstream connection to actual MCP server
        try {
            if (mcpServer.getServiceEndpoint() != null) {
                log.info("Establishing upstream connection to: {} for session: {}", 
                    mcpServer.getServiceEndpoint(), sessionId);
                connectionService.establishUpstreamSseConnection(sessionId, mcpServer, clientEmitter);
                
                // Update session status to CONNECTED
                session.setStatus(Session.SessionStatus.CONNECTED);
                sessionRepository.save(session);
            } else {
                log.warn("No service endpoint configured for MCP server: {}", mcpServer.getId());
                clientEmitter.send(SseEmitter.event()
                    .name("warning")
                    .data("{\"warning\":\"No upstream endpoint configured - running in local mode\"}"));
            }
        } catch (Exception e) {
            log.error("Failed to establish upstream connection for session: {}", sessionId, e);
            try {
                clientEmitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Failed to connect to upstream MCP server: " + e.getMessage() + "\"}"));
            } catch (Exception sendError) {
                log.error("Failed to send upstream connection error", sendError);
            }
        }
        
        return clientEmitter;
    }

    public void sendMessage(UUID sessionId, MessageRequest message) {
        long startTime = System.currentTimeMillis();
        Integer statusCode = 200; // 默认成功状态
        
        try {
            validateSession(sessionId);
            Session session = getSession(sessionId);
            McpServer mcpServer = session.getMcpServer();
            
            // Update session to ACTIVE on first message
            if (session.getStatus() == Session.SessionStatus.CONNECTED) {
                session.setStatus(Session.SessionStatus.ACTIVE);
                sessionRepository.save(session);
            }
            
            // Forward message to MCP server based on transport type
            switch (session.getTransportType()) {
                case SSE:
                    handleSseMessage(sessionId, mcpServer, message);
                    break;
                case WEBSOCKET:
                    handleWebSocketMessage(sessionId, mcpServer, message);
                    break;
                case STREAMABLE_HTTP:
                    handleStreamableHttpMessage(sessionId, mcpServer, message);
                    break;
                case STDIO:
                    handleStdioMessage(sessionId, mcpServer, message);
                    break;
            }
            
        } catch (Exception e) {
            log.error("Error processing message for session: {}", sessionId, e);
            statusCode = 500; // 内部服务器错误
            throw e;
        } finally {
            // 记录使用量（异步）
            long processingTime = System.currentTimeMillis() - startTime;
            String apiEndpoint = "/api/v1/sse/message"; // 根据实际情况调整
            
            usageBillingService.recordUsageAsync(sessionId, apiEndpoint, "POST", statusCode, message);
            
            log.debug("Recorded usage for session: {}, endpoint: {}, status: {}, processing time: {}ms", 
                sessionId, apiEndpoint, statusCode, processingTime);
        }
    }

    public StreamingResponseBody createStreamingResponse(UUID sessionId, MessageRequest message) {
        validateSession(sessionId);
        Session session = getSession(sessionId);
        McpServer mcpServer = session.getMcpServer();
        
        return outputStream -> {
            long startTime = System.currentTimeMillis();
            Integer statusCode = 200;
            
            try {
                String response = processMessage(sessionId, mcpServer, message);
                outputStream.write(response.getBytes());
                outputStream.flush();
            } catch (Exception e) {
                log.error("Error in streaming response for session: " + sessionId, e);
                statusCode = 500;
                throw new RuntimeException(e);
            } finally {
                // 记录使用量
                long processingTime = System.currentTimeMillis() - startTime;
                String apiEndpoint = "/api/v1/sessions/" + sessionId + "/streamable-http";
                
                usageBillingService.recordUsageAsync(sessionId, apiEndpoint, "POST", statusCode, message);
            }
        };
    }

    private void handleSseMessage(UUID sessionId, McpServer mcpServer, MessageRequest message) {
        // Check if we have an upstream connection
        if (mcpServer.getMessageEndpoint() != null || mcpServer.getServiceEndpoint() != null) {
            // Send to upstream MCP server
            connectionService.sendMessageToUpstream(sessionId, mcpServer, message);
        } else {
            // Fallback to local processing for testing
            SseEmitter emitter = sseConnections.get(sessionId);
            if (emitter != null) {
                try {
                    String response = processMessage(sessionId, mcpServer, message);
                    emitter.send(SseEmitter.event()
                        .name("message_response")
                        .data(response));
                } catch (Exception e) {
                    log.error("Error sending local SSE message", e);
                    emitter.completeWithError(e);
                }
            }
        }
    }

    private void handleWebSocketMessage(UUID sessionId, McpServer mcpServer, MessageRequest message) {
        // TODO: Implement WebSocket message forwarding to upstream
        log.info("Handling WebSocket message for session: {} to server: {}", sessionId, mcpServer.getServiceName());
    }

    private void handleStreamableHttpMessage(UUID sessionId, McpServer mcpServer, MessageRequest message) {
        // TODO: Implement Streamable HTTP message forwarding to upstream
        log.info("Handling Streamable HTTP message for session: {} to server: {}", sessionId, mcpServer.getServiceName());
    }

    private void handleStdioMessage(UUID sessionId, McpServer mcpServer, MessageRequest message) {
        // TODO: Implement STDIO message forwarding to upstream
        log.info("Handling STDIO message for session: {} to server: {}", sessionId, mcpServer.getServiceName());
    }

    private String processMessage(UUID sessionId, McpServer mcpServer, MessageRequest message) {
        // Local message processing fallback
        return String.format("{\"result\": \"Processed %s for session %s on server %s\"}", 
            message.getEffectiveType(), sessionId, mcpServer.getServiceName());
    }

    private String generateSessionToken() {
        return "session_" + UUID.randomUUID().toString().replace("-", "");
    }
} 