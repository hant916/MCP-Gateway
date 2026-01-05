package com.mcpgateway.controller.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.Session;
import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.service.McpServerConnectionService;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.service.UsageBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "STDIO Transport", description = "STDIO transport endpoints for MCP")
@SecurityRequirement(name = "bearerAuth")
public class StdioController {

    private final McpServerConnectionService connectionService;
    private final SessionService sessionService;
    private final UsageBillingService billingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/{sessionId}/stdio")
    @Operation(summary = "Establish STDIO connection to upstream MCP server process")
    public ResponseEntity<StreamingResponseBody> establishConnection(@PathVariable UUID sessionId) {
        log.info("Establishing STDIO connection for session: {}", sessionId);
        Session session = sessionService.getSession(sessionId);

        // Record connection establishment usage
        billingService.recordUsageAsync(
            sessionId,
            "/api/v1/sessions/" + sessionId + "/stdio",
            "GET",
            200,
            System.currentTimeMillis(),
            null,
            null,
            100
        );

        // Create a queue for messages from upstream process
        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

        // Establish upstream STDIO connection
        connectionService.establishUpstreamStdioConnection(
            sessionId,
            session.getMcpServer(),
            messageQueue
        );

        // Return streaming response body
        StreamingResponseBody responseBody = outputStream -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String message = messageQueue.take();
                    outputStream.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("STDIO streaming interrupted for session: {}", sessionId);
            } catch (IOException e) {
                log.error("Error writing STDIO response", e);
            } finally {
                connectionService.closeUpstreamConnectionByType(
                    sessionId,
                    Session.TransportType.STDIO
                );
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(responseBody);
    }

    @PostMapping("/stdio/message")
    @Operation(summary = "Send a message to upstream MCP server via STDIO")
    public ResponseEntity<String> sendMessage(
            @RequestParam UUID sessionId,
            @RequestBody MessageRequest message) {
        log.info("Sending STDIO message for session: {}", sessionId);

        // Record message sending usage
        billingService.recordUsageAsync(
            sessionId,
            "/api/v1/stdio/message",
            "POST",
            200,
            System.currentTimeMillis(),
            null,
            null,
            50
        );

        // Send message to upstream
        connectionService.sendMessageToStdioUpstream(sessionId, message);

        return ResponseEntity.ok("{\"status\":\"Message sent to STDIO process\"}");
    }

    @DeleteMapping("/{sessionId}/stdio")
    @Operation(summary = "Close STDIO connection")
    public ResponseEntity<String> closeConnection(@PathVariable UUID sessionId) {
        log.info("Closing STDIO connection for session: {}", sessionId);

        connectionService.closeUpstreamConnectionByType(
            sessionId,
            Session.TransportType.STDIO
        );

        return ResponseEntity.ok("{\"status\":\"STDIO connection closed\"}");
    }
}
