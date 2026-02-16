# Budget Guard v0.2

## Endpoints
- `GET /api/ailuros/budget/policies?app_id={app}&env={env}`
- `POST /api/ailuros/budget/policies`
- `POST /api/ailuros/budget/evaluate?app_id={app}&env={env}&route={route}`
- `GET /api/ailuros/budget/status?app_id={app}&env={env}&range=30d`

## Policy Model
- `dailyUsdLimit`
- `monthlyUsdLimit`
- `forecastMonthlyUsdLimit`
- dimension: `appId + env + optional route`

## Evaluation Rules
- `EXCEEDED`: daily or monthly actual cost above configured limit.
- `FORECAST_EXCEEDED`: projected month-end cost (7-day average extrapolation) above forecast limit.

## Runtime Behavior
- Budget evaluation runs on cron `ailuros.budget.eval-cron`.
- Manual trigger is available with `POST /budget/evaluate`.
- Every evaluation persists an audit row in `ac_budget_eval`.
- Exceeded events emit governance webhook when enabled.

## Config
- `ailuros.budget.enabled`
- `ailuros.budget.eval-cron`
- `ailuros.budget.forecast-days`
- `ailuros.webhook.enabled`
- `ailuros.webhook.url`
