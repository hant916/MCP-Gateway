package com.mcpgateway.stream.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.stream.StreamToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket transport implementation.
 *
 * Features:
 * - Full duplex communication
 * - Supports reconnection with token replay
 * - Token sequence for ordering
 */
@Slf4j
public class WebSocketStreamTransport implements StreamTransport {

    private final WebSocketSession session;
    private final String requestId;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean connected = new AtomicBoolean(true);

    public WebSocketStreamTransport(WebSocketSession session, String requestId, ObjectMapper objectMapper) {
        this.session = session;
        this.requestId = requestId;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(StreamToken token) throws TransportException {
        if (!isConnected()) {
            throw new TransportException("WebSocket connection closed");
        }

        try {
            String json = toJson(token);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            connected.set(false);
            throw new TransportException("Failed to send WebSocket message", e);
        }
    }

    @Override
    public void flush() throws TransportException {
        // WebSocket sends are typically unbuffered
        // This is a no-op but kept for interface consistency
    }

    @Override
    public void close() {
        if (connected.compareAndSet(true, false)) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                log.warn("Error closing WebSocket session: requestId={}", requestId, e);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get() && session.isOpen();
    }

    @Override
    public String getType() {
        return "WebSocket";
    }

    private String toJson(StreamToken token) throws TransportException {
        try {
            Map<String, Object> message = Map.of(
                    "type", token.getType().name().toLowerCase(),
                    "sequence", token.getSequence(),
                    "data", token.getText() != null ? token.getText() : "",
                    "timestamp", token.getTimestamp().toEpochMilli()
            );
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new TransportException("Failed to serialize token", e);
        }
    }
}
