package com.mcpgateway.service.ailuros;

import com.mcpgateway.config.AilurosGovernanceProperties;
import com.mcpgateway.domain.ailuros.AcBudgetEval;
import com.mcpgateway.domain.ailuros.AcBudgetPolicy;
import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.dto.ailuros.BudgetEvalResultDTO;
import com.mcpgateway.dto.ailuros.BudgetEvaluateResponseDTO;
import com.mcpgateway.dto.ailuros.BudgetPolicyDTO;
import com.mcpgateway.dto.ailuros.BudgetStatusDTO;
import com.mcpgateway.dto.ailuros.DashboardWindowDTO;
import com.mcpgateway.dto.ailuros.TimeseriesPointDTO;
import com.mcpgateway.repository.ailuros.AcBudgetEvalRepository;
import com.mcpgateway.repository.ailuros.AcBudgetPolicyRepository;
import com.mcpgateway.repository.ailuros.AcCallRepository;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosBudgetGuardService {

    private static final String DEFAULT_APP_ID = "clarity";
    private static final String DEFAULT_ENV = "prod";
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final AcBudgetPolicyRepository budgetPolicyRepository;
    private final AcBudgetEvalRepository budgetEvalRepository;
    private final AcCallRepository callRepository;
    private final AilurosGovernanceWebhookService webhookService;
    private final AilurosGovernanceProperties governanceProperties;

    @Transactional(readOnly = true)
    public List<BudgetPolicyDTO> getPolicies(String appId, String env) {
        String effectiveAppId = normalizeText(appId);
        String effectiveEnv = normalizeText(env);

        List<AcBudgetPolicy> policies;
        if (effectiveAppId != null && effectiveEnv != null) {
            policies = budgetPolicyRepository.findByAppIdAndEnvOrderByUpdatedTsDesc(effectiveAppId, effectiveEnv);
        } else {
            policies = budgetPolicyRepository.findByIsEnabledTrueOrderByUpdatedTsDesc();
        }

        return policies.stream().map(this::toDto).toList();
    }

    @Transactional
    public BudgetPolicyDTO savePolicy(BudgetPolicyDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("policy payload cannot be null");
        }
        if (request.getAppId() == null || request.getAppId().isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        if (request.getEnv() == null || request.getEnv().isBlank()) {
            throw new IllegalArgumentException("env is required");
        }

        String route = normalizeRoute(request.getRoute());
        AcBudgetPolicy policy;
        if (request.getId() != null) {
            policy = budgetPolicyRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("budget policy not found: " + request.getId()));
        } else {
            policy = budgetPolicyRepository.findExactPolicy(request.getAppId(), request.getEnv(), route)
                .orElseGet(AcBudgetPolicy::new);
        }

        policy.setAppId(request.getAppId().trim());
        policy.setEnv(request.getEnv().trim());
        policy.setRoute(route);
        policy.setDailyUsdLimit(scale6(request.getDailyUsdLimit()));
        policy.setMonthlyUsdLimit(scale6(request.getMonthlyUsdLimit()));
        policy.setForecastMonthlyUsdLimit(scale6(request.getForecastMonthlyUsdLimit()));
        policy.setIsEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());

        return toDto(budgetPolicyRepository.save(policy));
    }

    @Transactional
    public BudgetEvaluateResponseDTO evaluatePolicies(String appId, String env, String route, String trigger) {
        String effectiveAppId = normalizeText(appId);
        String effectiveEnv = normalizeText(env);
        String normalizedRoute = normalizeRoute(route);

        List<AcBudgetPolicy> policies = budgetPolicyRepository.findEnabledPolicies(
            effectiveAppId,
            effectiveEnv,
            normalizedRoute
        );

        Instant now = Instant.now();
        List<BudgetEvalResultDTO> evaluations = new ArrayList<>();
        for (AcBudgetPolicy policy : policies) {
            evaluations.add(evaluateOnePolicy(policy, now, trigger));
        }

        return BudgetEvaluateResponseDTO.builder()
            .evaluatedAt(now)
            .trigger(normalizeText(trigger) != null ? trigger : "manual")
            .evaluations(evaluations)
            .build();
    }

    @Transactional(readOnly = true)
    public BudgetStatusDTO getBudgetStatus(String appId,
                                           String env,
                                           String range,
                                           Instant from,
                                           Instant to,
                                           String timezone) {
        Window window = resolveWindow(range, from, to, timezone);

        String effectiveAppId = normalizeText(appId) != null ? normalizeText(appId) : DEFAULT_APP_ID;
        String effectiveEnv = normalizeText(env) != null ? normalizeText(env) : DEFAULT_ENV;

        List<AcCall> calls = loadCalls(effectiveAppId, effectiveEnv, null, window.from, window.to);
        Map<LocalDate, BigDecimal> dailyCosts = initDailyCosts(window);
        for (AcCall call : calls) {
            LocalDate day = eventDate(call, window.zoneId);
            dailyCosts.computeIfPresent(day, (k, v) -> v.add(nullSafe(call.getCostEstimateUsd())));
        }

        Instant monthStart = Instant.now().atZone(window.zoneId)
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(window.zoneId)
            .toInstant();
        BigDecimal monthToDate = calculateTotalCost(loadCalls(effectiveAppId, effectiveEnv, null, monthStart, Instant.now()));

        List<AcBudgetPolicy> policies = budgetPolicyRepository.findEnabledPolicies(effectiveAppId, effectiveEnv, null);
        BigDecimal monthlyLimit = policies.stream()
            .filter(policy -> policy.getRoute() == null)
            .map(AcBudgetPolicy::getMonthlyUsdLimit)
            .filter(limit -> limit != null && limit.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            monthlyLimit = null;
        }

        List<AcBudgetEval> monthEvaluations = budgetEvalRepository.findInWindow(
            effectiveAppId,
            effectiveEnv,
            null,
            monthStart,
            Instant.now()
        );

        BigDecimal forecast = monthEvaluations.stream()
            .map(AcBudgetEval::getForecastMonthlyUsd)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElse(null);

        long exceededCount = budgetEvalRepository.countByStatusInWindow(
            effectiveAppId,
            effectiveEnv,
            window.from,
            window.to,
            List.of(AcBudgetEval.Status.EXCEEDED, AcBudgetEval.Status.FORECAST_EXCEEDED)
        );

        List<TimeseriesPointDTO> points = dailyCosts.entrySet().stream()
            .map(entry -> TimeseriesPointDTO.builder()
                .date(entry.getKey())
                .value(scale4(entry.getValue()))
                .build())
            .toList();

        return BudgetStatusDTO.builder()
            .appId(effectiveAppId)
            .env(effectiveEnv)
            .range(window.rangeLabel)
            .window(window.dto)
            .monthToDateCostUsd(scale4(monthToDate))
            .monthlyLimitUsd(monthlyLimit != null ? scale4(monthlyLimit) : null)
            .forecastMonthlyUsd(forecast != null ? scale4(forecast) : null)
            .exceededCount(exceededCount)
            .dailyCost(points)
            .build();
    }

    private BudgetEvalResultDTO evaluateOnePolicy(AcBudgetPolicy policy, Instant now, String trigger) {
        ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
        Instant dayStart = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant();
        Instant monthStart = now.atZone(zoneId).withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId).toInstant();

        List<AcCall> dayCalls = loadCalls(policy.getAppId(), policy.getEnv(), policy.getRoute(), dayStart, now);
        List<AcCall> monthCalls = loadCalls(policy.getAppId(), policy.getEnv(), policy.getRoute(), monthStart, now);

        BigDecimal dayCost = calculateTotalCost(dayCalls);
        BigDecimal monthCost = calculateTotalCost(monthCalls);
        BigDecimal forecastMonthly = estimateForecastMonthly(policy, now, zoneId);

        AcBudgetEval.Status status = AcBudgetEval.Status.OK;
        BigDecimal limitUsd = null;
        BigDecimal costForStatus = monthCost;
        Instant windowStart = monthStart;

        if (isExceeded(dayCost, policy.getDailyUsdLimit())) {
            status = AcBudgetEval.Status.EXCEEDED;
            limitUsd = policy.getDailyUsdLimit();
            costForStatus = dayCost;
            windowStart = dayStart;
        } else if (isExceeded(monthCost, policy.getMonthlyUsdLimit())) {
            status = AcBudgetEval.Status.EXCEEDED;
            limitUsd = policy.getMonthlyUsdLimit();
            costForStatus = monthCost;
            windowStart = monthStart;
        } else if (isExceeded(forecastMonthly, policy.getForecastMonthlyUsdLimit())) {
            status = AcBudgetEval.Status.FORECAST_EXCEEDED;
            limitUsd = policy.getForecastMonthlyUsdLimit();
            costForStatus = monthCost;
            windowStart = monthStart;
        }

        AcBudgetEval eval = AcBudgetEval.builder()
            .appId(policy.getAppId())
            .env(policy.getEnv())
            .route(policy.getRoute())
            .windowStartTs(windowStart)
            .windowEndTs(now)
            .costUsd(scale6(costForStatus))
            .limitUsd(scale6(limitUsd))
            .status(status)
            .forecastMonthlyUsd(scale6(forecastMonthly))
            .build();
        eval = budgetEvalRepository.save(eval);

        if (status != AcBudgetEval.Status.OK) {
            String eventType = status == AcBudgetEval.Status.EXCEEDED
                ? "budget_exceeded"
                : "budget_forecast_exceeded";
            webhookService.notifyEvent(eventType, Map.of(
                "app_id", policy.getAppId(),
                "env", policy.getEnv(),
                "route", policy.getRoute() != null ? policy.getRoute() : "all",
                "status", status.name(),
                "cost_usd", eval.getCostUsd(),
                "limit_usd", eval.getLimitUsd(),
                "forecast_monthly_usd", eval.getForecastMonthlyUsd(),
                "window_start_ts", eval.getWindowStartTs(),
                "window_end_ts", eval.getWindowEndTs(),
                "trigger", normalizeText(trigger) != null ? trigger : "scheduled"
            ));
        }

        return toDto(eval);
    }

    private BigDecimal estimateForecastMonthly(AcBudgetPolicy policy, Instant now, ZoneId zoneId) {
        int forecastDays = Math.max(1, governanceProperties.getBudget().getForecastDays());
        Instant forecastStart = now.minus(forecastDays - 1L, ChronoUnit.DAYS)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant();

        List<AcCall> recentCalls = loadCalls(policy.getAppId(), policy.getEnv(), policy.getRoute(), forecastStart, now);
        BigDecimal periodCost = calculateTotalCost(recentCalls);
        BigDecimal averageDaily = periodCost.divide(BigDecimal.valueOf(forecastDays), 6, RoundingMode.HALF_UP);
        int monthDays = YearMonth.from(now.atZone(zoneId)).lengthOfMonth();
        return averageDaily.multiply(BigDecimal.valueOf(monthDays));
    }

    private boolean isExceeded(BigDecimal value, BigDecimal limit) {
        return value != null
            && limit != null
            && limit.compareTo(BigDecimal.ZERO) > 0
            && value.compareTo(limit) > 0;
    }

    private List<AcCall> loadCalls(String appId, String env, String route, Instant from, Instant to) {
        Specification<AcCall> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (appId != null && !appId.isBlank()) {
                predicates.add(cb.or(
                    cb.equal(root.get("projectKey"), appId),
                    cb.equal(root.get("appId"), appId)
                ));
            }

            if (env != null && !env.isBlank()) {
                predicates.add(cb.equal(root.get("env"), env));
            }

            if (route != null && !route.isBlank()) {
                predicates.add(cb.equal(root.get("route"), route));
            }

            var ts = cb.function("COALESCE", Instant.class, root.get("requestTs"), root.get("createdAt"));
            predicates.add(cb.greaterThanOrEqualTo(ts, from));
            predicates.add(cb.lessThanOrEqualTo(ts, to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return callRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    private Map<LocalDate, BigDecimal> initDailyCosts(Window window) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate start = window.from.atZone(window.zoneId).toLocalDate();
        LocalDate end = window.to.atZone(window.zoneId).toLocalDate();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            map.put(day, BigDecimal.ZERO);
        }
        return map;
    }

    private LocalDate eventDate(AcCall call, ZoneId zoneId) {
        Instant ts = call.getRequestTs() != null ? call.getRequestTs() : call.getCreatedAt();
        return ts.atZone(zoneId).toLocalDate();
    }

    private BigDecimal calculateTotalCost(List<AcCall> calls) {
        return calls.stream()
            .map(call -> nullSafe(call.getCostEstimateUsd()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BudgetPolicyDTO toDto(AcBudgetPolicy policy) {
        return BudgetPolicyDTO.builder()
            .id(policy.getId())
            .appId(policy.getAppId())
            .env(policy.getEnv())
            .route(policy.getRoute())
            .dailyUsdLimit(scale6(policy.getDailyUsdLimit()))
            .monthlyUsdLimit(scale6(policy.getMonthlyUsdLimit()))
            .forecastMonthlyUsdLimit(scale6(policy.getForecastMonthlyUsdLimit()))
            .enabled(policy.getIsEnabled())
            .createdTs(policy.getCreatedTs())
            .updatedTs(policy.getUpdatedTs())
            .build();
    }

    private BudgetEvalResultDTO toDto(AcBudgetEval eval) {
        return BudgetEvalResultDTO.builder()
            .id(eval.getId())
            .appId(eval.getAppId())
            .env(eval.getEnv())
            .route(eval.getRoute())
            .windowStartTs(eval.getWindowStartTs())
            .windowEndTs(eval.getWindowEndTs())
            .costUsd(scale6(eval.getCostUsd()))
            .limitUsd(scale6(eval.getLimitUsd()))
            .forecastMonthlyUsd(scale6(eval.getForecastMonthlyUsd()))
            .status(eval.getStatus())
            .createdTs(eval.getCreatedTs())
            .build();
    }

    private String normalizeText(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRoute(String route) {
        String value = normalizeText(route);
        if (value == null || "*".equals(value) || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale6(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal scale4(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
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

        return new Window(effectiveFrom, effectiveTo, zoneId, dto, rangeLabel);
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record Window(Instant from, Instant to, ZoneId zoneId, DashboardWindowDTO dto, String rangeLabel) {}
}
