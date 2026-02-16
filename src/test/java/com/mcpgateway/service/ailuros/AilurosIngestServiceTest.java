package com.mcpgateway.service.ailuros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.dto.ailuros.CallEventV1DTO;
import com.mcpgateway.repository.ailuros.AcCallFlagRepository;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AilurosIngestServiceTest {

    @Mock
    private AcCallRepository callRepository;

    @Mock
    private AcCallFlagRepository flagRepository;

    @Mock
    private ObjectMapper objectMapper;

    private AilurosIngestService service;

    @BeforeEach
    void setUp() {
        service = new AilurosIngestService(callRepository, flagRepository, objectMapper);
    }

    @Test
    void ingest_shouldPersistCallAndAutoFlags() throws Exception {
        CallEventV1DTO event = CallEventV1DTO.builder()
            .eventVersion("call_event.v1")
            .identity(CallEventV1DTO.Identity.builder().traceId("trace-123").spanId("span-456").build())
            .dims(CallEventV1DTO.Dimensions.builder()
                .appId("clarity")
                .env("prod")
                .route("/v1/chat/completions")
                .provider("openai")
                .model("gpt-4o")
                .promptVersion("v12")
                .streaming(false)
                .userTier("enterprise")
                .build())
            .timing(CallEventV1DTO.Timing.builder()
                .requestTs(Instant.parse("2026-02-16T10:00:00Z"))
                .responseTs(Instant.parse("2026-02-16T10:00:01Z"))
                .latencyMs(1000)
                .build())
            .usage(CallEventV1DTO.Usage.builder()
                .inputTokens(100)
                .outputTokens(50)
                .costUsd(new BigDecimal("0.0312"))
                .build())
            .outcome(CallEventV1DTO.Outcome.builder()
                .status("error")
                .errorType("provider_error")
                .httpStatus(500)
                .build())
            .privacy(CallEventV1DTO.Privacy.builder().promptHash("abc123").build())
            .flags(List.of("provider_error"))
            .metadata(Map.of("request_id", "req-1"))
            .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"request_id\":\"req-1\"}");
        when(callRepository.findByTraceId(anyString())).thenReturn(Optional.empty());
        when(callRepository.save(any(AcCall.class))).thenAnswer(invocation -> {
            AcCall call = invocation.getArgument(0);
            call.setId(UUID.randomUUID());
            return call;
        });

        AcCall persisted = service.ingest(event);

        assertNotNull(persisted.getId());
        assertEquals("clarity", persisted.getProjectKey());
        assertEquals("clarity", persisted.getAppId());
        assertEquals("prod", persisted.getEnv());
        assertEquals("/v1/chat/completions", persisted.getRoute());
        assertEquals("gpt-4o", persisted.getModel());
        assertEquals("v12", persisted.getPromptVersion());
        assertEquals("error", persisted.getStatus());
        assertEquals(150, persisted.getTokensTotal());
        assertEquals(new BigDecimal("0.0312"), persisted.getCostEstimateUsd());
        assertEquals(Integer.valueOf(1000), persisted.getLatencyMs());

        verify(callRepository, times(1)).save(any(AcCall.class));
        verify(flagRepository, atLeast(1)).save(any());

        ArgumentCaptor<AcCall> callCaptor = ArgumentCaptor.forClass(AcCall.class);
        verify(callRepository).save(callCaptor.capture());
        assertEquals("trace-123", callCaptor.getValue().getTraceId());
    }
}
