package com.mcpgateway.dto.mcpserver;

import com.mcpgateway.domain.McpServer;
import lombok.Data;

import java.util.UUID;

@Data
public class McpServerResponse {
    private UUID serverId;
    private McpServer.ServerStatus status;
    private String serviceUrl;
    
    public static McpServerResponse fromEntity(McpServer server, String baseUrl) {
        McpServerResponse response = new McpServerResponse();
        response.setServerId(server.getId());
        response.setStatus(server.getStatus());
        response.setServiceUrl(baseUrl + "/mcp-server/" + server.getId());
        return response;
    }
} 