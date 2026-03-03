package com.mcpgateway.stream.upstream;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request to send to an LLM provider.
 */
@Data
@Builder
public class LlmRequest {

    /**
     * The model to use (e.g., "gpt-4", "claude-3-opus").
     */
    private String model;

    /**
     * The messages to send.
     */
    private List<Message> messages;

    /**
     * Maximum tokens to generate.
     */
    private Integer maxTokens;

    /**
     * Temperature for generation.
     */
    private Double temperature;

    /**
     * Whether to stream the response.
     */
    @Builder.Default
    private boolean stream = true;

    /**
     * Additional provider-specific parameters.
     */
    private Map<String, Object> additionalParams;

    /**
     * Request timeout in milliseconds.
     */
    @Builder.Default
    private long timeoutMs = 60000;

    /**
     * A message in the conversation.
     */
    @Data
    @Builder
    public static class Message {
        public enum Role {
            SYSTEM, USER, ASSISTANT
        }

        private Role role;
        private String content;
    }

    /**
     * Create a simple user message request.
     */
    public static LlmRequest userMessage(String model, String content) {
        return LlmRequest.builder()
                .model(model)
                .messages(List.of(
                        Message.builder()
                                .role(Message.Role.USER)
                                .content(content)
                                .build()
                ))
                .build();
    }
}
