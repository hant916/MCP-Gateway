package com.mcpgateway.service.ailuros;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.config.AilurosGovernanceProperties;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcRegressionRun;
import com.mcpgateway.domain.ailuros.AcReleaseBaseline;
import com.mcpgateway.dto.ailuros.RegressionRunDTO;
import com.mcpgateway.dto.ailuros.RegressionRunRequestDTO;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import com.mcpgateway.repository.ailuros.AcRegressionRunRepository;
import com.mcpgateway.repository.ailuros.AcReleaseBaselineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AilurosRegressionServiceTest {

    @Mock
    private AcRegressionRunRepository runRepository;
    @Mock
    private AcReleaseBaselineRepository baselineRepository;
    @Mock
    private AcCallRepository callRepository;
    @Mock
    private AilurosGovernanceWebhookService webhookService;
    @Mock
    private ObjectMapper objectMapper;

    private AilurosRegressionService service;

    @BeforeEach
    void setUp() throws Exception {
        AilurosGovernanceProperties properties = new AilurosGovernanceProperties();
        properties.getRegression().setMaxCases(20);
        properties.getRegression().setEnabled(true);

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("ok", true));

        when(runRepository.save(any(AcRegressionRun.class))).thenAnswer(invocation -> {
            AcRegressionRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            if (run.getCreatedTs() == null) {
                run.setCreatedTs(Instant.now());
            }
            return run;
        });

        service = new AilurosRegressionService(
            runRepository,
            baselineRepository,
            callRepository,
            webhookService,
            properties,
            objectMapper
        );
    }

    @Test
    void runRegression_shouldBlockRelease_whenCandidateRegresses() {
        AcReleaseBaseline baseline = AcReleaseBaseline.builder()
            .id(UUID.randomUUID())
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .baselineModel("gpt-4o")
            .baselinePromptVersion("v12")
            .isEnabled(true)
            .createdTs(Instant.now())
            .updatedTs(Instant.now())
            .build();

        AcCall latestCandidate = call("clarity", "prod", "/v1/chat", "gpt-4.1", "v13", "ok", 400, "0.010000");
        AcCall base1 = call("clarity", "prod", "/v1/chat", "gpt-4o", "v12", "ok", 200, "0.005000");
        AcCall base2 = call("clarity", "prod", "/v1/chat", "gpt-4o", "v12", "ok", 180, "0.005000");
        AcCall cand1 = call("clarity", "prod", "/v1/chat", "gpt-4.1", "v13", "error", 1200, "0.020000");
        AcCall cand2 = call("clarity", "prod", "/v1/chat", "gpt-4.1", "v13", "ok", 900, "0.020000");

        when(baselineRepository.findExactBaseline("clarity", "prod", "/v1/chat"))
            .thenReturn(Optional.of(baseline));
        when(callRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(latestCandidate)))
            .thenReturn(new PageImpl<>(List.of(cand1, cand2, base1, base2)));

        RegressionRunRequestDTO request = RegressionRunRequestDTO.builder()
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .candidateModel("gpt-4.1")
            .candidatePromptVersion("v13")
            .build();

        RegressionRunDTO result = service.runRegression(request);

        assertEquals(AcRegressionRun.Status.FAIL, result.getStatus());
        assertTrue(Boolean.TRUE.equals(result.getReleaseBlocked()));
        verify(webhookService, atLeastOnce()).notifyEvent(anyString(), any());
    }

    private AcCall call(String appId,
                        String env,
                        String route,
                        String model,
                        String promptVersion,
                        String status,
                        int latencyMs,
                        String cost) {
        return AcCall.builder()
            .id(UUID.randomUUID())
            .traceId(UUID.randomUUID().toString())
            .projectKey(appId)
            .appId(appId)
            .env(env)
            .route(route)
            .provider("openai")
            .model(model)
            .promptVersion(promptVersion)
            .status(status)
            .latencyMs(latencyMs)
            .costEstimateUsd(new BigDecimal(cost))
            .requestTs(Instant.now())
            .createdAt(Instant.now())
            .build();
    }
}
