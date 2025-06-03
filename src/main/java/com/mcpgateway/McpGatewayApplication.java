package com.mcpgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
    info = @Info(
        title = "MCP Gateway API",
        version = "1.0.0",
        description = "Spring Boot implementation of the MCP Gateway service"
    )
)
public class McpGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }
} 