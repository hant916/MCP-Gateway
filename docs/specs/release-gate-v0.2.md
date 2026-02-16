# Release Gate v0.2

## Endpoints
- `GET /api/ailuros/release/baseline?app_id={app}&env={env}&route={route}`
- `POST /api/ailuros/release/baseline`
- `POST /api/ailuros/regression/run`
- `GET /api/ailuros/regression/runs?app_id={app}&env={env}&route={route}&range=30d`
- `GET /api/ailuros/regression/runs/{id}`
- `GET /api/ailuros/regression/report/{id}`

## Flow
1. Baseline (`model + prompt_version`) is stored per `app/env/route`.
2. Candidate is detected from recent calls.
3. If candidate differs from baseline, a `PENDING` regression run is queued.
4. Manual `POST /regression/run` executes offline rule-based regression.
5. Failed regression sets `release_blocked=true`.

## Regression Output
- statuses: `PENDING | RUNNING | PASS | FAIL | ERROR`
- persisted in `ac_regression_run`
- summary stored as JSON (`summary_json`)
- report endpoint returns JSON + HTML payload

## Gate Logic (v0.2)
- compares candidate vs baseline for:
  - error rate
  - p95 latency
  - average cost
- fail => `regression_failed` + `release_blocked` webhook events

## Config
- `ailuros.regression.enabled`
- `ailuros.regression.detect-cron`
- `ailuros.regression.max-cases`
- `ailuros.regression.judge-enabled`
- `ailuros.regression.timeout-ms`
- `ailuros.regression.detector-window-hours`
