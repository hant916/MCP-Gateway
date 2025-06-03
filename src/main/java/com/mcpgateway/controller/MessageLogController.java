package com.mcpgateway.controller;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.dto.message.MessageLogDTO;
import com.mcpgateway.service.MessageLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/message-logs")
@RequiredArgsConstructor
@Tag(name = "Message Logs", description = "Message logging APIs")
@SecurityRequirement(name = "bearerAuth")
public class MessageLogController {

    private final MessageLogService messageLogService;

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Get all messages for a session")
    public ResponseEntity<List<MessageLogDTO>> getSessionMessages(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(messageLogService.getSessionMessages(sessionId));
    }

    @GetMapping("/session/{sessionId}/time-range")
    @Operation(summary = "Get messages for a session within a time range")
    public ResponseEntity<List<MessageLogDTO>> getSessionMessagesByTimeRange(
            @PathVariable UUID sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end
    ) {
        return ResponseEntity.ok(messageLogService.getSessionMessagesByTimeRange(sessionId, start, end));
    }

    @GetMapping("/session/{sessionId}/type/{messageType}")
    @Operation(summary = "Get messages for a session by type")
    public ResponseEntity<List<MessageLogDTO>> getSessionMessagesByType(
            @PathVariable UUID sessionId,
            @PathVariable MessageLog.MessageType messageType
    ) {
        return ResponseEntity.ok(messageLogService.getSessionMessagesByType(sessionId, messageType));
    }
} 