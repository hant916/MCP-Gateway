package com.mcpgateway.dto.mcpserver;

import com.mcpgateway.domain.McpServer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class RegisterMcpServerRequest {
    @NotBlank(message = "Service name is required")
    private String serviceName;

    private String description;
    private String iconUrl;
    private String repositoryUrl;

    @Valid
    @NotNull(message = "Transport configuration is required")
    private TransportConfig transport;

    @Valid
    private AuthConfig authentication;

    @Data
    public static class TransportConfig {
        @NotNull(message = "Transport type is required")
        private McpServer.TransportType type;

        @NotNull(message = "Transport configuration is required")
        private TransportEndpoints config;
    }

    @Data
    public static class TransportEndpoints {
        @NotBlank(message = "Service endpoint is required")
        private String serviceEndpoint;
        
        private String messageEndpoint;
        
        private McpServer.SessionIdLocationType sessionIdLocation;
        private String sessionIdParamName;
    }

    @Data
    public static class AuthConfig {
        @NotNull(message = "Authentication type is required")
        private McpServer.AuthType type;
        
        private OAuth2Config config;
    }

    @Data
    public static class OAuth2Config {
        private String clientId;
        private String clientSecret;
        private String authorizationUrl;
        private String tokenUrl;
        private Set<String> scopes;
    }
} 