package com.mcpgateway.stream;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * A token/chunk in the stream.
 *
 * This is the atomic unit of streaming - everything flows as tokens.
 */
@Data
@Builder
public class StreamToken {

    public enum TokenType {
        /**
         * Text content token.
         */
        TEXT,

        /**
         * Start of message marker.
         */
        START,

        /**
         * End of message marker.
         */
        END,

        /**
         * Error token.
         */
        ERROR,

        /**
         * Heartbeat/keep-alive token.
         */
        HEARTBEAT,

        /**
         * Metadata token (not displayed to user).
         */
        METADATA
    }

    /**
     * Sequence number for ordering and replay.
     */
    private long sequence;

    /**
     * Type of token.
     */
    private TokenType type;

    /**
     * Text content (for TEXT tokens).
     */
    private String text;

    /**
     * When this token was generated.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Optional metadata.
     */
    private Object metadata;

    /**
     * Size in bytes.
     */
    public int getByteSize() {
        return text != null ? text.getBytes().length : 0;
    }

    // Factory methods

    public static StreamToken text(long sequence, String text) {
        return StreamToken.builder()
                .sequence(sequence)
                .type(TokenType.TEXT)
                .text(text)
                .build();
    }

    public static StreamToken start(long sequence) {
        return StreamToken.builder()
                .sequence(sequence)
                .type(TokenType.START)
                .build();
    }

    public static StreamToken end(long sequence) {
        return StreamToken.builder()
                .sequence(sequence)
                .type(TokenType.END)
                .build();
    }

    public static StreamToken error(long sequence, String errorMessage) {
        return StreamToken.builder()
                .sequence(sequence)
                .type(TokenType.ERROR)
                .text(errorMessage)
                .build();
    }

    public static StreamToken heartbeat(long sequence) {
        return StreamToken.builder()
                .sequence(sequence)
                .type(TokenType.HEARTBEAT)
                .build();
    }
}
