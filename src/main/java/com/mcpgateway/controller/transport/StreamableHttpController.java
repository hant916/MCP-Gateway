package com.mcpgateway.controller.transport;

import com.mcpgateway.domain.Session;
import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.service.McpServerConnectionService;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.service.UsageBillingService;
import com.mcpgateway.transport.StreamableHttpTransport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Streamable HTTP Transport", description = "Streamable HTTP transport endpoints for MCP")
@SecurityRequirement(name = "bearerAuth")
public class StreamableHttpController {

    private final McpServerConnectionService connectionService;
    private final SessionService sessionService;
    private final UsageBillingService billingService;

    @GetMapping("/{sessionId}/streamable-http")
    @Operation(summary = "Establish Streamable HTTP connection to upstream MCP server")
    public ResponseEntity<StreamingResponseBody> establishConnection(@PathVariable UUID sessionId) {
        log.info("Establishing Streamable HTTP connection for session: {}", sessionId);
        Session session = sessionService.getSession(sessionId);

        // Record connection establishment usage
        billingService.recordUsageAsync(
            sessionId,
            "/api/v1/sessions/" + sessionId + "/streamable-http",
            "GET",
            200,
            System.currentTimeMillis(),
            null,
            null,
            100
        );

        // Establish upstream connection and get streaming response
        StreamingResponseBody responseBody = connectionService.establishUpstreamStreamableHttpConnection(
            sessionId,
            session.getMcpServer()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(responseBody);
    }

    @PostMapping("/streamable-http/message")
    @Operation(summary = "Send a message to upstream MCP server via Streamable HTTP")
    public ResponseEntity<String> sendMessage(
            @RequestParam UUID sessionId,
            @RequestBody MessageRequest message) {
        log.info("Sending Streamable HTTP message for session: {}", sessionId);
        Session session = sessionService.getSession(sessionId);

        // Record message sending usage
        billingService.recordUsageAsync(
            sessionId,
            "/api/v1/streamable-http/message",
            "POST",
            200,
            System.currentTimeMillis(),
            null,
            null,
            50
        );

        // Send message to upstream
        connectionService.sendMessageToStreamableHttpUpstream(
            sessionId,
            session.getMcpServer(),
            message
        );

        return ResponseEntity.ok("{\"status\":\"Message sent\"}");
    }
} 