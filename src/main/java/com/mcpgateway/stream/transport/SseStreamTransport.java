package com.mcpgateway.stream.transport;

import com.mcpgateway.stream.StreamToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE (Server-Sent Events) transport implementation.
 *
 * HARD RULES:
 * 1. First byte (: or data:) must arrive < 1s
 * 2. Every chunk immediately flushed
 * 3. Flush failure → immediate abort → fallback
 */
@Slf4j
public class SseStreamTransport implements StreamTransport {

    private final SseEmitter emitter;
    private final String requestId;
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicBoolean firstByteSent = new AtomicBoolean(false);

    public SseStreamTransport(SseEmitter emitter, String requestId) {
        this.emitter = emitter;
        this.requestId = requestId;

        // Set up completion/error handlers
        emitter.onCompletion(() -> {
            connected.set(false);
            log.debug("SSE completed: requestId={}", requestId);
        });

        emitter.onTimeout(() -> {
            connected.set(false);
            log.warn("SSE timeout: requestId={}", requestId);
        });

        emitter.onError(e -> {
            connected.set(false);
            log.error("SSE error: requestId={}", requestId, e);
        });
    }

    @Override
    public void send(StreamToken token) throws TransportException {
        if (!connected.get()) {
            throw new TransportException("SSE connection closed");
        }

        try {
            switch (token.getType()) {
                case START -> {
                    // Send comment to establish connection (counts as first byte)
                    emitter.send(SseEmitter.event().comment("stream-start"));
                    firstByteSent.set(true);
                }
                case TEXT -> {
                    // Send data event
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .id(String.valueOf(token.getSequence()))
                            .data(token.getText());
                    emitter.send(event);

                    if (!firstByteSent.getAndSet(true)) {
                        log.debug("SSE first byte sent: requestId={}", requestId);
                    }
                }
                case END -> {
                    // Send done event
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("[DONE]"));
                }
                case ERROR -> {
                    // Send error event
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(token.getText()));
                }
                case HEARTBEAT -> {
                    // Send comment as heartbeat
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
                case METADATA -> {
                    // Send metadata event
                    emitter.send(SseEmitter.event()
                            .name("metadata")
                            .data(token.getMetadata() != null ? token.getMetadata().toString() : ""));
                }
            }
        } catch (IOException e) {
            connected.set(false);
            throw new TransportException("Failed to send SSE event", e);
        }
    }

    @Override
    public void flush() throws TransportException {
        // SseEmitter auto-flushes on send()
        // But we track this for consistency
        if (!connected.get()) {
            throw new TransportException("Cannot flush: SSE connection closed");
        }
    }

    @Override
    public void close() {
        if (connected.compareAndSet(true, false)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Error completing SSE emitter: requestId={}", requestId, e);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public String getType() {
        return "SSE";
    }

    /**
     * Create an SSE emitter with appropriate timeout.
     */
    public static SseEmitter createEmitter(long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        return emitter;
    }
}
