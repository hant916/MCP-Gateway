package com.mcpgateway.stream.upstream;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response from an LLM provider (for non-streaming).
 */
@Data
@Builder
public class LlmResponse {

    /**
     * The generated content.
     */
    private String content;

    /**
     * The model used.
     */
    private String model;

    /**
     * Token usage information.
     */
    private Usage usage;

    /**
     * Finish reason (stop, length, etc.).
     */
    private String finishReason;

    /**
     * Provider-specific metadata.
     */
    private Object metadata;

    @Data
    @Builder
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
