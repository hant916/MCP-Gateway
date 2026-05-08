param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AppId = "clarity",
    [string]$EnvName = "prod",
    [string]$Route = "/v1/chat/completions",
    [string]$Range = "7d",
    [switch]$SkipSmoke,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

function Step([string]$Message) {
    Write-Host ""
    Write-Host "==== $Message ====" -ForegroundColor Cyan
}

function Ensure-Backend {
    Step "Backend Health Check"
    $healthUrl = "$BaseUrl/api/ailuros/health"
    $health = Invoke-RestMethod -Method GET -Uri $healthUrl
    Write-Host "Health: $health"
}

function Run-Smoke {
    Step "Run v0.2-A API Smoke Script"
    $psSmoke = Join-Path $PSScriptRoot "ailuros-v0.2-a-smoke.ps1"
    if (Test-Path $psSmoke) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $psSmoke -BaseUrl $BaseUrl -AppId $AppId -EnvName $EnvName -Route $Route -Range $Range
        if ($LASTEXITCODE -ne 0) {
            throw "PowerShell smoke script failed with exit code $LASTEXITCODE"
        }
        return
    }

    $shSmoke = Join-Path $PSScriptRoot "ailuros-v0.2-a-smoke.sh"
    if (-not (Test-Path $shSmoke)) {
        throw "Smoke script not found: $psSmoke or $shSmoke"
    }

    if (Get-Command bash -ErrorAction SilentlyContinue) {
        & bash $shSmoke
        if ($LASTEXITCODE -ne 0) {
            throw "Smoke script failed with exit code $LASTEXITCODE"
        }
        return
    }

    if (Get-Command wsl -ErrorAction SilentlyContinue) {
        $wslPath = (wsl wslpath -a $shSmoke).Trim()
        & wsl bash $wslPath
        if ($LASTEXITCODE -ne 0) {
            throw "Smoke script failed in WSL with exit code $LASTEXITCODE"
        }
        return
    }

    throw "Neither bash nor wsl is available. Install Git Bash or WSL, or run scripts/ailuros-v0.2-a-smoke.sh manually."
}

function Show-LiveSnapshot {
    Step "Fetch Live Snapshot"

    $routeEncoded = [Uri]::EscapeDataString($Route)
    $stats = Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/ailuros/stats?app_id=$AppId&env=$EnvName&range=$Range&tz=UTC"
    $budget = Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/ailuros/budget/status?app_id=$AppId&env=$EnvName&range=$Range&tz=UTC"
    $release = Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/ailuros/release/baseline?app_id=$AppId&env=$EnvName&route=$routeEncoded"
    $runs = Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/ailuros/regression/runs?app_id=$AppId&env=$EnvName&route=$routeEncoded&range=30d&limit=5&tz=UTC"

    Write-Host ("Reliability: {0}%  |  Flagged: {1}  |  Cost: `${2}  |  p95: {3}ms" -f $stats.reliability, $stats.flaggedCount, $stats.totalCost, $stats.p95LatencyMs)
    Write-Host ("Budget MTD: `${0}  |  Forecast: `${1}  |  Exceeded Events: {2}" -f $budget.monthToDateCostUsd, $budget.forecastMonthlyUsd, $budget.exceededCount)
    Write-Host ("Release Changed: {0}  |  Blocked: {1}  |  Latest Run: {2}" -f $release.changed, $release.releaseBlocked, $release.latestRunStatus)

    if ($runs -is [System.Array] -and $runs.Count -gt 0) {
        $last = $runs[0]
        Write-Host ("Latest Regression Run: {0}  status={1}  blocked={2}" -f $last.id, $last.status, $last.releaseBlocked)
    }
}

function Open-Dashboard {
    Step "Open Dashboard"
    $url = "$BaseUrl/ailuros-dashboard.html?app_id=$AppId&env=$EnvName&range=$Range&tz=UTC"
    Write-Host "Dashboard URL: $url"
    if (-not $NoBrowser) {
        Start-Process $url
    }
}

function Show-DemoChecklist {
    Step "Demo Checklist (Investor Flow)"
    Write-Host "1) Top KPI bar: show Reliability / Cost / p95 / Flagged (same time window)."
    Write-Host "2) Budget Guard panel: show MTD vs forecast vs limit + exceeded events."
    Write-Host "3) Release Gate panel: show baseline vs candidate drift and blocked state."
    Write-Host "4) Incidents table: verify BUDGET_EXCEEDED and RELEASE_BLOCKED rows."
    Write-Host "5) Drill-down: open one incident detail and compare two calls."
}

Ensure-Backend

if (-not $SkipSmoke) {
    Run-Smoke
} else {
    Write-Host "SkipSmoke enabled: assuming data already prepared."
}

Show-LiveSnapshot
Open-Dashboard
Show-DemoChecklist
