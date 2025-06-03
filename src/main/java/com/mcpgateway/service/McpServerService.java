package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.mcpserver.RegisterMcpServerRequest;
import com.mcpgateway.repository.McpServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpServerService {
    private final McpServerRepository mcpServerRepository;

    @Transactional
    public McpServer registerServer(RegisterMcpServerRequest request, User user) {
        McpServer server = new McpServer();
        server.setServiceName(request.getServiceName());
        server.setDescription(request.getDescription());
        server.setIconUrl(request.getIconUrl());
        server.setRepositoryUrl(request.getRepositoryUrl());
        
        // Set transport configuration
        server.setTransportType(request.getTransport().getType());
        server.setServiceEndpoint(request.getTransport().getConfig().getServiceEndpoint());
        server.setMessageEndpoint(request.getTransport().getConfig().getMessageEndpoint());
        server.setSessionIdLocation(request.getTransport().getConfig().getSessionIdLocation());
        server.setSessionIdParamName(request.getTransport().getConfig().getSessionIdParamName());
        
        // Set authentication configuration if provided
        if (request.getAuthentication() != null) {
            server.setAuthType(request.getAuthentication().getType());
            if (request.getAuthentication().getConfig() != null) {
                server.setClientId(request.getAuthentication().getConfig().getClientId());
                server.setClientSecret(request.getAuthentication().getConfig().getClientSecret());
                server.setAuthorizationUrl(request.getAuthentication().getConfig().getAuthorizationUrl());
                server.setTokenUrl(request.getAuthentication().getConfig().getTokenUrl());
                server.setScopes(request.getAuthentication().getConfig().getScopes());
            }
        }
        
        server.setStatus(McpServer.ServerStatus.REGISTERED);
        server.setUser(user);
        
        return mcpServerRepository.save(server);
    }

    @Transactional(readOnly = true)
    public McpServer getServer(UUID serverId) {
        return mcpServerRepository.findById(serverId)
            .orElseThrow(() -> new RuntimeException("MCP Server not found"));
    }

    @Transactional
    public void testConnection(UUID serverId) {
        McpServer server = getServer(serverId);
        // Implement connection testing logic here
        // This could include:
        // 1. Testing the transport connection
        // 2. Validating authentication
        // 3. Checking tool availability
    }

    @Transactional
    public void updateServerStatus(UUID serverId, McpServer.ServerStatus status) {
        McpServer server = getServer(serverId);
        server.setStatus(status);
        mcpServerRepository.save(server);
    }
} 