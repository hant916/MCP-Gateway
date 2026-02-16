package com.mcpgateway.service.ailuros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.config.AilurosGovernanceProperties;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcRegressionRun;
import com.mcpgateway.domain.ailuros.AcReleaseBaseline;
import com.mcpgateway.dto.ailuros.ReleaseGateStatusDTO;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AilurosReleaseGateServiceTest {

    @Mock
    private AcReleaseBaselineRepository baselineRepository;
    @Mock
    private AcRegressionRunRepository runRepository;
    @Mock
    private AcCallRepository callRepository;
    @Mock
    private ObjectMapper objectMapper;

    private AilurosReleaseGateService service;

    @BeforeEach
    void setUp() throws Exception {
        AilurosGovernanceProperties properties = new AilurosGovernanceProperties();
        properties.getRegression().setEnabled(true);
        properties.getRegression().setDetectorWindowHours(24);

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"reason\":\"change_detected\"}");

        service = new AilurosReleaseGateService(
            baselineRepository,
            runRepository,
            callRepository,
            properties,
            objectMapper
        );
    }

    @Test
    void getReleaseStatus_shouldQueuePending_whenCandidateDiffersFromBaseline() {
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

        AcCall latestCall = AcCall.builder()
            .id(UUID.randomUUID())
            .traceId("trace-a")
            .projectKey("clarity")
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .model("gpt-4.1")
            .promptVersion("v13")
            .provider("openai")
            .status("ok")
            .requestTs(Instant.now())
            .createdAt(Instant.now())
            .build();

        AcRegressionRun queued = AcRegressionRun.builder()
            .id(UUID.randomUUID())
            .appId("clarity")
            .env("prod")
            .route("/v1/chat")
            .status(AcRegressionRun.Status.PENDING)
            .createdTs(Instant.now())
            .releaseBlocked(false)
            .build();

        when(baselineRepository.findExactBaseline("clarity", "prod", "/v1/chat"))
            .thenReturn(Optional.of(baseline));
        when(callRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(latestCall)));
        when(runRepository.countActiveForCandidate(
            eq("clarity"), eq("prod"), eq("/v1/chat"), eq("gpt-4.1"), eq("v13"), any()))
            .thenReturn(0L);
        when(runRepository.save(any(AcRegressionRun.class))).thenReturn(queued);
        when(runRepository.findLatestByDimension("clarity", "prod", "/v1/chat")).thenReturn(List.of(queued));

        ReleaseGateStatusDTO status = service.getReleaseStatus("clarity", "prod", "/v1/chat");

        assertTrue(status.isChanged());
        assertNotNull(status.getPendingRunId());
        assertEquals("gpt-4.1", status.getCandidate().getCandidateModel());
    }
}
