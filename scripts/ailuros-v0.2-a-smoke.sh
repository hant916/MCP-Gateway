#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_BASE="${BASE_URL%/}/api/ailuros"

APP_ID="${APP_ID:-clarity}"
ENV_NAME="${ENV_NAME:-prod}"
ROUTE="${ROUTE:-/v1/chat/completions}"
PROVIDER="${PROVIDER:-openai}"

BASELINE_MODEL="${BASELINE_MODEL:-gpt-4o-mini}"
BASELINE_PROMPT="${BASELINE_PROMPT:-v12}"
CANDIDATE_MODEL="${CANDIDATE_MODEL:-gpt-4.1-mini}"
CANDIDATE_PROMPT="${CANDIDATE_PROMPT:-v13}"

BUDGET_DAILY_LIMIT="${BUDGET_DAILY_LIMIT:-0.050000}"
BUDGET_MONTHLY_LIMIT="${BUDGET_MONTHLY_LIMIT:-2.000000}"
BUDGET_FORECAST_LIMIT="${BUDGET_FORECAST_LIMIT:-2.500000}"

RANGE="${RANGE:-7d}"

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

pretty_print() {
  local payload="$1"
  if command -v jq >/dev/null 2>&1; then
    echo "$payload" | jq .
  else
    echo "$payload"
  fi
}

step() {
  echo
  echo "==== $1 ===="
}

api_get() {
  local path="$1"
  curl -sS -f "${API_BASE}${path}"
}

api_post() {
  local path="$1"
  local data="$2"
  curl -sS -f -X POST "${API_BASE}${path}" \
    -H "Content-Type: application/json" \
    -d "$data"
}

extract_uuid() {
  local payload="$1"
  echo "$payload" | grep -oE '"id":"[0-9a-f-]+"' | head -n 1 | cut -d '"' -f 4
}

ingest_call() {
  local label="$1"
  local model="$2"
  local prompt_version="$3"
  local status="$4"
  local http_status="$5"
  local latency_ms="$6"
  local cost_usd="$7"
  local error_type="${8:-}"

  local trace_id="smoke-${label}-$(date +%s%N)"
  local span_id="span-${label}-$(date +%s%N)"
  local request_ts
  request_ts="$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")"
  local error_json="null"
  if [[ -n "$error_type" ]]; then
    error_json="\"${error_type}\""
  fi

  local payload
  payload="$(cat <<EOF
{
  "event_version": "call_event.v1",
  "identity": {
    "trace_id": "${trace_id}",
    "span_id": "${span_id}"
  },
  "dims": {
    "app_id": "${APP_ID}",
    "env": "${ENV_NAME}",
    "route": "${ROUTE}",
    "provider": "${PROVIDER}",
    "model": "${model}",
    "prompt_version": "${prompt_version}",
    "streaming": false,
    "user_tier": "enterprise"
  },
  "timing": {
    "request_ts": "${request_ts}",
    "response_ts": "${request_ts}",
    "latency_ms": ${latency_ms}
  },
  "usage": {
    "input_tokens": 400,
    "output_tokens": 120,
    "cost_usd": ${cost_usd}
  },
  "outcome": {
    "status": "${status}",
    "error_type": ${error_json},
    "http_status": ${http_status}
  },
  "privacy": {
    "prompt_hash": "hash-${prompt_version}"
  },
  "flags": [],
  "metadata": {
    "source": "smoke-script",
    "release_candidate": true
  }
}
EOF
)"

  api_post "/ingest" "$payload" >/dev/null
  sleep 0.05
}

assert_contains() {
  local payload="$1"
  local expected="$2"
  local message="$3"
  if [[ "$payload" != *"$expected"* ]]; then
    echo "Assertion failed: ${message}" >&2
    exit 1
  fi
}

require_command curl

step "Health Check"
health="$(api_get "/health")"
echo "$health"

step "Seed Baseline + Candidate Calls via /ingest"
ingest_call "base-1" "$BASELINE_MODEL" "$BASELINE_PROMPT" "ok" "200" "190" "0.002100"
ingest_call "base-2" "$BASELINE_MODEL" "$BASELINE_PROMPT" "ok" "200" "210" "0.002000"
ingest_call "base-3" "$BASELINE_MODEL" "$BASELINE_PROMPT" "ok" "200" "220" "0.002200"
ingest_call "cand-1" "$CANDIDATE_MODEL" "$CANDIDATE_PROMPT" "error" "500" "1800" "0.040000" "provider_error"
ingest_call "cand-2" "$CANDIDATE_MODEL" "$CANDIDATE_PROMPT" "ok" "200" "1250" "0.035000"
ingest_call "cand-3" "$CANDIDATE_MODEL" "$CANDIDATE_PROMPT" "ok" "200" "1180" "0.036000"
echo "Seeded 6 call events for app=${APP_ID}, env=${ENV_NAME}, route=${ROUTE}"

step "Create/Upsert Budget Policy"
policy_payload="$(cat <<EOF
{
  "appId": "${APP_ID}",
  "env": "${ENV_NAME}",
  "route": "${ROUTE}",
  "dailyUsdLimit": ${BUDGET_DAILY_LIMIT},
  "monthlyUsdLimit": ${BUDGET_MONTHLY_LIMIT},
  "forecastMonthlyUsdLimit": ${BUDGET_FORECAST_LIMIT},
  "enabled": true
}
EOF
)"
policy_resp="$(api_post "/budget/policies" "$policy_payload")"
pretty_print "$policy_resp"

step "Manual Budget Evaluation"
budget_eval_resp="$(curl -sS -f -X POST "${API_BASE}/budget/evaluate?app_id=${APP_ID}&env=${ENV_NAME}&route=${ROUTE}")"
pretty_print "$budget_eval_resp"
assert_contains "$budget_eval_resp" "\"EXCEEDED\"" "expected budget EXCEEDED status"

step "Read Budget Status"
budget_status_resp="$(api_get "/budget/status?app_id=${APP_ID}&env=${ENV_NAME}&range=${RANGE}")"
pretty_print "$budget_status_resp"

step "Set Release Baseline"
baseline_payload="$(cat <<EOF
{
  "appId": "${APP_ID}",
  "env": "${ENV_NAME}",
  "route": "${ROUTE}",
  "baselineModel": "${BASELINE_MODEL}",
  "baselinePromptVersion": "${BASELINE_PROMPT}",
  "enabled": true
}
EOF
)"
baseline_resp="$(api_post "/release/baseline" "$baseline_payload")"
pretty_print "$baseline_resp"

step "Read Release Gate Status"
release_status_resp="$(api_get "/release/baseline?app_id=${APP_ID}&env=${ENV_NAME}&route=${ROUTE}")"
pretty_print "$release_status_resp"
assert_contains "$release_status_resp" "\"changed\":true" "expected changed=true after candidate drift"

step "Run Offline Regression"
regression_payload="$(cat <<EOF
{
  "appId": "${APP_ID}",
  "env": "${ENV_NAME}",
  "route": "${ROUTE}",
  "baselineModel": "${BASELINE_MODEL}",
  "baselinePromptVersion": "${BASELINE_PROMPT}",
  "candidateModel": "${CANDIDATE_MODEL}",
  "candidatePromptVersion": "${CANDIDATE_PROMPT}"
}
EOF
)"
regression_resp="$(api_post "/regression/run" "$regression_payload")"
pretty_print "$regression_resp"
run_id="$(extract_uuid "$regression_resp")"
if [[ -z "${run_id}" ]]; then
  echo "Failed to extract regression run id from response" >&2
  exit 1
fi

step "Read Regression Run + Report"
run_resp="$(api_get "/regression/runs/${run_id}")"
pretty_print "$run_resp"
report_resp="$(api_get "/regression/report/${run_id}")"
pretty_print "$report_resp"
assert_contains "$run_resp" "\"releaseBlocked\":true" "expected releaseBlocked=true for failing candidate"

step "Read Incidents (must include governance signals)"
incidents_resp="$(api_get "/incidents?app_id=${APP_ID}&env=${ENV_NAME}&range=${RANGE}&limit=100")"
pretty_print "$incidents_resp"
assert_contains "$incidents_resp" "BUDGET_EXCEEDED" "expected budget incident in incidents list"
assert_contains "$incidents_resp" "RELEASE_BLOCKED" "expected release gate incident in incidents list"

step "Smoke Summary"
echo "Smoke passed."
echo "Base URL: ${BASE_URL}"
echo "App/Env/Route: ${APP_ID} / ${ENV_NAME} / ${ROUTE}"
echo "Regression Run ID: ${run_id}"
echo "Dashboard URL: ${BASE_URL%/}/ailuros-dashboard.html?app_id=${APP_ID}&env=${ENV_NAME}&range=${RANGE}&tz=UTC"
