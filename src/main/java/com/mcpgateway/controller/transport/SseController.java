package com.mcpgateway.controller.transport;

import com.mcpgateway.domain.Session;
import com.mcpgateway.service.SessionService;
import com.mcpgateway.transport.SseTransport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/transport/sse")
@RequiredArgsConstructor
@Tag(name = "SSE Transport", description = "Server-Sent Events transport endpoints")
@SecurityRequirement(name = "bearerAuth")
public class SseController {

    private final SseTransport sseTransport;
    private final SessionService sessionService;

    @GetMapping(value = "/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to SSE events for a session")
    public SseEmitter subscribe(@PathVariable UUID sessionId) {
        Session session = sessionService.getSession(sessionId);
        sseTransport.initialize(session);
        return sseTransport.getEmitter(session.getSessionToken());
    }

    @PostMapping("/{sessionId}/message")
    @Operation(summary = "Send a message through SSE transport")
    public void sendMessage(@PathVariable UUID sessionId, @RequestBody String message) {
        Session session = sessionService.getSession(sessionId);
        sseTransport.sendMessage(message);
    }
} 