package com.mcpgateway.dto.mcpserver;

import com.mcpgateway.domain.McpServer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Arrays;
import java.util.LinkedHashSet;
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

        public void setType(McpServer.TransportType type) {
            this.type = type;
        }

        public void setType(String type) {
            if (type == null || type.isBlank()) {
                this.type = null;
                return;
            }
            String normalized = type.trim().toUpperCase().replace('-', '_');
            this.type = McpServer.TransportType.valueOf(normalized);
        }

        /**
         * Backward-compatible alias for older tests/code.
         */
        public static class Config extends TransportEndpoints {
        }
    }

    @Data
    public static class TransportEndpoints {
        @NotBlank(message = "Service endpoint is required")
        private String serviceEndpoint;
        
        private String messageEndpoint;
        
        private McpServer.SessionIdLocationType sessionIdLocation;
        private String sessionIdParamName;

        public void setSessionIdLocation(McpServer.SessionIdLocationType sessionIdLocation) {
            this.sessionIdLocation = sessionIdLocation;
        }

        public void setSessionIdLocation(String sessionIdLocation) {
            if (sessionIdLocation == null || sessionIdLocation.isBlank()) {
                this.sessionIdLocation = null;
                return;
            }
            String normalized = sessionIdLocation.trim().toUpperCase().replace('-', '_');
            this.sessionIdLocation = McpServer.SessionIdLocationType.valueOf(normalized);
        }
    }

    @Data
    public static class AuthConfig {
        @NotNull(message = "Authentication type is required")
        private McpServer.AuthType type;
        
        private OAuth2Config config;

        public void setType(McpServer.AuthType type) {
            this.type = type;
        }

        public void setType(String type) {
            if (type == null || type.isBlank()) {
                this.type = null;
                return;
            }
            String normalized = type.trim().toUpperCase().replace('-', '_');
            this.type = McpServer.AuthType.valueOf(normalized);
        }
    }

    @Data
    public static class OAuth2Config {
        private String clientId;
        private String clientSecret;
        private String authorizationUrl;
        private String tokenUrl;
        private Set<String> scopes;

        public void setScopes(Set<String> scopes) {
            this.scopes = scopes;
        }

        public void setScopes(String scopes) {
            if (scopes == null || scopes.isBlank()) {
                this.scopes = null;
                return;
            }
            this.scopes = new LinkedHashSet<>(
                Arrays.asList(scopes.trim().split("[,\\s]+"))
            );
        }
    }

    /**
     * Backward-compatible alias for older tests/code.
     */
    public static class AuthenticationConfig extends AuthConfig {
        public static class Config extends OAuth2Config {
        }
    }
} 
