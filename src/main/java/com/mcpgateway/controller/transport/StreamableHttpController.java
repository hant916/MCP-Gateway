package com.mcpgateway.controller.transport;

import com.mcpgateway.domain.Session;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.transport.StreamableHttpTransport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/transport/streamable-http")
@RequiredArgsConstructor
@Tag(name = "Streamable HTTP Transport", description = "Streamable HTTP transport endpoints")
@SecurityRequirement(name = "bearerAuth")
public class StreamableHttpController {

    private final StreamableHttpTransport streamableHttpTransport;
    private final SessionService sessionService;

    @GetMapping("/{sessionId}")
    @Operation(summary = "Subscribe to streamable HTTP events for a session")
    public ResponseEntity<StreamingResponseBody> subscribe(@PathVariable UUID sessionId) {
        Session session = sessionService.getSession(sessionId);
        streamableHttpTransport.initialize(session);
        return ResponseEntity.ok()
                .contentType(streamableHttpTransport.getMediaType())
                .body(streamableHttpTransport.getStreamingResponse(session.getSessionToken()));
    }

    @PostMapping("/{sessionId}/message")
    @Operation(summary = "Send a message through streamable HTTP transport")
    public void sendMessage(@PathVariable UUID sessionId, @RequestBody String message) {
        Session session = sessionService.getSession(sessionId);
        streamableHttpTransport.sendMessage(message);
    }
} 