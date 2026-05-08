param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AppId = "clarity",
    [string]$EnvName = "prod",
    [string]$Route = "/v1/chat/completions",
    [string]$Provider = "openai",
    [string]$Range = "7d",
    [string]$BaselineModel = "gpt-4o-mini",
    [string]$BaselinePrompt = "v12",
    [string]$CandidateModel = "gpt-4.1-mini",
    [string]$CandidatePrompt = "v13",
    [decimal]$BudgetDailyLimit = 0.050000,
    [decimal]$BudgetMonthlyLimit = 2.000000,
    [decimal]$BudgetForecastLimit = 2.500000
)

$ErrorActionPreference = "Stop"

$ApiBase = "$($BaseUrl.TrimEnd('/'))/api/ailuros"

function Step([string]$Message) {
    Write-Host ""
    Write-Host "==== $Message ====" -ForegroundColor Cyan
}

function To-JsonNumber([decimal]$Value) {
    return [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:0.000000}", $Value)
}

function Invoke-CurlJson {
    param(
        [ValidateSet("GET","POST")] [string]$Method,
        [Parameter(Mandatory=$true)] [string]$Path,
        [string]$Body
    )

    $url = "$ApiBase$Path"
    if ($Body) {
        $tmp = New-TemporaryFile
        try {
            Set-Content -Path $tmp -Value $Body -Encoding UTF8 -NoNewline
            return (curl.exe -sS -f -X $Method $url -H "Content-Type: application/json" --data-binary "@$tmp")
        } finally {
            Remove-Item $tmp -ErrorAction SilentlyContinue
        }
    }
    return (curl.exe -sS -f -X $Method $url)
}

function Assert-Contains {
    param(
        [string]$Text,
        [string]$Expected,
        [string]$Message
    )
    if (-not $Text.Contains($Expected)) {
        throw "Assertion failed: $Message"
    }
}

function Ingest-Call {
    param(
        [string]$Label,
        [string]$Model,
        [string]$PromptVersion,
        [string]$Status,
        [int]$HttpStatus,
        [int]$LatencyMs,
        [decimal]$CostUsd,
        [string]$ErrorType = $null
    )

    $traceId = "smoke-$Label-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())-$([guid]::NewGuid().ToString('N').Substring(0,8))"
    $spanId = "span-$Label-$([guid]::NewGuid().ToString('N').Substring(0,12))"
    if (-not $script:SmokeTsOffsetMs) {
        $script:SmokeTsOffsetMs = 0
    }
    $ts = (Get-Date).ToUniversalTime().AddMilliseconds($script:SmokeTsOffsetMs).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $script:SmokeTsOffsetMs += 75
    $costText = To-JsonNumber $CostUsd
    $errorJson = if ([string]::IsNullOrWhiteSpace($ErrorType)) { "null" } else { '"' + $ErrorType + '"' }

    $payload = @"
{
  "event_version": "call_event.v1",
  "identity": {
    "trace_id": "$traceId",
    "span_id": "$spanId"
  },
  "dims": {
    "app_id": "$AppId",
    "env": "$EnvName",
    "route": "$Route",
    "provider": "$Provider",
    "model": "$Model",
    "prompt_version": "$PromptVersion",
    "streaming": false,
    "user_tier": "enterprise"
  },
  "timing": {
    "request_ts": "$ts",
    "response_ts": "$ts",
    "latency_ms": $LatencyMs
  },
  "usage": {
    "input_tokens": 400,
    "output_tokens": 120,
    "cost_usd": $costText
  },
  "outcome": {
    "status": "$Status",
    "error_type": $errorJson,
    "http_status": $HttpStatus
  },
  "privacy": {
    "prompt_hash": "hash-$PromptVersion"
  },
  "flags": [],
  "metadata": {
    "source": "smoke-script-ps1",
    "release_candidate": true
  }
}
"@

    [void](Invoke-CurlJson -Method POST -Path "/ingest" -Body $payload)
}

Step "Health Check"
$health = Invoke-CurlJson -Method GET -Path "/health"
Write-Host $health

Step "Seed Baseline + Candidate Calls via /ingest"
Ingest-Call -Label "base-1" -Model $BaselineModel -PromptVersion $BaselinePrompt -Status "ok" -HttpStatus 200 -LatencyMs 190 -CostUsd 0.002100
Ingest-Call -Label "base-2" -Model $BaselineModel -PromptVersion $BaselinePrompt -Status "ok" -HttpStatus 200 -LatencyMs 210 -CostUsd 0.002000
Ingest-Call -Label "base-3" -Model $BaselineModel -PromptVersion $BaselinePrompt -Status "ok" -HttpStatus 200 -LatencyMs 220 -CostUsd 0.002200
Ingest-Call -Label "cand-1" -Model $CandidateModel -PromptVersion $CandidatePrompt -Status "error" -HttpStatus 500 -LatencyMs 1800 -CostUsd 0.040000 -ErrorType "provider_error"
Ingest-Call -Label "cand-2" -Model $CandidateModel -PromptVersion $CandidatePrompt -Status "ok" -HttpStatus 200 -LatencyMs 1250 -CostUsd 0.035000
Ingest-Call -Label "cand-3" -Model $CandidateModel -PromptVersion $CandidatePrompt -Status "ok" -HttpStatus 200 -LatencyMs 1180 -CostUsd 0.036000
Write-Host "Seeded 6 call events."

Step "Create/Upsert Budget Policy"
$policyPayload = @"
{
  "appId": "$AppId",
  "env": "$EnvName",
  "route": "$Route",
  "dailyUsdLimit": $(To-JsonNumber $BudgetDailyLimit),
  "monthlyUsdLimit": $(To-JsonNumber $BudgetMonthlyLimit),
  "forecastMonthlyUsdLimit": $(To-JsonNumber $BudgetForecastLimit),
  "enabled": true
}
"@
$policyResp = Invoke-CurlJson -Method POST -Path "/budget/policies" -Body $policyPayload
Write-Host $policyResp

$routeEncoded = [Uri]::EscapeDataString($Route)

Step "Manual Budget Evaluation"
$budgetEvalResp = Invoke-CurlJson -Method POST -Path "/budget/evaluate?app_id=$AppId&env=$EnvName&route=$routeEncoded"
Write-Host $budgetEvalResp
Assert-Contains -Text $budgetEvalResp -Expected '"EXCEEDED"' -Message "expected budget EXCEEDED status"

Step "Read Budget Status"
$budgetStatusResp = Invoke-CurlJson -Method GET -Path "/budget/status?app_id=$AppId&env=$EnvName&range=$Range&tz=UTC"
Write-Host $budgetStatusResp

Step "Set Release Baseline"
$baselinePayload = @"
{
  "appId": "$AppId",
  "env": "$EnvName",
  "route": "$Route",
  "baselineModel": "$BaselineModel",
  "baselinePromptVersion": "$BaselinePrompt",
  "enabled": true
}
"@
$baselineResp = Invoke-CurlJson -Method POST -Path "/release/baseline" -Body $baselinePayload
Write-Host $baselineResp

Step "Read Release Gate Status"
$releaseStatusResp = Invoke-CurlJson -Method GET -Path "/release/baseline?app_id=$AppId&env=$EnvName&route=$routeEncoded"
Write-Host $releaseStatusResp
Assert-Contains -Text $releaseStatusResp -Expected '"changed":true' -Message "expected changed=true after candidate drift"

Step "Run Offline Regression"
$regressionPayload = @"
{
  "appId": "$AppId",
  "env": "$EnvName",
  "route": "$Route",
  "baselineModel": "$BaselineModel",
  "baselinePromptVersion": "$BaselinePrompt",
  "candidateModel": "$CandidateModel",
  "candidatePromptVersion": "$CandidatePrompt"
}
"@
$regressionResp = Invoke-CurlJson -Method POST -Path "/regression/run" -Body $regressionPayload
Write-Host $regressionResp
$regressionObj = $regressionResp | ConvertFrom-Json
$runId = $regressionObj.id
if ([string]::IsNullOrWhiteSpace($runId)) {
    throw "Failed to extract regression run id from response"
}

Step "Read Regression Run + Report"
$runResp = Invoke-CurlJson -Method GET -Path "/regression/runs/$runId"
Write-Host $runResp
Assert-Contains -Text $runResp -Expected '"releaseBlocked":true' -Message "expected releaseBlocked=true for failing candidate"
$reportResp = Invoke-CurlJson -Method GET -Path "/regression/report/$runId"
Write-Host $reportResp

Step "Read Incidents"
$incidentsResp = Invoke-CurlJson -Method GET -Path "/incidents?app_id=$AppId&env=$EnvName&range=$Range&limit=100"
Write-Host $incidentsResp
Assert-Contains -Text $incidentsResp -Expected 'BUDGET_EXCEEDED' -Message "expected BUDGET_EXCEEDED incident"
Assert-Contains -Text $incidentsResp -Expected 'RELEASE_BLOCKED' -Message "expected RELEASE_BLOCKED incident"

Step "Smoke Summary"
Write-Host "Smoke passed."
Write-Host "Regression Run ID: $runId"
Write-Host "Dashboard URL: $($BaseUrl.TrimEnd('/'))/ailuros-dashboard.html?app_id=$AppId&env=$EnvName&range=$Range&tz=UTC"
