package com.mcpgateway.stream.upstream;

import com.mcpgateway.stream.StreamToken;
import reactor.core.publisher.Flux;

/**
 * Adapter interface for streaming from LLM providers.
 *
 * RULE: Upstream streaming is DECOUPLED from downstream delivery.
 * Gateway always controls how content is delivered to client.
 */
public interface LlmStreamAdapter {

    /**
     * Stream tokens from the LLM provider.
     *
     * @param request The request to send to the LLM
     * @return A Flux of tokens from the LLM
     */
    Flux<StreamToken> streamTokens(LlmRequest request);

    /**
     * Check if this adapter supports streaming.
     */
    boolean supportsStreaming();

    /**
     * Get the provider name.
     */
    String getProviderName();
}
