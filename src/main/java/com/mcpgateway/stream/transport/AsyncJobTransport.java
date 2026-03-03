package com.mcpgateway.stream.transport;

import com.mcpgateway.stream.StreamBuffer;
import com.mcpgateway.stream.StreamToken;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async Job transport - doesn't actually deliver to client.
 *
 * Instead, it buffers all tokens for later retrieval via polling.
 * This is the SAFEST delivery mode - works through any proxy.
 *
 * Client receives:
 * {
 *   "status": "accepted",
 *   "requestId": "req_123"
 * }
 *
 * Then polls:
 * GET /result/{requestId}
 * GET /result/{requestId}?cursor=...
 */
@Slf4j
public class AsyncJobTransport implements StreamTransport {

    private final String requestId;
    private final StreamBuffer buffer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AsyncJobTransport(String requestId, StreamBuffer buffer) {
        this.requestId = requestId;
        this.buffer = buffer;
    }

    @Override
    public void send(StreamToken token) throws TransportException {
        if (closed.get()) {
            throw new TransportException("Async job transport closed");
        }

        // Just buffer - no actual delivery
        // Buffer is already managed by StreamSession
        log.trace("Async job buffered token: requestId={}, sequence={}",
                requestId, token.getSequence());
    }

    @Override
    public void flush() throws TransportException {
        // No-op - async jobs don't flush to client
    }

    @Override
    public void close() {
        closed.set(true);
        log.debug("Async job transport closed: requestId={}", requestId);
    }

    @Override
    public boolean isConnected() {
        // Always "connected" as long as not closed
        return !closed.get();
    }

    @Override
    public String getType() {
        return "AsyncJob";
    }

    /**
     * Get the buffer for result retrieval.
     */
    public StreamBuffer getBuffer() {
        return buffer;
    }
}
