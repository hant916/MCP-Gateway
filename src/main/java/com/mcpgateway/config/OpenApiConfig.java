package com.mcpgateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Enhanced OpenAPI/Swagger configuration
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI mcpGatewayOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers())
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme()))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private Info apiInfo() {
        return new Info()
                .title("MCP Gateway API")
                .version("1.0.0")
                .description("""
                        # MCP Gateway API Documentation

                        ## Overview
                        MCP Gateway is a comprehensive platform for managing and executing Model Context Protocol (MCP) tools.
                        This API provides endpoints for:

                        - **Authentication** - User registration, login, and JWT token management
                        - **MCP Tool Management** - Create, configure, and manage MCP tools
                        - **Session Management** - Handle MCP sessions across multiple transports (SSE, WebSocket, HTTP, STDIO)
                        - **Tool Execution** - Execute MCP tools with quota tracking and billing
                        - **Marketplace** - Browse, subscribe, and review tools
                        - **Analytics** - Usage statistics, revenue analysis, and reporting
                        - **Admin** - User and quota management (admin only)
                        - **Payments** - Stripe integration for tool subscriptions
                        - **Webhooks** - Configure webhook notifications for events

                        ## Authentication
                        Most endpoints require authentication using JWT Bearer tokens.
                        Include the token in the `Authorization` header:
                        ```
                        Authorization: Bearer <your-jwt-token>
                        ```

                        ## Rate Limiting
                        All endpoints are rate-limited based on subscription tier:
                        - **FREE**: 100 requests/month
                        - **BASIC**: 1,000 requests/month
                        - **PRO**: 10,000 requests/month
                        - **ENTERPRISE**: Unlimited

                        ## Pagination
                        List endpoints support pagination with `page` and `size` parameters:
                        - `page`: Page number (0-indexed)
                        - `size`: Items per page (default: 20)

                        ## Error Responses
                        Errors follow a standard format:
                        ```json
                        {
                          "timestamp": "2024-01-01T12:00:00",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Invalid parameter",
                          "path": "/api/v1/resource"
                        }
                        ```

                        ## Monitoring
                        Prometheus metrics available at `/actuator/prometheus`
                        Health check available at `/actuator/health`
                        """)
                .contact(new Contact()
                        .name("MCP Gateway Team")
                        .email("support@mcpgateway.com")
                        .url("https://mcpgateway.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> apiServers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Development Server"),
                new Server()
                        .url("https://api.mcpgateway.com")
                        .description("Production Server")
        );
    }

    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT authentication token. Obtain from /api/v1/auth/login endpoint.");
    }
}
