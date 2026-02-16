package com.mcpgateway.service.ailuros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.config.AilurosGovernanceProperties;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcRegressionRun;
import com.mcpgateway.domain.ailuros.AcReleaseBaseline;
import com.mcpgateway.dto.ailuros.ReleaseBaselineDTO;
import com.mcpgateway.dto.ailuros.ReleaseCandidateDTO;
import com.mcpgateway.dto.ailuros.ReleaseGateStatusDTO;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import com.mcpgateway.repository.ailuros.AcRegressionRunRepository;
import com.mcpgateway.repository.ailuros.AcReleaseBaselineRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosReleaseGateService {

    private static final String DEFAULT_APP_ID = "clarity";
    private static final String DEFAULT_ENV = "prod";

    private final AcReleaseBaselineRepository releaseBaselineRepository;
    private final AcRegressionRunRepository regressionRunRepository;
    private final AcCallRepository callRepository;
    private final AilurosGovernanceProperties governanceProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ReleaseGateStatusDTO getReleaseStatus(String appId, String env, String route) {
        String effectiveAppId = normalizeText(appId) != null ? normalizeText(appId) : DEFAULT_APP_ID;
        String effectiveEnv = normalizeText(env) != null ? normalizeText(env) : DEFAULT_ENV;
        String normalizedRoute = normalizeRoute(route);

        AcReleaseBaseline baseline = findBaselineWithFallback(effectiveAppId, effectiveEnv, normalizedRoute);
        ReleaseCandidateDTO candidate = detectCandidate(effectiveAppId, effectiveEnv, normalizedRoute);

        boolean changed = baseline != null
            && candidate != null
            && (isDifferent(baseline.getBaselineModel(), candidate.getCandidateModel())
                || isDifferent(baseline.getBaselinePromptVersion(), candidate.getCandidatePromptVersion()));

        UUID pendingRunId = null;
        if (changed && governanceProperties.getRegression().isEnabled()) {
            pendingRunId = enqueuePendingRegressionIfNeeded(baseline, candidate);
        }

        String latestRoute = normalizedRoute != null
            ? normalizedRoute
            : candidate != null ? candidate.getRoute() : null;
        AcRegressionRun latestRun = regressionRunRepository.findLatestByDimension(
            effectiveAppId,
            effectiveEnv,
            latestRoute
        ).stream().findFirst().orElse(null);

        return ReleaseGateStatusDTO.builder()
            .baseline(toDto(baseline))
            .candidate(candidate)
            .changed(changed)
            .pendingRunId(pendingRunId)
            .latestRunId(latestRun != null ? latestRun.getId() : null)
            .latestRunStatus(latestRun != null ? latestRun.getStatus() : null)
            .releaseBlocked(latestRun != null && Boolean.TRUE.equals(latestRun.getReleaseBlocked()))
            .build();
    }

    @Transactional
    public ReleaseBaselineDTO saveBaseline(ReleaseBaselineDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("baseline payload cannot be null");
        }
        if (request.getAppId() == null || request.getAppId().isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        if (request.getEnv() == null || request.getEnv().isBlank()) {
            throw new IllegalArgumentException("env is required");
        }

        String route = normalizeRoute(request.getRoute());
        AcReleaseBaseline baseline;
        if (request.getId() != null) {
            baseline = releaseBaselineRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("baseline not found: " + request.getId()));
        } else {
            baseline = releaseBaselineRepository.findExactBaseline(request.getAppId(), request.getEnv(), route)
                .orElseGet(AcReleaseBaseline::new);
        }

        baseline.setAppId(request.getAppId().trim());
        baseline.setEnv(request.getEnv().trim());
        baseline.setRoute(route);
        baseline.setBaselineModel(normalizeText(request.getBaselineModel()));
        baseline.setBaselinePromptVersion(normalizeText(request.getBaselinePromptVersion()));
        baseline.setIsEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());

        return toDto(releaseBaselineRepository.save(baseline));
    }

    @Transactional
    public int detectAndQueueAllBaselines() {
        if (!governanceProperties.getRegression().isEnabled()) {
            return 0;
        }

        int created = 0;
        List<AcReleaseBaseline> baselines = releaseBaselineRepository.findByIsEnabledTrueOrderByUpdatedTsDesc();
        for (AcReleaseBaseline baseline : baselines) {
            ReleaseCandidateDTO candidate = detectCandidate(baseline.getAppId(), baseline.getEnv(), baseline.getRoute());
            if (candidate == null) {
                continue;
            }

            boolean changed = isDifferent(baseline.getBaselineModel(), candidate.getCandidateModel())
                || isDifferent(baseline.getBaselinePromptVersion(), candidate.getCandidatePromptVersion());
            if (!changed) {
                continue;
            }

            UUID pendingRunId = enqueuePendingRegressionIfNeeded(baseline, candidate);
            if (pendingRunId != null) {
                created++;
            }
        }

        return created;
    }

    private UUID enqueuePendingRegressionIfNeeded(AcReleaseBaseline baseline, ReleaseCandidateDTO candidate) {
        long activeCount = regressionRunRepository.countActiveForCandidate(
            baseline.getAppId(),
            baseline.getEnv(),
            baseline.getRoute(),
            candidate.getCandidateModel(),
            candidate.getCandidatePromptVersion(),
            List.of(AcRegressionRun.Status.PENDING, AcRegressionRun.Status.RUNNING)
        );
        if (activeCount > 0) {
            return null;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reason", "change_detected");
        summary.put("detected_at", candidate.getDetectedAt());
        summary.put("baseline_model", baseline.getBaselineModel());
        summary.put("baseline_prompt_version", baseline.getBaselinePromptVersion());
        summary.put("candidate_model", candidate.getCandidateModel());
        summary.put("candidate_prompt_version", candidate.getCandidatePromptVersion());

        AcRegressionRun run = AcRegressionRun.builder()
            .appId(baseline.getAppId())
            .env(baseline.getEnv())
            .route(baseline.getRoute())
            .baselineModel(baseline.getBaselineModel())
            .baselinePromptVersion(baseline.getBaselinePromptVersion())
            .candidateModel(candidate.getCandidateModel())
            .candidatePromptVersion(candidate.getCandidatePromptVersion())
            .status(AcRegressionRun.Status.PENDING)
            .releaseBlocked(Boolean.FALSE)
            .summaryJson(toJson(summary))
            .reportUri(null)
            .build();
        run = regressionRunRepository.save(run);
        log.info("Queued pending regression run id={} app={} env={} route={}",
            run.getId(), run.getAppId(), run.getEnv(), run.getRoute());
        return run.getId();
    }

    private ReleaseCandidateDTO detectCandidate(String appId, String env, String route) {
        int hours = Math.max(1, governanceProperties.getRegression().getDetectorWindowHours());
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        Specification<AcCall> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(
                cb.equal(root.get("projectKey"), appId),
                cb.equal(root.get("appId"), appId)
            ));
            predicates.add(cb.equal(root.get("env"), env));
            if (route != null) {
                predicates.add(cb.equal(root.get("route"), route));
            }

            var ts = cb.function("COALESCE", Instant.class, root.get("requestTs"), root.get("createdAt"));
            predicates.add(cb.greaterThanOrEqualTo(ts, since));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<AcCall> calls = callRepository.findAll(
            spec,
            PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        Optional<AcCall> latest = calls.stream()
            .filter(call -> normalizeText(call.getModel()) != null || normalizeText(call.getPromptVersion()) != null)
            .findFirst();

        if (latest.isEmpty()) {
            return null;
        }

        AcCall call = latest.get();
        return ReleaseCandidateDTO.builder()
            .appId(appId)
            .env(env)
            .route(route != null ? route : normalizeRoute(call.getRoute()))
            .candidateModel(normalizeText(call.getModel()))
            .candidatePromptVersion(normalizeText(call.getPromptVersion()))
            .detectedAt(call.getRequestTs() != null ? call.getRequestTs() : call.getCreatedAt())
            .build();
    }

    private AcReleaseBaseline findBaselineWithFallback(String appId, String env, String route) {
        AcReleaseBaseline baseline = releaseBaselineRepository.findExactBaseline(appId, env, route).orElse(null);
        if (baseline == null && route != null) {
            baseline = releaseBaselineRepository.findExactBaseline(appId, env, null).orElse(null);
        }
        return baseline;
    }

    private boolean isDifferent(String left, String right) {
        return !Objects.equals(normalizeText(left), normalizeText(right));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private ReleaseBaselineDTO toDto(AcReleaseBaseline baseline) {
        if (baseline == null) {
            return null;
        }

        return ReleaseBaselineDTO.builder()
            .id(baseline.getId())
            .appId(baseline.getAppId())
            .env(baseline.getEnv())
            .route(baseline.getRoute())
            .baselineModel(baseline.getBaselineModel())
            .baselinePromptVersion(baseline.getBaselinePromptVersion())
            .enabled(baseline.getIsEnabled())
            .createdTs(baseline.getCreatedTs())
            .updatedTs(baseline.getUpdatedTs())
            .build();
    }

    private String normalizeRoute(String route) {
        String value = normalizeText(route);
        if (value == null || "*".equals(value) || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private String normalizeText(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
