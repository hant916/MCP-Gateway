package com.mcpgateway.controller;

import com.mcpgateway.dto.session.MessageRequest;
import com.mcpgateway.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Session Transport", description = "APIs for different transport protocols")
public class SessionTransportController {

    private final SessionService sessionService;

    // Session-specific endpoints (with sessionId in path)
    @GetMapping("/sessions/{sessionId}/sse")
    @Operation(summary = "Establish SSE connection")
    public SseEmitter establishSseConnection(@PathVariable UUID sessionId) {
        return sessionService.createSseConnection(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/sse/message")
    @Operation(summary = "Send message via SSE (path-based)")
    public ResponseEntity<Map<String, String>> sendSseMessagePath(
            @PathVariable UUID sessionId,
            @RequestBody MessageRequest message) {
        sessionService.sendMessage(sessionId, message);
        return ResponseEntity.ok(Map.of("status", "Message sent"));
    }

    // Standalone SSE message endpoint (with sessionId as query parameter)
    @PostMapping("/sse/message")
    @Operation(summary = "Send message via SSE (query-based)")
    public ResponseEntity<Map<String, String>> sendSseMessage(
            @RequestParam UUID sessionId,
            @RequestBody MessageRequest message) {
        sessionService.sendMessage(sessionId, message);
        return ResponseEntity.ok(Map.of("status", "Message sent"));
    }

    @PostMapping("/sessions/{sessionId}/streamable-http")
    @Operation(summary = "Handle streamable HTTP request")
    public ResponseEntity<StreamingResponseBody> handleStreamableHttp(
            @PathVariable UUID sessionId,
            @RequestBody MessageRequest message) {
        StreamingResponseBody stream = sessionService.createStreamingResponse(sessionId, message);
        return ResponseEntity.ok(stream);
    }

    @GetMapping("/sessions/{sessionId}/status")
    @Operation(summary = "Get session status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(@PathVariable UUID sessionId) {
        var session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(Map.of(
            "sessionId", session.getId(),
            "status", session.getStatus(),
            "transportType", session.getTransportType(),
            "isExpired", session.isExpired()
        ));
    }
} 