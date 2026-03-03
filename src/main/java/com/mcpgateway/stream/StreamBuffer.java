package com.mcpgateway.stream;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer for stream tokens.
 *
 * The buffer is CRITICAL for:
 * 1. Async replay
 * 2. Resume after disconnect
 * 3. WebSocket reconnect
 * 4. Audit (if allowed)
 *
 * Gateway ALWAYS buffers - we never just "pass through".
 */
@Slf4j
public class StreamBuffer {

    private final String requestId;
    private final List<StreamToken> tokens;
    private final AtomicLong sequenceCounter;
    private final int maxTokens;
    private final long maxBytes;

    private volatile boolean completed = false;
    private volatile boolean overflow = false;
    private volatile long totalBytes = 0;

    public StreamBuffer(String requestId, int maxTokens, long maxBytes) {
        this.requestId = requestId;
        this.tokens = new CopyOnWriteArrayList<>();
        this.sequenceCounter = new AtomicLong(0);
        this.maxTokens = maxTokens;
        this.maxBytes = maxBytes;
    }

    public StreamBuffer(String requestId) {
        this(requestId, 10000, 10 * 1024 * 1024); // 10k tokens, 10MB default
    }

    /**
     * Append a token to the buffer.
     *
     * @return The token with sequence number assigned
     */
    public StreamToken append(String text) {
        if (completed) {
            throw new IllegalStateException("Buffer is completed, cannot append");
        }

        StreamToken token = StreamToken.text(sequenceCounter.getAndIncrement(), text);
        return appendToken(token);
    }

    /**
     * Append a pre-built token.
     */
    public StreamToken appendToken(StreamToken token) {
        if (completed && token.getType() != StreamToken.TokenType.END) {
            throw new IllegalStateException("Buffer is completed, cannot append");
        }

        // Check overflow
        if (tokens.size() >= maxTokens) {
            overflow = true;
            log.warn("Stream buffer overflow for request {}: max tokens {} exceeded",
                    requestId, maxTokens);
            return token;
        }

        long tokenBytes = token.getByteSize();
        if (totalBytes + tokenBytes > maxBytes) {
            overflow = true;
            log.warn("Stream buffer overflow for request {}: max bytes {} exceeded",
                    requestId, maxBytes);
            return token;
        }

        // Assign sequence if not set
        if (token.getSequence() == 0 && token.getType() == StreamToken.TokenType.TEXT) {
            token = StreamToken.builder()
                    .sequence(sequenceCounter.getAndIncrement())
                    .type(token.getType())
                    .text(token.getText())
                    .timestamp(token.getTimestamp())
                    .metadata(token.getMetadata())
                    .build();
        }

        tokens.add(token);
        totalBytes += tokenBytes;

        if (token.getType() == StreamToken.TokenType.END) {
            completed = true;
        }

        return token;
    }

    /**
     * Mark buffer as started.
     */
    public StreamToken start() {
        StreamToken startToken = StreamToken.start(sequenceCounter.getAndIncrement());
        tokens.add(startToken);
        return startToken;
    }

    /**
     * Mark buffer as completed.
     */
    public StreamToken complete() {
        if (!completed) {
            StreamToken endToken = StreamToken.end(sequenceCounter.getAndIncrement());
            tokens.add(endToken);
            completed = true;
            return endToken;
        }
        return null;
    }

    /**
     * Mark buffer as errored.
     */
    public StreamToken error(String message) {
        StreamToken errorToken = StreamToken.error(sequenceCounter.getAndIncrement(), message);
        tokens.add(errorToken);
        completed = true;
        return errorToken;
    }

    /**
     * Get all tokens.
     */
    public List<StreamToken> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(tokens));
    }

    /**
     * Get tokens from a specific sequence (for replay).
     */
    public List<StreamToken> getFromSequence(long fromSequence) {
        return tokens.stream()
                .filter(t -> t.getSequence() >= fromSequence)
                .toList();
    }

    /**
     * Get the last N tokens.
     */
    public List<StreamToken> getLastN(int n) {
        int size = tokens.size();
        if (size <= n) {
            return snapshot();
        }
        return tokens.subList(size - n, size);
    }

    /**
     * Get current sequence number.
     */
    public long getCurrentSequence() {
        return sequenceCounter.get();
    }

    /**
     * Get token count.
     */
    public int getTokenCount() {
        return tokens.size();
    }

    /**
     * Get total bytes.
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Check if buffer is completed.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Check if buffer has overflowed.
     */
    public boolean isOverflow() {
        return overflow;
    }

    /**
     * Get concatenated text content.
     */
    public String getFullText() {
        StringBuilder sb = new StringBuilder();
        for (StreamToken token : tokens) {
            if (token.getType() == StreamToken.TokenType.TEXT && token.getText() != null) {
                sb.append(token.getText());
            }
        }
        return sb.toString();
    }

    /**
     * Clear the buffer (for memory management).
     */
    public void clear() {
        tokens.clear();
        totalBytes = 0;
        sequenceCounter.set(0);
    }
}
