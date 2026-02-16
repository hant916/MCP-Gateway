package com.mcpgateway.service.ailuros.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.dto.ailuros.CallEventV1DTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CallEventBuilderServiceTest {

    private final CallEventBuilderService service = new CallEventBuilderService(new ObjectMapper());

    @Test
    void parseUsage_shouldExtractTokensAndCost() {
        String payload = """
            {
              "usage": {
                "prompt_tokens": 120,
                "completion_tokens": 80
              },
              "cost_usd": 0.0421
            }
            """;

        CallEventBuilderService.ParsedUsage usage = service.parseUsage(payload);

        assertEquals(120, usage.inputTokens());
        assertEquals(80, usage.outputTokens());
        assertEquals(new BigDecimal("0.0421"), usage.costUsd());
    }

    @Test
    void parseUsage_shouldReturnZeroesOnInvalidPayload() {
        CallEventBuilderService.ParsedUsage usage = service.parseUsage("not-json");

        assertEquals(0, usage.inputTokens());
        assertEquals(0, usage.outputTokens());
        assertEquals(BigDecimal.ZERO, usage.costUsd());
    }

    @Test
    void buildErrorEvent_shouldDeriveTimeoutAndInterruptionFlags() {
        GatewayCallContext context = GatewayCallContext.builder()
            .eventVersion(CallEventBuilderService.EVENT_VERSION)
            .traceId("trace-1")
            .spanId("span-1")
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .provider("openai")
            .model("gpt-4o")
            .promptVersion("v12")
            .userTier("enterprise")
            .streaming(true)
            .requestTs(Instant.now())
            .promptHash("hash")
            .metadata(Map.of("transport", "sse"))
            .build();

        CallEventV1DTO event = service.buildErrorEvent(
            context,
            499,
            "timeout_interrupted",
            "stream timeout interrupted",
            List.of("custom_flag"),
            null
        );

        assertEquals("error", event.getOutcome().getStatus());
        assertTrue(event.getFlags().contains("timeout"));
        assertTrue(event.getFlags().contains("stream_interrupted"));
        assertTrue(event.getFlags().contains("custom_flag"));
    }
}
