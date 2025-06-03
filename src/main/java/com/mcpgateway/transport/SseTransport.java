package com.mcpgateway.transport;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.service.MessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseTransport implements McpTransport {
    private final MessageLogService messageLogService;
    private static final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private Session session;

    @Override
    public void initialize(Session session) {
        this.session = session;
        SseEmitter emitter = new SseEmitter(0L); // No timeout
        
        emitter.onCompletion(() -> {
            emitters.remove(session.getSessionToken());
            log.info("SSE connection completed for session: {}", session.getSessionToken());
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(session.getSessionToken());
            log.info("SSE connection timed out for session: {}", session.getSessionToken());
        });
        
        emitters.put(session.getSessionToken(), emitter);
    }

    @Override
    public void sendMessage(String message) {
        SseEmitter emitter = emitters.get(session.getSessionToken());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .data(message)
                        .id(String.valueOf(System.currentTimeMillis()))
                        .build());
                messageLogService.logMessage(session.getId(), MessageLog.MessageType.REQUEST, message);
            } catch (IOException e) {
                log.error("Failed to send SSE message", e);
                messageLogService.logMessage(session.getId(), MessageLog.MessageType.ERROR,
                        "Failed to send SSE message: " + e.getMessage());
                emitters.remove(session.getSessionToken());
            }
        }
    }

    @Override
    public void handleMessage(String message) {
        messageLogService.logMessage(session.getId(), MessageLog.MessageType.RESPONSE, message);
    }

    @Override
    public void close() {
        SseEmitter emitter = emitters.remove(session.getSessionToken());
        if (emitter != null) {
            emitter.complete();
        }
    }

    public SseEmitter getEmitter(String sessionToken) {
        return emitters.get(sessionToken);
    }
} 