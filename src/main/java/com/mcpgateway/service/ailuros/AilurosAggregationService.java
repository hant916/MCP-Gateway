package com.mcpgateway.service.ailuros;

import com.mcpgateway.domain.ailuros.AcBudgetEval;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcCallFlag;
import com.mcpgateway.domain.ailuros.AcRegressionRun;
import com.mcpgateway.dto.ailuros.*;
import com.mcpgateway.repository.ailuros.AcCallFlagRepository;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import com.mcpgateway.repository.ailuros.AcBudgetEvalRepository;
import com.mcpgateway.repository.ailuros.AcRegressionRunRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosAggregationService {

    private final AcCallRepository callRepository;
    private final AcCallFlagRepository flagRepository;
    private final AcBudgetEvalRepository budgetEvalRepository;
    private final AcRegressionRunRepository regressionRunRepository;

    @Transactional(readOnly = true)
    public AilurosStatsDTO getStats(String appId,
                                    String env,
                                    String range,
                                    Instant from,
                                    Instant to,
                                    String timezone) {
        Window window = resolveWindow(range, from, to, timezone);
        String effectiveAppId = effectiveAppId(appId);
        List<AcCall> calls = loadCalls(effectiveAppId, env, window.from, window.to);
        Map<UUID, List<AcCallFlag>> flagMap = loadFlags(calls);

        long total = calls.size();
        long errorCalls = calls.stream().filter(this::isError).count();
        long flagged = calls.stream().filter(call -> hasFlags(call, flagMap)).count();

        BigDecimal reliability = total > 0
            ? BigDecimal.valueOf(total - errorCalls)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.valueOf(100);

        BigDecimal errorRate = total > 0
            ? BigDecimal.valueOf(errorCalls)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal flaggedRate = total > 0
            ? BigDecimal.valueOf(flagged)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal totalCost = calls.stream()
            .map(call -> call.getCostEstimateUsd() != null ? call.getCostEstimateUsd() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double p95Latency = calculateP95Latency(calls);
        String topRiskDriver = topRiskDriver(flagMap, errorCalls);

        return AilurosStatsDTO.builder()
            .appId(effectiveAppId)
            .env(defaultValue(env, "all"))
            .range(window.rangeLabel)
            .window(window.dto)
            .reliability(scale2(reliability))
            .totalCalls(total)
            .errorCalls(errorCalls)
            .errorRate(scale2(errorRate))
            .flaggedCount(flagged)
            .flaggedRate(scale2(flaggedRate))
            .totalCost(scale4(totalCost))
            .p95LatencyMs(p95Latency)
            .topRiskDriver(topRiskDriver)
            .flagPolicy(buildFlagPolicy())
            .kpiFormulas(buildFormulas())
            .build();
    }

    @Transactional(readOnly = true)
    public TimeseriesResponseDTO getCostTimeseries(String appId,
                                                   String env,
                                                   String range,
                                                   Instant from,
                                                   Instant to,
                                                   String timezone) {
        Window window = resolveWindow(range, from, to, timezone);
        String effectiveAppId = effectiveAppId(appId);
        List<AcCall> calls = loadCalls(effectiveAppId, env, window.from, window.to);

        Map<LocalDate, BigDecimal> values = initDailyBigDecimal(window);
        for (AcCall call : calls) {
            LocalDate date = eventDate(call, window.zoneId);
            values.computeIfPresent(date, (k, v) -> v.add(nullSafe(call.getCostEstimateUsd())));
        }

        List<TimeseriesPointDTO> points = values.entrySet().stream()
            .map(entry -> TimeseriesPointDTO.builder()
                .date(entry.getKey())
                .value(scale4(entry.getValue()))
                .build())
            .collect(Collectors.toList());

        return TimeseriesResponseDTO.builder()
            .appId(effectiveAppId)
            .env(defaultValue(env, "all"))
            .range(window.rangeLabel)
            .metric("cost_usd")
            .window(window.dto)
            .points(points)
            .build();
    }

    @Transactional(readOnly = true)
    public TimeseriesResponseDTO getErrorTimeseries(String appId,
                                                    String env,
                                                    String range,
                                                    Instant from,
                                                    Instant to,
                                                    String timezone) {
        Window window = resolveWindow(range, from, to, timezone);
        String effectiveAppId = effectiveAppId(appId);
        List<AcCall> calls = loadCalls(effectiveAppId, env, window.from, window.to);

        Map<LocalDate, long[]> counters = initDailyCounters(window);
        for (AcCall call : calls) {
            LocalDate date = eventDate(call, window.zoneId);
            long[] daily = counters.get(date);
            if (daily == null) {
                continue;
            }
            daily[0] += 1;
            if (isError(call)) {
                daily[1] += 1;
            }
        }

        List<TimeseriesPointDTO> points = counters.entrySet().stream()
            .map(entry -> {
                long total = entry.getValue()[0];
                long errors = entry.getValue()[1];
                BigDecimal value = total > 0
                    ? BigDecimal.valueOf(errors)
                        .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
                return TimeseriesPointDTO.builder()
                    .date(entry.getKey())
                    .value(scale2(value))
                    .build();
            })
            .collect(Collectors.toList());

        return TimeseriesResponseDTO.builder()
            .appId(effectiveAppId)
            .env(defaultValue(env, "all"))
            .range(window.rangeLabel)
            .metric("error_rate")
            .window(window.dto)
            .points(points)
            .build();
    }

    @Transactional(readOnly = true)
    public TimeseriesResponseDTO getFlaggedTimeseries(String appId,
                                                      String env,
                                                      String range,
                                                      Instant from,
                                                      Instant to,
                                                      String timezone) {
        Window window = resolveWindow(range, from, to, timezone);
        String effectiveAppId = effectiveAppId(appId);
        List<AcCall> calls = loadCalls(effectiveAppId, env, window.from, window.to);
        Map<UUID, List<AcCallFlag>> flagMap = loadFlags(calls);

        Map<LocalDate, BigDecimal> values = initDailyBigDecimal(window);
        for (AcCall call : calls) {
            if (!hasFlags(call, flagMap)) {
                continue;
            }
            LocalDate date = eventDate(call, window.zoneId);
            values.computeIfPresent(date, (k, v) -> v.add(BigDecimal.ONE));
        }

        List<TimeseriesPointDTO> points = values.entrySet().stream()
            .map(entry -> TimeseriesPointDTO.builder()
                .date(entry.getKey())
                .value(entry.getValue())
                .build())
            .collect(Collectors.toList());

        return TimeseriesResponseDTO.builder()
            .appId(effectiveAppId)
            .env(defaultValue(env, "all"))
            .range(window.rangeLabel)
            .metric("flagged_count")
            .window(window.dto)
            .points(points)
            .build();
    }

    @Transactional(readOnly = true)
    public IncidentsResponseDTO getIncidents(String appId,
                                             String env,
                                             String range,
                                             Instant from,
                                             Instant to,
                                             String timezone,
                                             int limit) {
        Window window = resolveWindow(range, from, to, timezone);
        String effectiveAppId = effectiveAppId(appId);
        List<AcCall> calls = loadCalls(effectiveAppId, env, window.from, window.to);
        Map<UUID, List<AcCallFlag>> flagMap = loadFlags(calls);

        List<IncidentDTO> incidents = new ArrayList<>();

        incidents.addAll(calls.stream()
            .filter(call -> hasFlags(call, flagMap))
            .map(call -> toIncident(call, flagMap.getOrDefault(call.getId(), List.of())))
            .toList());
        incidents.addAll(loadBudgetIncidents(effectiveAppId, env, window.from, window.to));
        incidents.addAll(loadRegressionIncidents(effectiveAppId, env, window.from, window.to));

        incidents = incidents.stream()
            .sorted(incidentComparator())
            .limit(Math.max(1, limit))
            .collect(Collectors.toList());

        return IncidentsResponseDTO.builder()
            .appId(effectiveAppId)
            .env(defaultValue(env, "all"))
            .range(window.rangeLabel)
            .window(window.dto)
            .incidents(incidents)
            .build();
    }

    private Comparator<IncidentDTO> incidentComparator() {
        return Comparator
            .comparing((IncidentDTO incident) -> !"error".equalsIgnoreCase(incident.getStatus()))
            .thenComparing((IncidentDTO incident) -> incident.getFlags() != null ? -incident.getFlags().size() : 0)
            .thenComparing((IncidentDTO incident) -> incident.getLatencyMs() != null ? -incident.getLatencyMs() : 0)
            .thenComparing(IncidentDTO::getRequestTs, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private IncidentDTO toIncident(AcCall call, List<AcCallFlag> flags) {
        List<String> reasonCodes = flags.stream()
            .map(AcCallFlag::getFlagType)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));

        if (reasonCodes.isEmpty() && isError(call)) {
            reasonCodes.add("ERROR");
        }

        return IncidentDTO.builder()
            .id(call.getId())
            .traceId(call.getTraceId())
            .spanId(call.getSpanId())
            .appId(defaultValue(call.getAppId(), call.getProjectKey()))
            .env(call.getEnv())
            .route(defaultValue(call.getRoute(), "unknown"))
            .provider(call.getProvider())
            .model(call.getModel())
            .promptVersion(defaultValue(call.getPromptVersion(), call.getPromptRef()))
            .streaming(Boolean.TRUE.equals(call.getStreaming()))
            .status(call.getStatus())
            .errorType(call.getErrorType())
            .httpStatus(call.getHttpStatus())
            .latencyMs(call.getLatencyMs())
            .costUsd(scale4(nullSafe(call.getCostEstimateUsd())))
            .requestTs(call.getRequestTs() != null ? call.getRequestTs() : call.getCreatedAt())
            .responseTs(call.getResponseTs())
            .flags(reasonCodes)
            .build();
    }

    private List<IncidentDTO> loadBudgetIncidents(String appId, String env, Instant from, Instant to) {
        List<AcBudgetEval> evaluations = budgetEvalRepository.findInWindow(appId, env, null, from, to);
        return evaluations.stream()
            .filter(eval -> eval.getStatus() != AcBudgetEval.Status.OK)
            .map(this::toBudgetIncident)
            .toList();
    }

    private List<IncidentDTO> loadRegressionIncidents(String appId, String env, Instant from, Instant to) {
        List<AcRegressionRun> runs = regressionRunRepository.findInWindow(appId, env, null, from, to);
        return runs.stream()
            .filter(run -> run.getStatus() == AcRegressionRun.Status.FAIL
                || run.getStatus() == AcRegressionRun.Status.ERROR
                || Boolean.TRUE.equals(run.getReleaseBlocked()))
            .map(this::toRegressionIncident)
            .toList();
    }

    private IncidentDTO toBudgetIncident(AcBudgetEval eval) {
        String code = eval.getStatus() == AcBudgetEval.Status.EXCEEDED
            ? "BUDGET_EXCEEDED"
            : "BUDGET_FORECAST_EXCEEDED";
        return IncidentDTO.builder()
            .id(eval.getId())
            .traceId("budget:" + eval.getId())
            .spanId("budget")
            .appId(eval.getAppId())
            .env(eval.getEnv())
            .route(defaultValue(eval.getRoute(), "all"))
            .provider("governance")
            .model("budget-guard")
            .promptVersion("budget-policy")
            .streaming(false)
            .status("error")
            .errorType(code.toLowerCase(Locale.ROOT))
            .httpStatus(409)
            .latencyMs(null)
            .costUsd(scale4(nullSafe(eval.getCostUsd())))
            .requestTs(eval.getWindowEndTs())
            .responseTs(eval.getCreatedTs())
            .flags(List.of(code))
            .build();
    }

    private IncidentDTO toRegressionIncident(AcRegressionRun run) {
        List<String> flags = new ArrayList<>();
        if (run.getStatus() == AcRegressionRun.Status.FAIL) {
            flags.add("REGRESSION_FAILED");
        }
        if (run.getStatus() == AcRegressionRun.Status.ERROR) {
            flags.add("REGRESSION_ERROR");
        }
        if (Boolean.TRUE.equals(run.getReleaseBlocked())) {
            flags.add("RELEASE_BLOCKED");
        }
        if (flags.isEmpty()) {
            flags.add("REGRESSION_EVENT");
        }

        return IncidentDTO.builder()
            .id(run.getId())
            .traceId("regression:" + run.getId())
            .spanId("release-gate")
            .appId(run.getAppId())
            .env(run.getEnv())
            .route(defaultValue(run.getRoute(), "all"))
            .provider("governance")
            .model("release-gate")
            .promptVersion(defaultValue(run.getCandidatePromptVersion(), run.getBaselinePromptVersion()))
            .streaming(false)
            .status("error")
            .errorType(run.getStatus().name().toLowerCase(Locale.ROOT))
            .httpStatus(Boolean.TRUE.equals(run.getReleaseBlocked()) ? 423 : 409)
            .latencyMs(null)
            .costUsd(null)
            .requestTs(run.getStartedTs() != null ? run.getStartedTs() : run.getCreatedTs())
            .responseTs(run.getEndedTs())
            .flags(flags)
            .build();
    }

    private List<AcCall> loadCalls(String appId, String env, Instant from, Instant to) {
        Specification<AcCall> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (appId != null && !appId.isBlank()) {
                predicates.add(cb.equal(root.get("projectKey"), appId));
            }
            if (env != null && !env.isBlank()) {
                predicates.add(cb.equal(root.get("env"), env));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return callRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    private Map<UUID, List<AcCallFlag>> loadFlags(List<AcCall> calls) {
        List<UUID> callIds = calls.stream().map(AcCall::getId).toList();
        if (callIds.isEmpty()) {
            return Map.of();
        }

        List<AcCallFlag> flags = flagRepository.findByCallIdInOrderByCreatedAtDesc(callIds);
        return flags.stream().collect(Collectors.groupingBy(flag -> flag.getCall().getId()));
    }

    private boolean isError(AcCall call) {
        return call.getStatus() != null && !"ok".equalsIgnoreCase(call.getStatus());
    }

    private boolean hasFlags(AcCall call, Map<UUID, List<AcCallFlag>> flagMap) {
        List<AcCallFlag> flags = flagMap.get(call.getId());
        return (flags != null && !flags.isEmpty()) || isError(call);
    }

    private Double calculateP95Latency(List<AcCall> calls) {
        List<Integer> latencies = calls.stream()
            .filter(call -> !isError(call))
            .map(AcCall::getLatencyMs)
            .filter(Objects::nonNull)
            .sorted()
            .toList();

        if (latencies.isEmpty()) {
            return null;
        }

        int index = (int) Math.ceil(latencies.size() * 0.95d) - 1;
        index = Math.max(0, Math.min(index, latencies.size() - 1));
        return latencies.get(index).doubleValue();
    }

    private String topRiskDriver(Map<UUID, List<AcCallFlag>> flagMap, long errorCalls) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (List<AcCallFlag> flags : flagMap.values()) {
            for (AcCallFlag flag : flags) {
                if (flag.getFlagType() == null) {
                    continue;
                }
                counts.merge(flag.getFlagType(), 1L, Long::sum);
            }
        }

        if (!counts.isEmpty()) {
            return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
        }

        if (errorCalls > 0) {
            return "ERROR";
        }
        return "none";
    }

    private Map<String, String> buildFormulas() {
        Map<String, String> formulas = new LinkedHashMap<>();
        formulas.put("reliability", "1 - (error_calls / total_calls)");
        formulas.put("flagged_rate", "flagged_calls / total_calls");
        formulas.put("p95_latency", "p95(latency_ms over successful calls within window)");
        formulas.put("total_cost", "sum(cost_usd over calls within window)");
        return formulas;
    }

    private FlagPolicyDTO buildFlagPolicy() {
        List<FlagRuleDTO> rules = List.of(
            new FlagRuleDTO("ERROR", "call_status=ERROR"),
            new FlagRuleDTO("PROVIDER_ERROR", "http_status >= 500"),
            new FlagRuleDTO("BAD_REQUEST", "400 <= http_status < 500"),
            new FlagRuleDTO("TIMEOUT", "error_type contains timeout"),
            new FlagRuleDTO("STREAM_INTERRUPTED", "stream closed before completion")
        );

        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("latency", 1.5d);
        factors.put("cost", 2.0d);

        return FlagPolicyDTO.builder()
            .version(AilurosControlService.FLAG_POLICY_VERSION)
            .rules(rules)
            .kFactors(factors)
            .build();
    }

    private Map<LocalDate, BigDecimal> initDailyBigDecimal(Window window) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        LocalDate start = window.from.atZone(window.zoneId).toLocalDate();
        LocalDate end = window.to.atZone(window.zoneId).toLocalDate();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            out.put(day, BigDecimal.ZERO);
        }
        return out;
    }

    private Map<LocalDate, long[]> initDailyCounters(Window window) {
        Map<LocalDate, long[]> out = new LinkedHashMap<>();
        LocalDate start = window.from.atZone(window.zoneId).toLocalDate();
        LocalDate end = window.to.atZone(window.zoneId).toLocalDate();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            out.put(day, new long[]{0, 0});
        }
        return out;
    }

    private LocalDate eventDate(AcCall call, ZoneId zoneId) {
        Instant ts = call.getRequestTs() != null ? call.getRequestTs() : call.getCreatedAt();
        return ts.atZone(zoneId).toLocalDate();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String defaultValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String effectiveAppId(String appId) {
        return defaultValue(appId, "clarity");
    }

    private Window resolveWindow(String range, Instant from, Instant to, String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(defaultValue(timezone, "UTC"));
        } catch (Exception ex) {
            zoneId = ZoneId.of("UTC");
        }

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom;
        String rangeLabel;

        if (from != null) {
            effectiveFrom = from;
            long days = Math.max(1, ChronoUnit.DAYS.between(effectiveFrom, effectiveTo));
            rangeLabel = days + "d";
        } else {
            String safeRange = defaultValue(range, "7d").toLowerCase(Locale.ROOT);
            if (safeRange.endsWith("h")) {
                long hours = parseLong(safeRange.replace("h", ""), 24L);
                effectiveFrom = effectiveTo.minus(hours, ChronoUnit.HOURS);
                rangeLabel = hours + "h";
            } else {
                long days = parseLong(safeRange.replace("d", ""), 7L);
                long dayWindow = Math.max(1, days);
                effectiveFrom = effectiveTo.minus(dayWindow - 1, ChronoUnit.DAYS);
                rangeLabel = days + "d";
            }
        }

        DashboardWindowDTO dto = DashboardWindowDTO.builder()
            .start(effectiveFrom)
            .end(effectiveTo)
            .timezone(zoneId.getId())
            .granularity("day")
            .build();

        return new Window(effectiveFrom, effectiveTo, zoneId, dto, rangeLabel);
    }

    private long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record Window(Instant from, Instant to, ZoneId zoneId, DashboardWindowDTO dto, String rangeLabel) {}
}
