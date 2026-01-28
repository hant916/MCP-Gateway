package com.mcpgateway.stream;

import com.mcpgateway.stream.policy.DeliveryMode;
import com.mcpgateway.stream.upstream.LlmRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * StreamSafe Controller - the HTTP interface for streaming.
 *
 * Endpoints:
 * - POST /api/v1/stream/chat - Start a streaming chat request
 * - GET /api/v1/stream/result/{requestId} - Get async job result
 * - GET /api/v1/stream/result/{requestId}?cursor=N - Get with pagination
 * - GET /api/v1/stream/status - Get streaming service status
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
@Tag(name = "StreamSafe", description = "Streaming Reliability Mode - reliable content delivery")
public class StreamController {

    private final StreamService streamService;
    private final StreamMetrics streamMetrics;

    /**
     * Start a streaming chat request.
     *
     * The delivery mode is automatically decided based on:
     * - Client type (browser, CLI, SDK)
     * - Entry topology (direct, API gateway, CDN)
     * - Expected latency
     *
     * Possible responses:
     * - SSE stream (Content-Type: text/event-stream)
     * - Async job acknowledgment (Content-Type: application/json)
     * - Sync complete response (Content-Type: application/json)
     */
    @PostMapping(value = "/chat", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Start a streaming chat request",
               description = "Automatically selects the best delivery mode based on client and network conditions")
    public ResponseEntity<?> chat(
            HttpServletRequest request,
            @RequestBody @Valid ChatRequest chatRequest) {

        LlmRequest llmRequest = toLlmRequest(chatRequest);
        StreamService.StreamResult result = streamService.startStream(request, llmRequest);

        // Add delivery mode headers
        return switch (result.mode()) {
            case SSE_DIRECT -> ResponseEntity.ok()
                    .header("X-Delivery-Mode", "SSE_DIRECT")
                    .header("X-Delivery-Reason", result.reason())
                    .header("X-Request-Id", result.requestId())
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(result.response());

            case ASYNC_JOB -> ResponseEntity.accepted()
                    .header("X-Delivery-Mode", "ASYNC_JOB")
                    .header("X-Delivery-Reason", result.reason())
                    .header("X-Request-Id", result.requestId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.response());

            case SYNC -> ResponseEntity.ok()
                    .header("X-Delivery-Mode", "SYNC")
                    .header("X-Delivery-Reason", result.reason())
                    .header("X-Request-Id", result.requestId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.response());

            case WS_PUSH -> ResponseEntity.badRequest()
                    .body(Map.of("error", "WebSocket streaming should use /ws endpoint"));
        };
    }

    /**
     * Get async job result.
     */
    @GetMapping("/result/{requestId}")
    @Operation(summary = "Get async job result",
               description = "Retrieve the result of an async streaming job")
    public ResponseEntity<?> getResult(
            @PathVariable String requestId,
            @Parameter(description = "Cursor for pagination (token sequence number)")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "Maximum number of tokens to return")
            @RequestParam(required = false, defaultValue = "0") int limit) {

        if (cursor != null) {
            // Paginated result
            StreamReplay.ReplayResult result = streamService.getAsyncJobResultWithCursor(requestId, cursor, limit);
            if (!result.found()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of(
                    "requestId", result.requestId(),
                    "tokens", result.tokens().stream()
                            .filter(t -> t.getType() == StreamToken.TokenType.TEXT)
                            .map(t -> Map.of(
                                    "sequence", t.getSequence(),
                                    "text", t.getText()
                            ))
                            .toList(),
                    "cursor", result.cursor(),
                    "nextCursor", result.nextCursor(),
                    "hasMore", result.hasMore(),
                    "completed", result.completed()
            ));
        }

        // Full result
        StreamReplay.AsyncJobResult result = streamService.getAsyncJobResult(requestId);

        return switch (result.status()) {
            case "not_found" -> ResponseEntity.notFound().build();
            case "pending" -> ResponseEntity.accepted()
                    .body(Map.of(
                            "status", "pending",
                            "requestId", requestId
                    ));
            case "completed" -> ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "requestId", requestId,
                    "content", result.content(),
                    "tokenCount", result.tokenCount(),
                    "durationMs", result.durationMs()
            ));
            case "failed" -> ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "requestId", requestId,
                    "error", result.errorMessage()
            ));
            default -> ResponseEntity.internalServerError().build();
        };
    }

    /**
     * Get streaming service status.
     */
    @GetMapping("/status")
    @Operation(summary = "Get streaming service status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "activeSessions", streamService.getActiveSessionCount(),
                "sessionsByMode", Map.of(
                        "SSE_DIRECT", streamMetrics.getActiveSessionCount(DeliveryMode.SSE_DIRECT),
                        "ASYNC_JOB", streamMetrics.getActiveSessionCount(DeliveryMode.ASYNC_JOB),
                        "WS_PUSH", streamMetrics.getActiveSessionCount(DeliveryMode.WS_PUSH),
                        "SYNC", streamMetrics.getActiveSessionCount(DeliveryMode.SYNC)
                ),
                "healthy", true
        ));
    }

    /**
     * Force a specific delivery mode (for testing).
     */
    @PostMapping("/chat/force/{mode}")
    @Operation(summary = "Start a chat with forced delivery mode (for testing)")
    public ResponseEntity<?> chatForceMode(
            HttpServletRequest request,
            @PathVariable String mode,
            @RequestBody @Valid ChatRequest chatRequest) {

        // This is a simplified version for testing
        // In production, you might want to restrict this endpoint
        log.info("Forced delivery mode requested: {}", mode);

        LlmRequest llmRequest = toLlmRequest(chatRequest);
        StreamService.StreamResult result = streamService.startStream(request, llmRequest);

        return ResponseEntity.ok()
                .header("X-Delivery-Mode", result.mode().name())
                .header("X-Forced-Mode", mode)
                .body(result.response());
    }

    private LlmRequest toLlmRequest(ChatRequest chatRequest) {
        List<LlmRequest.Message> messages = chatRequest.getMessages().stream()
                .map(m -> LlmRequest.Message.builder()
                        .role(LlmRequest.Message.Role.valueOf(m.getRole().toUpperCase()))
                        .content(m.getContent())
                        .build())
                .toList();

        return LlmRequest.builder()
                .model(chatRequest.getModel())
                .messages(messages)
                .maxTokens(chatRequest.getMaxTokens())
                .temperature(chatRequest.getTemperature())
                .stream(chatRequest.isStream())
                .build();
    }

    /**
     * Chat request DTO.
     */
    @Data
    public static class ChatRequest {
        private String model = "mock";
        private List<MessageDto> messages;
        private Integer maxTokens = 1024;
        private Double temperature = 0.7;
        private boolean stream = true;
    }

    @Data
    public static class MessageDto {
        private String role;
        private String content;
    }
}
