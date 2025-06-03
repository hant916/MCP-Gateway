package com.mcpgateway.controller;

import com.mcpgateway.domain.User;
import com.mcpgateway.dto.mcpserver.McpServerResponse;
import com.mcpgateway.dto.mcpserver.RegisterMcpServerRequest;
import com.mcpgateway.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mcp-server")
@RequiredArgsConstructor
@Tag(name = "MCP Server Configuration", description = "APIs for managing MCP server configurations")
@SecurityRequirement(name = "bearerAuth")
public class McpServerController {

    private final McpServerService mcpServerService;

    @PostMapping("/register")
    @Operation(summary = "Register a new MCP server configuration")
    public ResponseEntity<McpServerResponse> registerServer(
            @Valid @RequestBody RegisterMcpServerRequest request,
            @AuthenticationPrincipal User user) {
        var server = mcpServerService.registerServer(request, user);
        var response = McpServerResponse.fromEntity(server, "/api/v1");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{serverId}/test-connection")
    @Operation(summary = "Test connection to MCP server")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable UUID serverId) {
        mcpServerService.testConnection(serverId);
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Successfully connected to MCP service",
            "details", Map.of(
                "transportStatus", "CONNECTED",
                "authStatus", "AUTHENTICATED"
            )
        ));
    }

    @GetMapping("/{serverId}")
    @Operation(summary = "Get MCP server configuration details")
    public ResponseEntity<McpServerResponse> getServerConfig(@PathVariable UUID serverId) {
        var server = mcpServerService.getServer(serverId);
        var response = McpServerResponse.fromEntity(server, "/api/v1");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{serverId}/status")
    @Operation(summary = "Update MCP server status")
    public ResponseEntity<Map<String, Object>> updateServerStatus(
            @PathVariable UUID serverId,
            @RequestBody Map<String, String> request) {
        mcpServerService.updateServerStatus(serverId, 
            com.mcpgateway.domain.McpServer.ServerStatus.valueOf(request.get("status")));
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Server status updated successfully"
        ));
    }

    @GetMapping("/{serverId}/status")
    @Operation(summary = "Get MCP server status")
    public ResponseEntity<Map<String, Object>> getServerStatus(@PathVariable UUID serverId) {
        var server = mcpServerService.getServer(serverId);
        return ResponseEntity.ok(Map.of(
            "serverId", server.getId(),
            "status", server.getStatus(),
            "transportType", server.getTransportType(),
            "serviceEndpoint", server.getServiceEndpoint()
        ));
    }
} 