package com.mcpgateway.controller;

import com.mcpgateway.domain.User;
import com.mcpgateway.dto.session.CreateSessionRequest;
import com.mcpgateway.dto.session.SessionDTO;
import com.mcpgateway.ratelimit.RateLimit;
import com.mcpgateway.service.McpServerService;
import com.mcpgateway.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mcp-server/{serverId}/sessions")
@RequiredArgsConstructor
@Tag(name = "MCP Session Management", description = "APIs for creating sessions with MCP servers")
public class McpSessionController {

    private final SessionService sessionService;
    private final McpServerService mcpServerService;

    @PostMapping
    @Operation(summary = "Create a new session for an MCP server")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user",
               errorMessage = "Too many session creation requests. Please try again later.")
    public ResponseEntity<SessionDTO> createSession(
            @PathVariable UUID serverId,
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal User user) {
        
        var mcpServer = mcpServerService.getServer(serverId);
        var session = sessionService.createSession(mcpServer, request.getTransportType(), user);
        var response = SessionDTO.from(session);
        
        return ResponseEntity.ok(response);
    }
} 