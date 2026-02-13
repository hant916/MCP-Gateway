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

    private final AcCallRepository callRepository;
    private final AcCallFlagRepository flagRepository;

    /**
     * Get calls with advanced filtering
     */
    @Transactional(readOnly = true)
    public Page<CallListDTO> getCalls(CallFilterRequest filter, Pageable pageable) {
        Specification<AcCall> spec = buildSpecification(filter);

        Page<AcCall> calls = callRepository.findAll(spec, pageable);

        return calls.map(this::toCallListDTO);
    }

    /**
     * Get call detail by ID
     */
    @Transactional(readOnly = true)
    public CallDetailDTO getCallDetail(UUID id) {
        AcCall call = callRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Call not found: " + id));

        return toCallDetailDTO(call);
    }

    /**
     * Get call detail by trace ID
     */
    @Transactional(readOnly = true)
    public CallDetailDTO getCallByTraceId(String traceId) {
        AcCall call = callRepository.findByTraceId(traceId)
            .orElseThrow(() -> new RuntimeException("Call not found: " + traceId));

        return toCallDetailDTO(call);
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
    public CostSummaryDTO getCostSummary(String projectKey, Instant from, Instant to) {
        // Total cost
        BigDecimal totalCost = callRepository.calculateTotalCost(projectKey, from, to);

        // Daily costs
        List<Object[]> dailyData = callRepository.calculateDailyCost(projectKey, from, to);
        List<DailyCostDTO> dailyCosts = dailyData.stream()
            .map(row -> DailyCostDTO.builder()
                .date(((java.sql.Date) row[0]).toLocalDate())
                .cost((BigDecimal) row[1])
                .build())
            .collect(Collectors.toList());

        // Costs by model
        List<Object[]> modelData = callRepository.calculateCostByModel(projectKey, from, to);
        List<ModelCostDTO> costsByModel = modelData.stream()
            .map(row -> {
                BigDecimal cost = (BigDecimal) row[1];
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
            .build();
    }

    /**
     * Get overview KPIs
     */
    @Transactional(readOnly = true)
    public OverviewKpiDTO getOverviewKpis(String projectKey, Instant from, Instant to) {
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

        // Latency
        Double p95Latency = callRepository.calculateP95Latency(projectKey, from, to);

        // Recent flagged calls
        Pageable flaggedPageable = PageRequest.of(0, 10);
        Page<AcCall> flaggedCalls = callRepository.findFlaggedCalls(projectKey, flaggedPageable);
        List<CallListDTO> recentFlagged = flaggedCalls.stream()
            .map(this::toCallListDTO)
            .collect(Collectors.toList());

        // Cost over time
        List<Object[]> dailyData = callRepository.calculateDailyCost(projectKey, from, to);
        List<DailyCostDTO> costOverTime = dailyData.stream()
            .map(row -> DailyCostDTO.builder()
                .date(((java.sql.Date) row[0]).toLocalDate())
                .cost((BigDecimal) row[1])
                .build())
            .collect(Collectors.toList());

        return OverviewKpiDTO.builder()
            .reliabilityScore(reliabilityScore)
            .totalCalls(totalCalls)
            .errorCalls(errorCalls)
            .errorRate(errorRate)
            .flaggedCallsCount(flaggedCount)
            .flaggedPercentage(flaggedPercentage)
            .totalCost(totalCost)
            .p95LatencyMs(p95Latency)
            .recentFlaggedCalls(recentFlagged)
            .costOverTime(costOverTime)
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

    // DTO Conversion Methods

    private CallListDTO toCallListDTO(AcCall call) {
        Long flagCount = flagRepository.countByCallId(call.getId());

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
            .isFlagged(flagCount > 0)
            .flagCount(flagCount)
            .build();
    }

    private CallDetailDTO toCallDetailDTO(AcCall call) {
        List<AcCallFlag> flags = flagRepository.findByCallIdOrderByCreatedAtDesc(call.getId());

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
    }
}
