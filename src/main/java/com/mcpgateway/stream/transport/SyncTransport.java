package com.mcpgateway.stream.transport;

import com.mcpgateway.stream.StreamToken;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synchronous transport - collects all tokens and returns complete response.
 *
 * This is the simplest mode:
 * - No streaming
 * - Wait for complete response
 * - Return all at once
 *
 * Best for:
 * - Debugging
 * - Clients that don't support streaming
 * - Guaranteed complete delivery
 */
@Slf4j
public class SyncTransport implements StreamTransport {

    private final String requestId;
    private final List<StreamToken> tokens = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final StringBuilder contentBuilder = new StringBuilder();

    public SyncTransport(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public void send(StreamToken token) throws TransportException {
        if (closed.get()) {
            throw new TransportException("Sync transport closed");
        }

        tokens.add(token);

        if (token.getType() == StreamToken.TokenType.TEXT && token.getText() != null) {
            contentBuilder.append(token.getText());
        }
    }

    @Override
    public void flush() throws TransportException {
        // No-op - sync doesn't flush incrementally
    }

    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    public boolean isConnected() {
        return !closed.get();
    }

    @Override
    public String getType() {
        return "Sync";
    }

    /**
     * Get all collected tokens.
     */
    public List<StreamToken> getTokens() {
        return new ArrayList<>(tokens);
    }

    /**
     * Get the complete content as a string.
     */
    public String getContent() {
        return contentBuilder.toString();
    }

    /**
     * Get token count.
     */
    public int getTokenCount() {
        return (int) tokens.stream()
                .filter(t -> t.getType() == StreamToken.TokenType.TEXT)
                .count();
    }
}
