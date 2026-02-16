package com.mcpgateway.controller.ailuros;

import com.mcpgateway.demo.AilurosDataGenerator;
import com.mcpgateway.dto.ailuros.*;
import com.mcpgateway.service.ailuros.AilurosControlService;
import com.mcpgateway.service.ailuros.ComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Ailuros Control REST API
 *
 * Provides endpoints for:
 * - Call querying and filtering
 * - Flag management
 * - Cost analysis
 * - Call comparison
 * - Overview KPIs
 * - Demo data generation (development only)
 *
 * Base path: /api/ailuros
 */
@Slf4j
@RestController
@RequestMapping("/api/ailuros")
@RequiredArgsConstructor
@Tag(name = "Ailuros Control", description = "LLM Call Observability & Governance")
@SecurityRequirement(name = "bearerAuth")
public class AilurosControlController {

    private final AilurosControlService controlService;
    private final ComparisonService comparisonService;
    private final AilurosDataGenerator dataGenerator;

    /**
     * Generate demo data
     *
     * POST /api/ailuros/demo/generate
     */
    @PostMapping("/demo/generate")
    @Operation(summary = "Generate demo data",
               description = "Generates 1,500+ realistic LLM calls with dramatic scenarios (DEMO ONLY)")
    public ResponseEntity<String> generateDemoData() {
        try {
            log.info("🎬 Demo data generation requested via API");
            dataGenerator.run("--demo.generate=true");
            return ResponseEntity.ok("✅ Demo data generated successfully! Open http://localhost:8080/ailuros-dashboard.html");
        } catch (Exception e) {
            log.error("Failed to generate demo data", e);
            return ResponseEntity.status(500).body("❌ Failed to generate demo data: " + e.getMessage());
        }
    }

    /**
     * Get calls with filtering and pagination
     *
     * GET /api/ailuros/calls
     *   ?projectKey=default
     *   &env=prod
     *   &from=2024-01-01T00:00:00Z
     *   &to=2024-12-31T23:59:59Z
     *   &model=gpt-4
     *   &provider=openai
     *   &status=ok
     *   &page=0
     *   &size=20
     */
    @GetMapping("/calls")
    @Operation(summary = "Get calls with filtering",
               description = "Query LLM calls with advanced filtering and pagination")
    public ResponseEntity<Page<CallListDTO>> getCalls(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String tz,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String promptRef,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean flagged,
            @RequestParam(required = false) Boolean flaggedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Build filter
        AilurosControlService.CallFilterRequest filter = new AilurosControlService.CallFilterRequest();
        filter.setProjectKey(projectKey != null ? projectKey : "default");
        filter.setEnv(env);
        filter.setFrom(from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS));
        filter.setTo(to != null ? to : Instant.now());
        filter.setModel(model);
        filter.setProvider(provider);
        filter.setPromptRef(promptRef);
        filter.setStatus(status);
        filter.setFlaggedOnly(Boolean.TRUE.equals(flaggedOnly) || Boolean.TRUE.equals(flagged));
        filter.setTimezone(tz);

        // Query
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CallListDTO> calls = controlService.getCalls(filter, pageable);

        return ResponseEntity.ok(calls);
    }

    /**
     * Get call detail by ID
     *
     * GET /api/ailuros/calls/{id}
     */
    @GetMapping("/calls/{id}")
    @Operation(summary = "Get call detail",
               description = "Get full details of a specific call including request/response text")
    public ResponseEntity<CallDetailDTO> getCallDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String tz) {
        CallDetailDTO call = controlService.getCallDetail(id, from, to, tz);
        return ResponseEntity.ok(call);
    }

    /**
     * Get call detail by trace ID
     *
     * GET /api/ailuros/calls/trace/{traceId}
     */
    @GetMapping("/calls/trace/{traceId}")
    @Operation(summary = "Get call by trace ID",
               description = "Get call details using the trace ID from the request header")
    public ResponseEntity<CallDetailDTO> getCallByTraceId(
            @PathVariable String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String tz) {
        CallDetailDTO call = controlService.getCallByTraceId(traceId, from, to, tz);
        return ResponseEntity.ok(call);
    }

    /**
     * Create a flag for a call
     *
     * POST /api/ailuros/calls/{id}/flag
     * {
     *   "flagType": "wrong",
     *   "note": "Hallucination detected",
     *   "createdBy": "john@example.com"
     * }
     */
    @PostMapping("/calls/{id}/flag")
    @Operation(summary = "Flag a call",
               description = "Mark a call for review (wrong output, risky content, etc.)")
    public ResponseEntity<FlagDTO> createFlag(
            @PathVariable UUID id,
            @Valid @RequestBody CreateFlagRequest request) {

        FlagDTO flag = controlService.createFlag(id, request);
        return ResponseEntity.ok(flag);
    }

    /**
     * Replay a call (placeholder for v0.1)
     *
     * POST /api/ailuros/calls/{id}/replay
     * {
     *   "model": "gpt-4",
     *   "temperature": 0.2
     * }
     */
    @PostMapping("/calls/{id}/replay")
    @Operation(summary = "Replay a call",
               description = "Re-execute a call with optional parameter overrides (v0.1: returns 501)")
    public ResponseEntity<String> replayCall(
            @PathVariable UUID id,
            @RequestBody(required = false) ReplayRequest request) {

        // v0.1: Not implemented
        // v0.2: Will integrate with actual LLM execution layer
        log.info("Replay requested for call: {}, overrides: {}", id, request);

        return ResponseEntity.status(501).body(
            "Replay functionality will be available in v0.2. " +
            "For now, use the dashboard to view the original request and manually re-execute."
        );
    }

    /**
     * Compare two calls
     *
     * GET /api/ailuros/compare?a={callIdA}&b={callIdB}
     */
    @GetMapping("/compare")
    @Operation(summary = "Compare two calls",
               description = "Generate diff and comparison summary for two calls")
    public ResponseEntity<CompareDTO> compareCalls(
            @RequestParam(required = false) UUID a,
            @RequestParam(required = false) UUID b,
            @RequestParam(required = false) UUID left,
            @RequestParam(required = false) UUID right) {

        UUID callA = a != null ? a : left;
        UUID callB = b != null ? b : right;
        if (callA == null || callB == null) {
            return ResponseEntity.badRequest().build();
        }

        CompareDTO comparison = comparisonService.compare(callA, callB);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Get cost summary
     *
     * GET /api/ailuros/cost/summary
     *   ?projectKey=default
     *   &from=2024-01-01T00:00:00Z
     *   &to=2024-12-31T23:59:59Z
     */
    @GetMapping("/cost/summary")
    @Operation(summary = "Get cost summary",
               description = "Get cost analysis with breakdown by model and time")
    public ResponseEntity<CostSummaryDTO> getCostSummary(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String tz) {

        String project = projectKey != null ? projectKey : "default";
        Instant fromDate = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toDate = to != null ? to : Instant.now();

        CostSummaryDTO summary = controlService.getCostSummary(project, fromDate, toDate, tz);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get overview KPIs
     *
     * GET /api/ailuros/overview
     *   ?projectKey=default
     *   &from=2024-01-01T00:00:00Z
     *   &to=2024-12-31T23:59:59Z
     */
    @GetMapping("/overview")
    @Operation(summary = "Get overview KPIs",
               description = "Get dashboard KPIs: reliability, cost, flagged calls, etc.")
    public ResponseEntity<OverviewKpiDTO> getOverview(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String tz) {

        String project = projectKey != null ? projectKey : "default";
        Instant fromDate = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toDate = to != null ? to : Instant.now();

        OverviewKpiDTO kpis = controlService.getOverviewKpis(project, fromDate, toDate, tz);
        return ResponseEntity.ok(kpis);
    }

    /**
     * Health check
     *
     * GET /api/ailuros/health
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if Ailuros Control is operational")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Ailuros Control v0.1 operational");
    }
}
