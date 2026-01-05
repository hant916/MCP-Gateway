package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.mcpserver.RegisterMcpServerRequest;
import com.mcpgateway.repository.McpServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpServerServiceTest {

    @Mock
    private McpServerRepository mcpServerRepository;

    @InjectMocks
    private McpServerService mcpServerService;

    private User testUser;
    private McpServer testServer;
    private RegisterMcpServerRequest registerRequest;
    private UUID serverId;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");

        serverId = UUID.randomUUID();
        testServer = new McpServer();
        testServer.setId(serverId);
        testServer.setServiceName("Test MCP Server");
        testServer.setDescription("Test Description");
        testServer.setTransportType("sse");
        testServer.setServiceEndpoint("https://example.com/sse");
        testServer.setStatus(McpServer.ServerStatus.REGISTERED);
        testServer.setUser(testUser);

        registerRequest = new RegisterMcpServerRequest();
        registerRequest.setServiceName("Test MCP Server");
        registerRequest.setDescription("Test Description");
        registerRequest.setIconUrl("https://example.com/icon.png");
        registerRequest.setRepositoryUrl("https://github.com/test/repo");

        RegisterMcpServerRequest.TransportConfig transportConfig = new RegisterMcpServerRequest.TransportConfig();
        transportConfig.setType("sse");
        RegisterMcpServerRequest.TransportConfig.Config config = new RegisterMcpServerRequest.TransportConfig.Config();
        config.setServiceEndpoint("https://example.com/sse");
        config.setMessageEndpoint("https://example.com/message");
        config.setSessionIdLocation("path");
        config.setSessionIdParamName("sessionId");
        transportConfig.setConfig(config);
        registerRequest.setTransport(transportConfig);
    }

    @Test
    void registerServer_WithValidRequest_ShouldCreateServer() {
        // Arrange
        when(mcpServerRepository.save(any(McpServer.class))).thenReturn(testServer);

        // Act
        McpServer result = mcpServerService.registerServer(registerRequest, testUser);

        // Assert
        assertNotNull(result);
        assertEquals("Test MCP Server", result.getServiceName());
        assertEquals(McpServer.ServerStatus.REGISTERED, result.getStatus());
        assertEquals(testUser, result.getUser());
        verify(mcpServerRepository).save(any(McpServer.class));
    }

    @Test
    void registerServer_WithAuthentication_ShouldSetAuthConfig() {
        // Arrange
        RegisterMcpServerRequest.AuthenticationConfig authConfig = new RegisterMcpServerRequest.AuthenticationConfig();
        authConfig.setType("oauth2");
        RegisterMcpServerRequest.AuthenticationConfig.Config config = new RegisterMcpServerRequest.AuthenticationConfig.Config();
        config.setClientId("test-client-id");
        config.setClientSecret("test-secret");
        config.setAuthorizationUrl("https://example.com/auth");
        config.setTokenUrl("https://example.com/token");
        config.setScopes("read write");
        authConfig.setConfig(config);
        registerRequest.setAuthentication(authConfig);

        when(mcpServerRepository.save(any(McpServer.class))).thenAnswer(invocation -> {
            McpServer server = invocation.getArgument(0);
            assertEquals("oauth2", server.getAuthType());
            assertEquals("test-client-id", server.getClientId());
            assertEquals("test-secret", server.getClientSecret());
            return server;
        });

        // Act
        McpServer result = mcpServerService.registerServer(registerRequest, testUser);

        // Assert
        verify(mcpServerRepository).save(any(McpServer.class));
    }

    @Test
    void getServer_WithValidId_ShouldReturnServer() {
        // Arrange
        when(mcpServerRepository.findById(serverId)).thenReturn(Optional.of(testServer));

        // Act
        McpServer result = mcpServerService.getServer(serverId);

        // Assert
        assertNotNull(result);
        assertEquals(serverId, result.getId());
        assertEquals("Test MCP Server", result.getServiceName());
        verify(mcpServerRepository).findById(serverId);
    }

    @Test
    void getServer_WithNonExistentId_ShouldThrowException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(mcpServerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> mcpServerService.getServer(nonExistentId)
        );
        assertEquals("MCP Server not found", exception.getMessage());
    }

    @Test
    void updateServerStatus_WithValidId_ShouldUpdateStatus() {
        // Arrange
        when(mcpServerRepository.findById(serverId)).thenReturn(Optional.of(testServer));
        when(mcpServerRepository.save(any(McpServer.class))).thenReturn(testServer);

        // Act
        mcpServerService.updateServerStatus(serverId, McpServer.ServerStatus.ACTIVE);

        // Assert
        verify(mcpServerRepository).findById(serverId);
        verify(mcpServerRepository).save(argThat(server ->
            server.getStatus() == McpServer.ServerStatus.ACTIVE
        ));
    }

    @Test
    void updateServerStatus_WithNonExistentServer_ShouldThrowException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(mcpServerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> mcpServerService.updateServerStatus(nonExistentId, McpServer.ServerStatus.ACTIVE)
        );
        verify(mcpServerRepository, never()).save(any());
    }

    @Test
    void testConnection_WithValidServerId_ShouldNotThrowException() {
        // Arrange
        when(mcpServerRepository.findById(serverId)).thenReturn(Optional.of(testServer));

        // Act & Assert
        assertDoesNotThrow(() -> mcpServerService.testConnection(serverId));
        verify(mcpServerRepository).findById(serverId);
    }
}
