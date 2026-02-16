# Implementation Pack
## Ailuros Control - Drill-down + Compare + Investor-ready KPI

Version: 1.0 (Frozen)
Date: 2026-02-13
Owner: MCP-Gateway / Ailuros Control
Execution Mode: Commit-by-commit, no architecture rewrite

## 1. Goal (Execution Target)
Ship an "LLM Incident Investigation Workbench" that supports a complete investigation flow:
KPI -> flagged list -> call detail -> compare -> explain impact.

## 2. Scope Freeze
In scope:
- Flagged drill-down list and investigation actions
- Structured call detail view
- Compare view (meta diff + impact summary + prompt diff)
- KPI governance language (definition + interpretation)
- Global time window (24h/7d/30d/custom)
- Demo data distribution tuning (realistic, no fabricated metrics)

Out of scope:
- Auth/permission redesign
- Multi-tenant architecture
- Production-grade pagination/search at scale
- Alert push channels (email/slack/webhook workflows)
- Realtime streaming UI

## 3. Hard Constraints
- No DB schema change in this iteration
- No repository layer redesign; only minimal query extensions
- Keep backward compatibility for existing API consumers
- Prefer additive API changes over breaking changes

## 4. Current Baseline (Confirmed)
Already present:
- DTOs: CallListDTO, CallDetailDTO, CompareDTO, OverviewKpiDTO, DailyCostDTO
- Backend: AilurosControlController, AilurosControlService, ComparisonService
- Frontend: src/main/resources/static/ailuros-dashboard.html

Recently completed baseline fixes:
- Real daily aggregation for cost/error/call volume
- Unified 30-day default window
- Flagged drill-down table + call detail + compare panel in dashboard

## 5. Execution Plan (Commit Blueprint)

### Commit 1 - API compatibility and window contract
Objective:
- Freeze query contract for all dashboard data endpoints

Changes:
- Add optional aliases for compatibility:
  - GET /api/ailuros/calls accepts flagged and flaggedOnly
  - GET /api/ailuros/compare accepts both:
    - a/b (existing)
    - left/right (new alias for product language)
- Ensure from/to optional on all dashboard endpoints and default consistently

Files:
- src/main/java/com/mcpgateway/controller/ailuros/AilurosControlController.java

DoD:
- All of these return 200 with equivalent payload:
  - /api/ailuros/calls?flagged=true
  - /api/ailuros/calls?flaggedOnly=true
  - /api/ailuros/compare?a={id1}&b={id2}
  - /api/ailuros/compare?left={id1}&right={id2}

### Commit 2 - Flagged investigation workflow
Objective:
- Make flagged KPI actionable, not just numeric

Changes:
- KPI click opens flagged list view
- Flagged list fields:
  - callId, timestamp, model, latencyMs, cost, status, flagCount
- Row action:
  - Inspect -> load call detail
  - Set A / Set B -> prepare compare

Files:
- src/main/resources/static/ailuros-dashboard.html

DoD:
- User can go KPI -> list -> inspect without page reload errors
- Data source is backend API only (no random/mock data)

### Commit 3 - Structured Call Detail view
Objective:
- Convert raw detail dump into investigation structure

Changes:
- Sections:
  1. Meta (model, promptRef/version, timestamp, trace id)
  2. Metrics (latency, cost, tokens, status, flags)
  3. Governance (flag reasons, policy markers, quality signal if available)
  4. Payload (request/response, collapsible)
- Add "Compare with baseline" action

Files:
- src/main/resources/static/ailuros-dashboard.html

DoD:
- Any call from flagged list opens detail in <500ms after API response
- Detail has explicit governance section, not plain JSON dump

### Commit 4 - Compare productization
Objective:
- Make compare the core differentiator

Changes:
- Compare view blocks:
  - Meta Diff table (model/prompt/config)
  - Impact Summary table (latency/cost/errors/tokens delta)
  - Prompt/response diff (line-level, MVP)
- Keep using CompareDTO, enrich mapping if missing keys

Files:
- src/main/resources/static/ailuros-dashboard.html
- src/main/java/com/mcpgateway/service/ailuros/ComparisonService.java (only if summary keys missing)

DoD:
- Two-call compare can be demoed end-to-end with measurable deltas
- Diff view clearly shows what changed and operational impact

### Commit 5 - Investor-grade KPI semantics
Objective:
- Shift from infra metrics to governance narrative

Changes:
- Add per-KPI text under cards:
  - Definition
  - Interpretation / risk threshold
- KPI set:
  - Reliability
  - p95 latency
  - Flagged rate/count
  - Cost

Files:
- src/main/resources/static/ailuros-dashboard.html

DoD:
- Each KPI card has governance-readable meaning without verbal explanation

### Commit 6 - Demo data distribution tuning
Objective:
- Keep realistic tension without looking broken by default

Target distribution:
- Reliability: 94% - 98%
- Flag rate: 2% - 6%
- p95: 2000ms - 4000ms
- Keep occasional spikes for storytelling

Changes:
- Tune generator probabilities and latency buckets only
- No hardcoded fake KPI overrides

Files:
- src/main/java/com/mcpgateway/demo/AilurosDataGenerator.java

DoD:
- Regenerated 30-day data fits target ranges on fresh DB
- Trend still shows at least one incident spike and recovery narrative

## 6. Decision Gates

Gate 1 - Drill-down integrity
Pass conditions:
- KPI -> flagged list -> detail -> compare works
- All values sourced from APIs, no synthetic random charts

Gate 2 - Compare value clarity
Pass conditions:
- Meta diff and impact summary readable in one screen
- Prompt/response diff usable for root-cause discussion

Gate 3 - Investor narrative
Pass conditions:
- Dashboard demonstrates "investigation workflow layer" not just metrics wall
- Reliability/p95/flag/cost are explainable with risk language

## 7. Test Plan

Build-level:
- mvn -q -DskipTests test-compile

API smoke:
- GET /api/ailuros/overview?projectKey=default&from=...&to=...
- GET /api/ailuros/cost/summary?projectKey=default&from=...&to=...
- GET /api/ailuros/calls?projectKey=default&flagged=true&from=...&to=...
- GET /api/ailuros/calls/{id}
- GET /api/ailuros/compare?left={id1}&right={id2}

UI acceptance:
- Window switch updates all cards/charts/list consistently
- Flagged list row click loads matching detail
- Compare A/B selection loads compare block with correct IDs

Data sanity checks:
- Sum(daily cost) equals cost summary total for same window
- Daily call volume equals sum(callCount) in selected window
- p95 computed from successful calls only

## 8. Rollback Plan
Per commit rollback strategy:
- Keep commits small and isolated by capability
- Revert latest commit if gate fails
- Never couple API contract changes and UI redesign in one commit

## 9. Final Release Checklist
- [ ] Gate 1 passed
- [ ] Gate 2 passed
- [ ] Gate 3 passed
- [ ] Demo data regenerated and validated
- [ ] README/demo guide updated with route flow and investigation script
- [ ] One-click demo script command verified

## 10. Positioning Statement (Post-Release)
"Ailuros Control is an investigation workflow layer for AI systems: it traces incidents from KPI anomalies to call-level evidence and controlled A/B comparison."
