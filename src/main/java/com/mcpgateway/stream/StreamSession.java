package com.mcpgateway.stream;

import com.mcpgateway.stream.policy.StreamContext;
import com.mcpgateway.stream.policy.StreamDecision;
import com.mcpgateway.stream.transport.StreamTransport;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A streaming session - the core unit of stream management.
 *
 * StreamSession owns:
 * - The buffer
 * - The transport
 * - The lifecycle state
 */
@Data
@Slf4j
public class StreamSession {

    public enum State {
        CREATED,
        STARTING,
        STREAMING,
        PAUSED,
        COMPLETING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String sessionId;
    private final String requestId;
    private final StreamContext context;
    private final StreamDecision decision;
    private final StreamBuffer buffer;
    private final Instant createdAt;

    private volatile State state = State.CREATED;
    private volatile StreamTransport transport;
    private volatile Instant startedAt;
    private volatile Instant firstByteAt;
    private volatile Instant completedAt;
    private volatile String failureReason;
    private volatile Throwable failureCause;

    private final AtomicBoolean firstByteSent = new AtomicBoolean(false);
    private final AtomicInteger chunksDelivered = new AtomicInteger(0);
    private final AtomicLong bytesDelivered = new AtomicLong(0);

    @Builder
    public StreamSession(String requestId, StreamContext context, StreamDecision decision,
                         int bufferMaxTokens, long bufferMaxBytes) {
        this.sessionId = UUID.randomUUID().toString();
        this.requestId = requestId;
        this.context = context;
        this.decision = decision;
        this.buffer = new StreamBuffer(requestId,
                bufferMaxTokens > 0 ? bufferMaxTokens : 10000,
                bufferMaxBytes > 0 ? bufferMaxBytes : 10 * 1024 * 1024);
        this.createdAt = Instant.now();
    }

    /**
     * Start the streaming session.
     */
    public void start(StreamTransport transport) {
        if (state != State.CREATED) {
            throw new IllegalStateException("Session already started: " + state);
        }

        this.transport = transport;
        this.state = State.STARTING;
        this.startedAt = Instant.now();
        this.buffer.start();

        log.debug("Stream session started: sessionId={}, requestId={}, mode={}",
                sessionId, requestId, decision.getMode());
    }

    /**
     * Send a token to the client.
     *
     * @return true if sent successfully, false if failed
     */
    public boolean sendToken(String text) {
        if (state != State.STARTING && state != State.STREAMING) {
            log.warn("Cannot send token in state: {}", state);
            return false;
        }

        state = State.STREAMING;

        // Buffer first
        StreamToken token = buffer.append(text);

        // Check first byte timing
        if (firstByteSent.compareAndSet(false, true)) {
            firstByteAt = Instant.now();
            long ttfbMs = Duration.between(startedAt, firstByteAt).toMillis();
            log.debug("First byte sent: sessionId={}, ttfbMs={}", sessionId, ttfbMs);

            // TTFB > 1s is a streaming failure
            if (ttfbMs > 1000) {
                log.warn("TTFB exceeded 1s threshold: sessionId={}, ttfbMs={}", sessionId, ttfbMs);
            }
        }

        // Deliver via transport
        try {
            if (transport != null) {
                transport.send(token);
                transport.flush();
                chunksDelivered.incrementAndGet();
                bytesDelivered.addAndGet(token.getByteSize());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to send token: sessionId={}", sessionId, e);
            fail("transport_send_failed", e);
        }

        return false;
    }

    /**
     * Send a pre-built token.
     */
    public boolean sendToken(StreamToken token) {
        if (state != State.STARTING && state != State.STREAMING) {
            return false;
        }

        state = State.STREAMING;
        buffer.appendToken(token);

        if (token.getType() == StreamToken.TokenType.TEXT) {
            if (firstByteSent.compareAndSet(false, true)) {
                firstByteAt = Instant.now();
            }
        }

        try {
            if (transport != null) {
                transport.send(token);
                transport.flush();
                if (token.getType() == StreamToken.TokenType.TEXT) {
                    chunksDelivered.incrementAndGet();
                    bytesDelivered.addAndGet(token.getByteSize());
                }
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to send token: sessionId={}", sessionId, e);
            fail("transport_send_failed", e);
        }

        return false;
    }

    /**
     * Complete the session successfully.
     */
    public void complete() {
        if (state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED) {
            return;
        }

        state = State.COMPLETING;
        StreamToken endToken = buffer.complete();

        try {
            if (transport != null && endToken != null) {
                transport.send(endToken);
                transport.flush();
                transport.close();
            }
        } catch (Exception e) {
            log.warn("Error closing transport: sessionId={}", sessionId, e);
        }

        state = State.COMPLETED;
        completedAt = Instant.now();

        log.info("Stream session completed: sessionId={}, requestId={}, chunks={}, bytes={}, duration={}ms",
                sessionId, requestId, chunksDelivered.get(), bytesDelivered.get(),
                Duration.between(startedAt, completedAt).toMillis());
    }

    /**
     * Fail the session.
     */
    public void fail(String reason, Throwable cause) {
        if (state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED) {
            return;
        }

        this.failureReason = reason;
        this.failureCause = cause;
        this.state = State.FAILED;
        this.completedAt = Instant.now();

        StreamToken errorToken = buffer.error(reason);

        try {
            if (transport != null) {
                transport.send(errorToken);
                transport.close();
            }
        } catch (Exception e) {
            log.warn("Error closing transport after failure: sessionId={}", sessionId, e);
        }

        log.error("Stream session failed: sessionId={}, requestId={}, reason={}",
                sessionId, requestId, reason, cause);
    }

    /**
     * Cancel the session (client disconnected).
     */
    public void cancel() {
        if (state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED) {
            return;
        }

        state = State.CANCELLED;
        completedAt = Instant.now();

        try {
            if (transport != null) {
                transport.close();
            }
        } catch (Exception e) {
            log.warn("Error closing transport on cancel: sessionId={}", sessionId, e);
        }

        log.info("Stream session cancelled: sessionId={}, requestId={}", sessionId, requestId);
    }

    /**
     * Get time to first byte in milliseconds.
     */
    public Long getTtfbMs() {
        if (startedAt == null || firstByteAt == null) {
            return null;
        }
        return Duration.between(startedAt, firstByteAt).toMillis();
    }

    /**
     * Get total duration in milliseconds.
     */
    public Long getDurationMs() {
        if (startedAt == null) {
            return null;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(startedAt, end).toMillis();
    }

    /**
     * Check if first byte has been sent within timeout.
     */
    public boolean isFirstByteTimedOut(Duration timeout) {
        if (startedAt == null) {
            return false;
        }
        if (firstByteSent.get()) {
            return false;
        }
        return Duration.between(startedAt, Instant.now()).compareTo(timeout) > 0;
    }
}
