package com.mcpgateway.controller;

import com.mcpgateway.service.McpStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/mcp/stream")
@RequiredArgsConstructor
public class McpStreamController {
    private final McpStreamService streamService;

    /**
     * SSE 方式的流式响应
     * curl -N -H "X-API-KEY: your_api_key" http://localhost:8080/mcp/stream/sse/{toolId}
     */
    @GetMapping(value = "/sse/{toolId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSSE(
            @PathVariable UUID toolId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        
        streamService.handleSSE(toolId, userId, emitter, event -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(event));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        
        emitter.onCompletion(() -> streamService.cleanup(toolId));
        emitter.onTimeout(() -> streamService.cleanup(toolId));
        emitter.onError(e -> streamService.cleanup(toolId));
        
        return emitter;
    }

    /**
     * HTTP Streaming 方式的流式响应
     * curl -N -H "X-API-KEY: your_api_key" http://localhost:8080/mcp/stream/http/{toolId}
     */
    @GetMapping(value = "/http/{toolId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamHTTP(
            @PathVariable UUID toolId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        StreamingResponseBody responseBody = response -> {
            streamService.handleHTTPStream(toolId, userId, chunk -> {
                try {
                    response.write(chunk.getBytes());
                    response.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Error writing to stream", e);
                }
            });
        };
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(responseBody);
    }

    /**
     * WebFlux 方式的响应式流
     * curl -N -H "X-API-KEY: your_api_key" http://localhost:8080/mcp/stream/reactive/{toolId}
     */
    @GetMapping(value = "/reactive/{toolId}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> streamReactive(
            @PathVariable UUID toolId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return streamService.handleReactiveStream(toolId, userId);
    }
} 