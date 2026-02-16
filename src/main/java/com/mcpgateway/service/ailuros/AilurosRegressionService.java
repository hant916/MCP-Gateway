package com.mcpgateway.service.ailuros;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.config.AilurosGovernanceProperties;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcRegressionRun;
import com.mcpgateway.domain.ailuros.AcReleaseBaseline;
import com.mcpgateway.dto.ailuros.DashboardWindowDTO;
import com.mcpgateway.dto.ailuros.RegressionReportDTO;
import com.mcpgateway.dto.ailuros.RegressionRunDTO;
import com.mcpgateway.dto.ailuros.RegressionRunRequestDTO;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosRegressionService {

    private static final String DEFAULT_APP_ID = "clarity";
    private static final String DEFAULT_ENV = "prod";
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final AcRegressionRunRepository regressionRunRepository;
    private final AcReleaseBaselineRepository releaseBaselineRepository;
    private final AcCallRepository callRepository;
    private final AilurosGovernanceWebhookService webhookService;
    private final AilurosGovernanceProperties governanceProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public RegressionRunDTO runRegression(RegressionRunRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("regression request cannot be null");
        }

        String appId = normalizeText(request.getAppId()) != null ? normalizeText(request.getAppId()) : DEFAULT_APP_ID;
        String env = normalizeText(request.getEnv()) != null ? normalizeText(request.getEnv()) : DEFAULT_ENV;
        String route = normalizeRoute(request.getRoute());

        AcReleaseBaseline baselineConfig = releaseBaselineRepository.findExactBaseline(appId, env, route)
            .orElseGet(() -> route != null
                ? releaseBaselineRepository.findExactBaseline(appId, env, null).orElse(null)
                : null);

        String baselineModel = firstNonBlank(request.getBaselineModel(), baselineConfig != null ? baselineConfig.getBaselineModel() : null);
        String baselinePromptVersion = firstNonBlank(
            request.getBaselinePromptVersion(),
            baselineConfig != null ? baselineConfig.getBaselinePromptVersion() : null
        );

        AcCall latestCandidateCall = findLatestCall(appId, env, route);
        String candidateModel = firstNonBlank(request.getCandidateModel(), latestCandidateCall != null ? latestCandidateCall.getModel() : null);
        String candidatePromptVersion = firstNonBlank(
            request.getCandidatePromptVersion(),
            latestCandidateCall != null ? latestCandidateCall.getPromptVersion() : null
        );

        if (candidateModel == null || candidatePromptVersion == null) {
            throw new IllegalArgumentException("candidate model and promptVersion are required");
        }
        if (baselineModel == null || baselinePromptVersion == null) {
            throw new IllegalArgumentException("baseline model and promptVersion are required");
        }

        Instant now = Instant.now();
        AcRegressionRun run = AcRegressionRun.builder()
            .appId(appId)
            .env(env)
            .route(route)
            .baselineModel(baselineModel)
            .candidateModel(candidateModel)
            .baselinePromptVersion(baselinePromptVersion)
            .candidatePromptVersion(candidatePromptVersion)
            .status(AcRegressionRun.Status.RUNNING)
            .startedTs(now)
            .releaseBlocked(Boolean.FALSE)
            .build();
        run = regressionRunRepository.save(run);

        try {
            int maxCases = Math.max(10, governanceProperties.getRegression().getMaxCases());
            Instant from = now.minus(7, ChronoUnit.DAYS);
            List<AcCall> windowCalls = loadCalls(appId, env, route, from, now, Math.max(200, maxCases * 20));

            List<AcCall> baselineCalls = windowCalls.stream()
                .filter(call -> matchesCandidate(call, baselineModel, baselinePromptVersion))
                .limit(maxCases)
                .toList();
            List<AcCall> candidateCalls = windowCalls.stream()
                .filter(call -> matchesCandidate(call, candidateModel, candidatePromptVersion))
                .limit(maxCases)
                .toList();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("window_from", from.toString());
            summary.put("window_to", now.toString());
            summary.put("baseline_sample_size", baselineCalls.size());
            summary.put("candidate_sample_size", candidateCalls.size());

            RegressionMetrics baselineMetrics = calculateMetrics(baselineCalls);
            RegressionMetrics candidateMetrics = calculateMetrics(candidateCalls);

            summary.put("baseline", baselineMetrics.asMap());
            summary.put("candidate", candidateMetrics.asMap());

            List<String> topDiffs = new ArrayList<>();
            boolean pass;

            if (candidateCalls.isEmpty()) {
                pass = false;
                topDiffs.add("candidate_sample_empty");
            } else {
                pass = evaluatePassCriteria(baselineMetrics, candidateMetrics, topDiffs);
            }

            summary.put("top_diffs", topDiffs);
            summary.put("judge_enabled", governanceProperties.getRegression().isJudgeEnabled());
            summary.put("max_cases", maxCases);
            summary.put("result", pass ? "PASS" : "FAIL");

            run.setStatus(pass ? AcRegressionRun.Status.PASS : AcRegressionRun.Status.FAIL);
            run.setEndedTs(Instant.now());
            run.setReleaseBlocked(!pass);
            run.setSummaryJson(toJson(summary));
            run.setReportUri("/api/ailuros/regression/report/" + run.getId());
            run = regressionRunRepository.save(run);

            if (!pass) {
                webhookService.notifyEvent("regression_failed", Map.of(
                    "run_id", run.getId(),
                    "app_id", run.getAppId(),
                    "env", run.getEnv(),
                    "route", run.getRoute() != null ? run.getRoute() : "all",
                    "baseline_model", run.getBaselineModel(),
                    "candidate_model", run.getCandidateModel(),
                    "baseline_prompt_version", run.getBaselinePromptVersion(),
                    "candidate_prompt_version", run.getCandidatePromptVersion(),
                    "summary", summary
                ));
                webhookService.notifyEvent("release_blocked", Map.of(
                    "run_id", run.getId(),
                    "app_id", run.getAppId(),
                    "env", run.getEnv(),
                    "route", run.getRoute() != null ? run.getRoute() : "all"
                ));
            }
        } catch (Exception ex) {
            run.setStatus(AcRegressionRun.Status.ERROR);
            run.setEndedTs(Instant.now());
            run.setReleaseBlocked(Boolean.TRUE);
            run.setSummaryJson(toJson(Map.of(
                "result", "ERROR",
                "message", ex.getMessage()
            )));
            run.setReportUri("/api/ailuros/regression/report/" + run.getId());
            run = regressionRunRepository.save(run);
        }

        return toDto(run);
    }

    @Transactional(readOnly = true)
    public List<RegressionRunDTO> getRuns(String appId,
                                          String env,
                                          String route,
                                          String range,
                                          Instant from,
                                          Instant to,
                                          String timezone,
                                          int limit) {
        String effectiveAppId = normalizeText(appId) != null ? normalizeText(appId) : DEFAULT_APP_ID;
        Window window = resolveWindow(range, from, to, timezone);

        List<AcRegressionRun> runs = regressionRunRepository.findInWindow(
            effectiveAppId,
            normalizeText(env),
            normalizeRoute(route),
            window.from,
            window.to
        );

        int safeLimit = Math.max(1, limit);
        return runs.stream().limit(safeLimit).map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RegressionRunDTO getRun(UUID runId) {
        AcRegressionRun run = regressionRunRepository.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("regression run not found: " + runId));
        return toDto(run);
    }

    @Transactional(readOnly = true)
    public RegressionReportDTO getReport(UUID runId) {
        AcRegressionRun run = regressionRunRepository.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("regression run not found: " + runId));

        Map<String, Object> summary = parseSummary(run.getSummaryJson());
        String html = buildReportHtml(run, summary);

        return RegressionReportDTO.builder()
            .runId(run.getId())
            .status(run.getStatus().name())
            .summary(summary)
            .reportHtml(html)
            .build();
    }

    private boolean evaluatePassCriteria(RegressionMetrics baseline,
                                         RegressionMetrics candidate,
                                         List<String> topDiffs) {
        boolean pass = true;

        if (baseline.total() > 0) {
            BigDecimal errorThreshold = baseline.errorRate().add(new BigDecimal("3.00"));
            if (candidate.errorRate().compareTo(errorThreshold) > 0) {
                pass = false;
                topDiffs.add("error_rate_regression");
            }

            if (baseline.p95LatencyMs() != null && candidate.p95LatencyMs() != null) {
                double latencyThreshold = baseline.p95LatencyMs() * 1.5d;
                if (candidate.p95LatencyMs() > latencyThreshold) {
                    pass = false;
                    topDiffs.add("latency_regression");
                }
            }

            if (baseline.avgCostUsd().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal costThreshold = baseline.avgCostUsd().multiply(new BigDecimal("2.0"));
                if (candidate.avgCostUsd().compareTo(costThreshold) > 0) {
                    pass = false;
                    topDiffs.add("cost_regression");
                }
            }
        } else {
            if (candidate.errorRate().compareTo(new BigDecimal("8.00")) > 0) {
                pass = false;
                topDiffs.add("candidate_error_rate_high");
            }
            if (candidate.p95LatencyMs() != null && candidate.p95LatencyMs() > 5000d) {
                pass = false;
                topDiffs.add("candidate_latency_high");
            }
            if (candidate.avgCostUsd().compareTo(new BigDecimal("0.080000")) > 0) {
                pass = false;
                topDiffs.add("candidate_cost_high");
            }
        }

        return pass;
    }

    private RegressionMetrics calculateMetrics(List<AcCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return new RegressionMetrics(0, 0L, BigDecimal.ZERO, null, BigDecimal.ZERO);
        }

        long total = calls.size();
        long errors = calls.stream()
            .filter(call -> call.getStatus() != null && !"ok".equalsIgnoreCase(call.getStatus()))
            .count();

        BigDecimal errorRate = BigDecimal.valueOf(errors)
            .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal avgCost = calls.stream()
            .map(call -> call.getCostEstimateUsd() != null ? call.getCostEstimateUsd() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

        List<Integer> latencies = calls.stream()
            .filter(call -> call.getStatus() == null || "ok".equalsIgnoreCase(call.getStatus()))
            .map(AcCall::getLatencyMs)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        Double p95 = null;
        if (!latencies.isEmpty()) {
            int index = (int) Math.ceil(latencies.size() * 0.95d) - 1;
            index = Math.max(0, Math.min(index, latencies.size() - 1));
            p95 = latencies.get(index).doubleValue();
        }

        return new RegressionMetrics((int) total, errors, errorRate, p95, avgCost.setScale(6, RoundingMode.HALF_UP));
    }

    private AcCall findLatestCall(String appId, String env, String route) {
        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);
        List<AcCall> calls = loadCalls(appId, env, route, from, Instant.now(), 100);
        return calls.isEmpty() ? null : calls.get(0);
    }

    private List<AcCall> loadCalls(String appId, String env, String route, Instant from, Instant to, int limit) {
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
            predicates.add(cb.greaterThanOrEqualTo(ts, from));
            predicates.add(cb.lessThanOrEqualTo(ts, to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return callRepository.findAll(
            spec,
            PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }

    private boolean matchesCandidate(AcCall call, String model, String promptVersion) {
        return Objects.equals(normalizeText(call.getModel()), normalizeText(model))
            && Objects.equals(normalizeText(call.getPromptVersion()), normalizeText(promptVersion));
    }

    private RegressionRunDTO toDto(AcRegressionRun run) {
        return RegressionRunDTO.builder()
            .id(run.getId())
            .appId(run.getAppId())
            .env(run.getEnv())
            .route(run.getRoute())
            .baselineModel(run.getBaselineModel())
            .candidateModel(run.getCandidateModel())
            .baselinePromptVersion(run.getBaselinePromptVersion())
            .candidatePromptVersion(run.getCandidatePromptVersion())
            .status(run.getStatus())
            .startedTs(run.getStartedTs())
            .endedTs(run.getEndedTs())
            .createdTs(run.getCreatedTs())
            .releaseBlocked(run.getReleaseBlocked())
            .reportUri(run.getReportUri())
            .summary(parseSummary(run.getSummaryJson()))
            .build();
    }

    private Map<String, Object> parseSummary(String summaryJson) {
        if (summaryJson == null || summaryJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(summaryJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of("raw", summaryJson);
        }
    }

    private String buildReportHtml(AcRegressionRun run, Map<String, Object> summary) {
        String statusColor = run.getStatus() == AcRegressionRun.Status.PASS ? "#10b981" : "#ef4444";
        return """
            <html>
            <head><title>Regression Report</title></head>
            <body style="font-family:Segoe UI,Arial,sans-serif;padding:16px;">
              <h2 style="margin:0 0 8px 0;">Regression Run %s</h2>
              <div style="margin-bottom:8px;">Status: <b style="color:%s;">%s</b></div>
              <div style="margin-bottom:8px;">App: %s | Env: %s | Route: %s</div>
              <div style="margin-bottom:8px;">Baseline: %s / %s</div>
              <div style="margin-bottom:12px;">Candidate: %s / %s</div>
              <pre style="background:#f4f4f4;padding:10px;border-radius:6px;">%s</pre>
            </body>
            </html>
            """.formatted(
            run.getId(),
            statusColor,
            run.getStatus(),
            safe(run.getAppId()),
            safe(run.getEnv()),
            safe(run.getRoute()),
            safe(run.getBaselineModel()),
            safe(run.getBaselinePromptVersion()),
            safe(run.getCandidateModel()),
            safe(run.getCandidatePromptVersion()),
            safe(summary)
        );
    }

    private String toJson(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception ex) {
            return null;
        }
    }

    private String safe(Object value) {
        return value != null ? value.toString() : "n/a";
    }

    private String normalizeRoute(String route) {
        String value = normalizeText(route);
        if (value == null || "all".equalsIgnoreCase(value) || "*".equals(value)) {
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Window resolveWindow(String range, Instant from, Instant to, String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(normalizeText(timezone) != null ? timezone : DEFAULT_TIMEZONE);
        } catch (Exception ex) {
            zoneId = ZoneId.of(DEFAULT_TIMEZONE);
        }

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom;
        String rangeLabel;

        if (from != null) {
            effectiveFrom = from;
            long days = Math.max(1, ChronoUnit.DAYS.between(effectiveFrom, effectiveTo));
            rangeLabel = days + "d";
        } else {
            String safeRange = (range == null || range.isBlank() ? "30d" : range).toLowerCase(Locale.ROOT);
            if (safeRange.endsWith("h")) {
                long hours = parseLong(safeRange.replace("h", ""), 24L);
                effectiveFrom = effectiveTo.minus(hours, ChronoUnit.HOURS);
                rangeLabel = hours + "h";
            } else {
                long days = parseLong(safeRange.replace("d", ""), 30L);
                long windowDays = Math.max(1, days);
                effectiveFrom = effectiveTo.minus(windowDays - 1, ChronoUnit.DAYS);
                rangeLabel = days + "d";
            }
        }

        DashboardWindowDTO dto = DashboardWindowDTO.builder()
            .start(effectiveFrom)
            .end(effectiveTo)
            .timezone(zoneId.getId())
            .granularity("day")
            .build();
        return new Window(effectiveFrom, effectiveTo, dto, rangeLabel);
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record Window(Instant from, Instant to, DashboardWindowDTO dto, String rangeLabel) {}

    private record RegressionMetrics(
        int total,
        long errors,
        BigDecimal errorRate,
        Double p95LatencyMs,
        BigDecimal avgCostUsd
    ) {
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("total", total);
            map.put("errors", errors);
            map.put("error_rate_pct", errorRate);
            map.put("p95_latency_ms", p95LatencyMs);
            map.put("avg_cost_usd", avgCostUsd);
            return map;
        }
    }
}
