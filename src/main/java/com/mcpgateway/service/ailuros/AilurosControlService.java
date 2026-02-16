package com.mcpgateway.service.ailuros;

import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcCallFlag;
import com.mcpgateway.dto.ailuros.*;
import com.mcpgateway.repository.ailuros.AcCallFlagRepository;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ailuros Control: Main Service Layer
 *
 * Provides business logic for:
 * - Querying and filtering calls
 * - Managing flags
 * - Cost analysis
 * - KPI calculations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosControlService {

    public static final String FLAG_POLICY_VERSION = "flag-policy@2026.02.14.1";
    private static final double LATENCY_K_FACTOR = 1.5d;
    private static final double COST_K_FACTOR = 2.0d;
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final AcCallRepository callRepository;
    private final AcCallFlagRepository flagRepository;

    /**
     * Get calls with advanced filtering
     */
    @Transactional(readOnly = true)
    public Page<CallListDTO> getCalls(CallFilterRequest filter, Pageable pageable) {
        Specification<AcCall> spec = buildSpecification(filter);
        FlagEvaluationContext context = buildFlagContext(
            filter.getProjectKey(),
            filter.getFrom(),
            filter.getTo(),
            filter.getTimezone());

        Page<AcCall> calls = callRepository.findAll(spec, pageable);

        return calls.map(call -> toCallListDTO(call, context));
    }

    /**
     * Get call detail by ID
     */
    @Transactional(readOnly = true)
    public CallDetailDTO getCallDetail(UUID id) {
        return getCallDetail(id, null, null, DEFAULT_TIMEZONE);
    }

    @Transactional(readOnly = true)
    public CallDetailDTO getCallDetail(UUID id, Instant from, Instant to, String timezone) {
        AcCall call = callRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Call not found: " + id));

        Instant fromDate = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toDate = to != null ? to : Instant.now();
        FlagEvaluationContext context = buildFlagContext(call.getProjectKey(), fromDate, toDate, timezone);

        return toCallDetailDTO(call, context);
    }

    /**
     * Get call detail by trace ID
     */
    @Transactional(readOnly = true)
    public CallDetailDTO getCallByTraceId(String traceId) {
        return getCallByTraceId(traceId, null, null, DEFAULT_TIMEZONE);
    }

    @Transactional(readOnly = true)
    public CallDetailDTO getCallByTraceId(String traceId, Instant from, Instant to, String timezone) {
        AcCall call = callRepository.findByTraceId(traceId)
            .orElseThrow(() -> new RuntimeException("Call not found: " + traceId));

        Instant fromDate = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toDate = to != null ? to : Instant.now();
        FlagEvaluationContext context = buildFlagContext(call.getProjectKey(), fromDate, toDate, timezone);

        return toCallDetailDTO(call, context);
    }

    /**
     * Create a flag for a call
     */
    @Transactional
    public FlagDTO createFlag(UUID callId, CreateFlagRequest request) {
        AcCall call = callRepository.findById(callId)
            .orElseThrow(() -> new RuntimeException("Call not found: " + callId));

        AcCallFlag flag = AcCallFlag.builder()
            .call(call)
            .flagType(request.getFlagType())
            .note(request.getNote())
            .createdBy(request.getCreatedBy())
            .build();

        flag = flagRepository.save(flag);

        log.info("Created flag: callId={}, type={}, createdBy={}",
                callId, request.getFlagType(), request.getCreatedBy());

        return toFlagDTO(flag);
    }

    /**
     * Get cost summary
     */
    @Transactional(readOnly = true)
    public CostSummaryDTO getCostSummary(String projectKey, Instant from, Instant to, String timezone) {
        // Total cost
        BigDecimal totalCost = callRepository.calculateTotalCost(projectKey, from, to);

        // Daily costs
        List<Object[]> dailyData = callRepository.calculateDailyCost(projectKey, from, to);
        List<DailyCostDTO> dailyCosts = dailyData.stream()
            .map(this::toDailyCostDTO)
            .collect(Collectors.toList());

        // Costs by model
        List<Object[]> modelData = callRepository.calculateCostByModel(projectKey, from, to);
        List<ModelCostDTO> costsByModel = modelData.stream()
            .map(row -> {
                BigDecimal cost = toBigDecimal(row[1]);
                BigDecimal percentage = totalCost.compareTo(BigDecimal.ZERO) > 0
                    ? cost.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

                return ModelCostDTO.builder()
                    .model((String) row[0])
                    .cost(cost)
                    .percentage(percentage)
                    .build();
            })
            .collect(Collectors.toList());

        // Forecast (simple linear projection)
        BigDecimal forecastedCost = calculateForecast(dailyCosts, from, to);

        // Trend
        CostTrendDTO trend = calculateCostTrend(projectKey, from, to);

        return CostSummaryDTO.builder()
            .totalCost(totalCost)
            .forecastedCost(forecastedCost)
            .dailyCosts(dailyCosts)
            .costsByModel(costsByModel)
            .trend(trend)
            .window(buildWindow(from, to, effectiveTimezone(timezone)))
            .build();
    }

    /**
     * Get overview KPIs
     */
    @Transactional(readOnly = true)
    public OverviewKpiDTO getOverviewKpis(String projectKey, Instant from, Instant to, String timezone) {
        FlagEvaluationContext context = buildFlagContext(projectKey, from, to, timezone);
        // Total calls and errors
        Long totalCalls = callRepository.countTotal(projectKey, from, to);
        Long errorCalls = callRepository.countErrors(projectKey, from, to);

        BigDecimal errorRate = totalCalls > 0
            ? BigDecimal.valueOf(errorCalls).divide(BigDecimal.valueOf(totalCalls), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal reliabilityScore = BigDecimal.valueOf(100).subtract(errorRate);

        // Flagged calls
        Long flaggedCount = callRepository.countFlaggedCalls(projectKey, from, to);
        BigDecimal flaggedPercentage = totalCalls > 0
            ? BigDecimal.valueOf(flaggedCount).divide(BigDecimal.valueOf(totalCalls), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // Cost
        BigDecimal totalCost = callRepository.calculateTotalCost(projectKey, from, to);
        CostTrendDTO costTrend = calculateCostTrend(projectKey, from, to);

        // Latency
        Double p95Latency = calculateP95Latency(projectKey, from, to);

        // Recent flagged calls
        Pageable flaggedPageable = PageRequest.of(0, 10);
        Page<AcCall> flaggedCalls = callRepository.findFlaggedCalls(projectKey, from, to, flaggedPageable);
        List<CallListDTO> recentFlagged = flaggedCalls.stream()
            .map(call -> toCallListDTO(call, context))
            .collect(Collectors.toList());

        // Cost over time
        List<Object[]> dailyData = callRepository.calculateDailyCost(projectKey, from, to);
        List<DailyCostDTO> costOverTime = dailyData.stream()
            .map(this::toDailyCostDTO)
            .collect(Collectors.toList());

        return OverviewKpiDTO.builder()
            .reliabilityScore(reliabilityScore)
            .totalCalls(totalCalls)
            .errorCalls(errorCalls)
            .errorRate(errorRate)
            .flaggedCallsCount(flaggedCount)
            .flaggedPercentage(flaggedPercentage)
            .totalCost(totalCost)
            .costTrend(costTrend.getChangePercentage())
            .p95LatencyMs(p95Latency)
            .recentFlaggedCalls(recentFlagged)
            .costOverTime(costOverTime)
            .window(buildWindow(from, to, effectiveTimezone(timezone)))
            .flagPolicy(buildFlagPolicy())
            .kpiFormulas(buildKpiFormulas())
            .build();
    }

    /**
     * Build JPA Specification from filter request
     */
    private Specification<AcCall> buildSpecification(CallFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getProjectKey() != null) {
                predicates.add(cb.equal(root.get("projectKey"), filter.getProjectKey()));
            }

            if (filter.getEnv() != null) {
                predicates.add(cb.equal(root.get("env"), filter.getEnv()));
            }

            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
            }

            if (filter.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getTo()));
            }

            if (filter.getModel() != null) {
                predicates.add(cb.equal(root.get("model"), filter.getModel()));
            }

            if (filter.getProvider() != null) {
                predicates.add(cb.equal(root.get("provider"), filter.getProvider()));
            }

            if (filter.getPromptRef() != null) {
                predicates.add(cb.equal(root.get("promptRef"), filter.getPromptRef()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (Boolean.TRUE.equals(filter.getFlaggedOnly())) {
                var flaggedSubquery = query.subquery(UUID.class);
                var flagRoot = flaggedSubquery.from(AcCallFlag.class);
                flaggedSubquery.select(flagRoot.get("call").get("id"));
                flaggedSubquery.where(cb.equal(flagRoot.get("call").get("id"), root.get("id")));
                predicates.add(cb.exists(flaggedSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Calculate cost forecast using simple linear regression
     */
    private BigDecimal calculateForecast(List<DailyCostDTO> dailyCosts, Instant from, Instant to) {
        if (dailyCosts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Average daily cost
        BigDecimal avgDailyCost = dailyCosts.stream()
            .map(DailyCostDTO::getCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(dailyCosts.size()), 6, RoundingMode.HALF_UP);

        // Project to end of month
        long daysInPeriod = ChronoUnit.DAYS.between(from, to);
        long daysRemaining = 30 - daysInPeriod;

        if (daysRemaining <= 0) {
            return BigDecimal.ZERO;
        }

        return avgDailyCost.multiply(BigDecimal.valueOf(daysRemaining));
    }

    /**
     * Calculate cost trend
     */
    private CostTrendDTO calculateCostTrend(String projectKey, Instant from, Instant to) {
        long days = ChronoUnit.DAYS.between(from, to);

        Instant previousFrom = from.minus(days, ChronoUnit.DAYS);
        Instant previousTo = from;

        BigDecimal currentCost = callRepository.calculateTotalCost(projectKey, from, to);
        BigDecimal previousCost = callRepository.calculateTotalCost(projectKey, previousFrom, previousTo);

        BigDecimal changeAmount = currentCost.subtract(previousCost);
        BigDecimal changePercentage = previousCost.compareTo(BigDecimal.ZERO) > 0
            ? changeAmount.divide(previousCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        String trend = changePercentage.compareTo(BigDecimal.ZERO) > 0 ? "up"
            : changePercentage.compareTo(BigDecimal.ZERO) < 0 ? "down"
            : "stable";

        return CostTrendDTO.builder()
            .currentPeriodCost(currentCost)
            .previousPeriodCost(previousCost)
            .changeAmount(changeAmount)
            .changePercentage(changePercentage)
            .trend(trend)
            .build();
    }

    private Double calculateP95Latency(String projectKey, Instant from, Instant to) {
        List<Integer> latencies = callRepository.findLatencyValues(projectKey, from, to);
        if (latencies.isEmpty()) {
            return null;
        }

        int index = (int) Math.ceil(latencies.size() * 0.95d) - 1;
        index = Math.max(0, Math.min(index, latencies.size() - 1));
        return latencies.get(index).doubleValue();
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String text) {
            return LocalDate.parse(text);
        }
        throw new IllegalArgumentException("Unsupported date type: " + value.getClass().getName());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private DailyCostDTO toDailyCostDTO(Object[] row) {
        LocalDate date = toLocalDate(row[0]);
        BigDecimal cost = toBigDecimal(row[1]);
        Long callCount = toLong(row[2]);
        Long errorCount = toLong(row[3]);
        BigDecimal errorRate = callCount > 0
            ? BigDecimal.valueOf(errorCount)
                .divide(BigDecimal.valueOf(callCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        return DailyCostDTO.builder()
            .date(date)
            .cost(cost)
            .callCount(callCount)
            .errorCount(errorCount)
            .errorRate(errorRate)
            .build();
    }

    private DashboardWindowDTO buildWindow(Instant from, Instant to, String timezone) {
        return DashboardWindowDTO.builder()
            .start(from)
            .end(to)
            .timezone(effectiveTimezone(timezone))
            .granularity("day")
            .build();
    }

    private String effectiveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (Exception ex) {
            return DEFAULT_TIMEZONE;
        }
    }

    private FlagPolicyDTO buildFlagPolicy() {
        List<FlagRuleDTO> rules = List.of(
            new FlagRuleDTO("ERROR", "call_status=ERROR"),
            new FlagRuleDTO("LATENCY_P95_SPIKE", "latency_ms > p95(window)*k"),
            new FlagRuleDTO("COST_SPIKE", "cost_usd > rolling_avg(window)*k"),
            new FlagRuleDTO("MODEL_CHANGED", "model != baseline_model"),
            new FlagRuleDTO("PROMPT_CHANGED", "prompt_hash != baseline_prompt_hash")
        );

        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("latency", LATENCY_K_FACTOR);
        factors.put("cost", COST_K_FACTOR);

        return FlagPolicyDTO.builder()
            .version(FLAG_POLICY_VERSION)
            .rules(rules)
            .kFactors(factors)
            .build();
    }

    private Map<String, String> buildKpiFormulas() {
        Map<String, String> formulas = new LinkedHashMap<>();
        formulas.put("reliability", "1 - (error_calls / total_calls)");
        formulas.put("flagged_rate", "flagged_calls / total_calls");
        formulas.put("p95_latency", "p95(latency_ms over successful calls within window)");
        formulas.put("total_cost", "sum(cost_usd over calls within window)");
        return formulas;
    }

    // DTO Conversion Methods

    private CallListDTO toCallListDTO(AcCall call, FlagEvaluationContext context) {
        Long flagCount = flagRepository.countByCallId(call.getId());
        boolean flagged = flagCount > 0;
        List<String> reasonCodes = evaluateReasonCodes(call, context, flagged);

        return CallListDTO.builder()
            .id(call.getId())
            .traceId(call.getTraceId())
            .projectKey(call.getProjectKey())
            .env(call.getEnv())
            .status(call.getStatus())
            .provider(call.getProvider())
            .model(call.getModel())
            .promptRef(call.getPromptRef())
            .tokensTotal(call.getTokensTotal())
            .costEstimateUsd(call.getCostEstimateUsd())
            .latencyMs(call.getLatencyMs())
            .createdAt(call.getCreatedAt())
            .isFlagged(flagged)
            .flagCount(flagCount)
            .flagReasonCodes(reasonCodes)
            .flagRuleVersion(FLAG_POLICY_VERSION)
            .build();
    }

    private CallDetailDTO toCallDetailDTO(AcCall call, FlagEvaluationContext context) {
        List<AcCallFlag> flags = flagRepository.findByCallIdOrderByCreatedAtDesc(call.getId());
        List<String> reasonCodes = evaluateReasonCodes(call, context, !flags.isEmpty());
        String bucket = call.getCreatedAt() == null
            ? null
            : call.getCreatedAt().atZone(ZoneId.of(context.timezone())).toLocalDate().toString();

        return CallDetailDTO.builder()
            .id(call.getId())
            .traceId(call.getTraceId())
            .projectKey(call.getProjectKey())
            .env(call.getEnv())
            .status(call.getStatus())
            .provider(call.getProvider())
            .model(call.getModel())
            .temperature(call.getTemperature())
            .topP(call.getTopP())
            .promptRef(call.getPromptRef())
            .requestText(call.getRequestText())
            .requestSha256(call.getRequestSha256())
            .responseText(call.getResponseText())
            .responseSha256(call.getResponseSha256())
            .tokensPrompt(call.getTokensPrompt())
            .tokensCompletion(call.getTokensCompletion())
            .tokensTotal(call.getTokensTotal())
            .costEstimateUsd(call.getCostEstimateUsd())
            .latencyMs(call.getLatencyMs())
            .upstreamRequestId(call.getUpstreamRequestId())
            .createdAt(call.getCreatedAt())
            .windowBucket(bucket)
            .flagReasonCodes(reasonCodes)
            .flagRuleVersion(FLAG_POLICY_VERSION)
            .baselineRef(BaselineRefDTO.builder()
                .latencyP95Ms(context.p95LatencyMs())
                .costRollingAvgUsd(context.avgCostUsd())
                .baselineModel(context.baselineModel())
                .baselinePromptHash(context.baselinePromptHash())
                .build())
            .flags(flags.stream().map(this::toFlagDTO).collect(Collectors.toList()))
            .build();
    }

    private FlagDTO toFlagDTO(AcCallFlag flag) {
        return FlagDTO.builder()
            .id(flag.getId())
            .callId(flag.getCall().getId())
            .flagType(flag.getFlagType())
            .note(flag.getNote())
            .createdBy(flag.getCreatedBy())
            .createdAt(flag.getCreatedAt())
            .build();
    }

    private FlagEvaluationContext buildFlagContext(String projectKey, Instant from, Instant to, String timezone) {
        String effectiveProjectKey = projectKey != null ? projectKey : "default";
        Instant effectiveFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveTo = to != null ? to : Instant.now();
        String effectiveTimezone = effectiveTimezone(timezone);

        Long totalCalls = callRepository.countTotal(effectiveProjectKey, effectiveFrom, effectiveTo);
        BigDecimal totalCost = callRepository.calculateTotalCost(effectiveProjectKey, effectiveFrom, effectiveTo);
        BigDecimal avgCost = totalCalls != null && totalCalls > 0
            ? totalCost.divide(BigDecimal.valueOf(totalCalls), 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        Double p95Latency = calculateP95Latency(effectiveProjectKey, effectiveFrom, effectiveTo);

        String baselineModel = null;
        List<Object[]> modelCounts = callRepository.countByModelInWindow(effectiveProjectKey, effectiveFrom, effectiveTo);
        if (!modelCounts.isEmpty() && modelCounts.get(0)[0] != null) {
            baselineModel = modelCounts.get(0)[0].toString();
        }

        String baselinePromptRef = null;
        List<Object[]> promptCounts = callRepository.countByPromptRefInWindow(effectiveProjectKey, effectiveFrom, effectiveTo);
        if (!promptCounts.isEmpty() && promptCounts.get(0)[0] != null) {
            baselinePromptRef = promptCounts.get(0)[0].toString();
        }

        return new FlagEvaluationContext(
            effectiveProjectKey,
            effectiveFrom,
            effectiveTo,
            effectiveTimezone,
            p95Latency,
            avgCost,
            baselineModel,
            hashText(baselinePromptRef)
        );
    }

    private List<String> evaluateReasonCodes(AcCall call, FlagEvaluationContext context, boolean manuallyFlagged) {
        List<String> reasons = new ArrayList<>();

        if (call != null && call.getStatus() != null && "error".equalsIgnoreCase(call.getStatus())) {
            reasons.add("ERROR");
        }

        if (call != null && call.getLatencyMs() != null && context.p95LatencyMs() != null) {
            double threshold = context.p95LatencyMs() * LATENCY_K_FACTOR;
            if (call.getLatencyMs() > threshold) {
                reasons.add("LATENCY_P95_SPIKE");
            }
        }

        if (call != null && call.getCostEstimateUsd() != null
                && context.avgCostUsd() != null
                && context.avgCostUsd().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal threshold = context.avgCostUsd().multiply(BigDecimal.valueOf(COST_K_FACTOR));
            if (call.getCostEstimateUsd().compareTo(threshold) > 0) {
                reasons.add("COST_SPIKE");
            }
        }

        if (call != null && context.baselineModel() != null && call.getModel() != null
                && !context.baselineModel().equals(call.getModel())) {
            reasons.add("MODEL_CHANGED");
        }

        if (call != null && context.baselinePromptHash() != null) {
            String callPromptHash = hashText(call.getPromptRef());
            if (callPromptHash != null && !context.baselinePromptHash().equals(callPromptHash)) {
                reasons.add("PROMPT_CHANGED");
            }
        }

        if (reasons.isEmpty() && manuallyFlagged) {
            reasons.add("MANUAL_FLAG");
        }

        return reasons;
    }

    private String hashText(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String part = Integer.toHexString(0xff & b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private record FlagEvaluationContext(
        String projectKey,
        Instant from,
        Instant to,
        String timezone,
        Double p95LatencyMs,
        BigDecimal avgCostUsd,
        String baselineModel,
        String baselinePromptHash
    ) {}

    /**
     * Filter request object
     */
    public static class CallFilterRequest {
        private String projectKey;
        private String env;
        private Instant from;
        private Instant to;
        private String model;
        private String provider;
        private String promptRef;
        private String status;
        private Boolean flaggedOnly;
        private String timezone;

        // Getters and setters
        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }

        public Instant getFrom() { return from; }
        public void setFrom(Instant from) { this.from = from; }

        public Instant getTo() { return to; }
        public void setTo(Instant to) { this.to = to; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getPromptRef() { return promptRef; }
        public void setPromptRef(String promptRef) { this.promptRef = promptRef; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Boolean getFlaggedOnly() { return flaggedOnly; }
        public void setFlaggedOnly(Boolean flaggedOnly) { this.flaggedOnly = flaggedOnly; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
    }
}
