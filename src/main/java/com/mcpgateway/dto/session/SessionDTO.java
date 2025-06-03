package com.mcpgateway.dto.session;

import com.mcpgateway.domain.Session;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class SessionDTO {
    private UUID sessionId;
    private Session.TransportType transportType;
    private Session.SessionStatus status;
    private Map<String, String> endpoints;
    private Instant expiresAt;

    public static SessionDTO from(Session session) {
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getId());
        dto.setTransportType(session.getTransportType());
        dto.setStatus(session.getStatus());
        dto.setExpiresAt(session.getExpiresAt().toInstant());
        
        // Generate endpoints based on transport type
        String baseUrl = "/api/v1/sessions/" + session.getId();
        switch (session.getTransportType()) {
            case SSE:
                dto.setEndpoints(Map.of(
                    "sse", baseUrl + "/sse",
                    "message", baseUrl + "/sse/message"
                ));
                break;
            case WEBSOCKET:
                dto.setEndpoints(Map.of(
                    "websocket", baseUrl + "/ws"
                ));
                break;
            case STREAMABLE_HTTP:
                dto.setEndpoints(Map.of(
                    "streamable", baseUrl + "/streamable-http"
                ));
                break;
            case STDIO:
                dto.setEndpoints(Map.of(
                    "stdio", baseUrl + "/stdio"
                ));
                break;
        }
        
        return dto;
    }
} 