package com.mcpgateway.stream.upstream;

import com.mcpgateway.stream.StreamToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock LLM stream adapter for testing.
 * Simulates streaming responses from an LLM.
 */
@Slf4j
@Component
public class MockLlmStreamAdapter implements LlmStreamAdapter {

    private static final String[] SAMPLE_TOKENS = {
            "Hello", "!", " ", "I", "'m", " ", "an", " ", "AI", " ",
            "assistant", ".", " ", "How", " ", "can", " ", "I", " ",
            "help", " ", "you", " ", "today", "?"
    };

    @Override
    public Flux<StreamToken> streamTokens(LlmRequest request) {
        log.debug("Mock LLM streaming for model: {}", request.getModel());

        AtomicLong sequence = new AtomicLong(0);

        return Flux.concat(
                // Start token
                Flux.just(StreamToken.start(sequence.getAndIncrement())),

                // Simulate streaming tokens with delay
                Flux.fromArray(SAMPLE_TOKENS)
                        .delayElements(Duration.ofMillis(50))
                        .map(text -> StreamToken.text(sequence.getAndIncrement(), text)),

                // End token
                Flux.just(StreamToken.end(sequence.getAndIncrement()))
        );
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    /**
     * Create a Flux that simulates an error after some tokens.
     */
    public Flux<StreamToken> streamWithError(LlmRequest request, int tokensBeforeError) {
        AtomicLong sequence = new AtomicLong(0);

        return Flux.concat(
                Flux.just(StreamToken.start(sequence.getAndIncrement())),
                Flux.fromArray(SAMPLE_TOKENS)
                        .take(tokensBeforeError)
                        .delayElements(Duration.ofMillis(50))
                        .map(text -> StreamToken.text(sequence.getAndIncrement(), text)),
                Flux.error(new RuntimeException("Simulated LLM error"))
        );
    }

    /**
     * Create a Flux with custom content.
     */
    public Flux<StreamToken> streamCustom(String content, Duration tokenDelay) {
        AtomicLong sequence = new AtomicLong(0);
        String[] words = content.split("(?<=\\s)|(?=\\s)");

        return Flux.concat(
                Flux.just(StreamToken.start(sequence.getAndIncrement())),
                Flux.fromArray(words)
                        .delayElements(tokenDelay)
                        .map(text -> StreamToken.text(sequence.getAndIncrement(), text)),
                Flux.just(StreamToken.end(sequence.getAndIncrement()))
        );
    }
}
