-- AILUROS v0.2 governance: budget guard + release gate + regression runs

ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS release_candidate BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS ac_budget_policy (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(16) NOT NULL,
    route VARCHAR(255),
    daily_usd_limit NUMERIC(12,6),
    monthly_usd_limit NUMERIC(12,6),
    forecast_monthly_usd_limit NUMERIC(12,6),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_budget_policy_app_env_route
    ON ac_budget_policy(app_id, env, route);

CREATE TABLE IF NOT EXISTS ac_budget_eval (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(16) NOT NULL,
    route VARCHAR(255),
    window_start_ts TIMESTAMP NOT NULL,
    window_end_ts TIMESTAMP NOT NULL,
    cost_usd NUMERIC(12,6) NOT NULL,
    limit_usd NUMERIC(12,6),
    status VARCHAR(32) NOT NULL,
    forecast_monthly_usd NUMERIC(12,6),
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_budget_eval_app_env_created
    ON ac_budget_eval(app_id, env, created_ts DESC);

CREATE TABLE IF NOT EXISTS ac_release_baseline (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(16) NOT NULL,
    route VARCHAR(255),
    baseline_model VARCHAR(128),
    baseline_prompt_version VARCHAR(128),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_release_baseline_app_env_route
    ON ac_release_baseline(app_id, env, route);

CREATE TABLE IF NOT EXISTS ac_regression_suite (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(16) NOT NULL,
    route VARCHAR(255),
    suite_version VARCHAR(128) NOT NULL,
    storage_uri VARCHAR(512),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_regression_suite_app_env_route
    ON ac_regression_suite(app_id, env, route);

CREATE TABLE IF NOT EXISTS ac_regression_run (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(16) NOT NULL,
    route VARCHAR(255),
    baseline_model VARCHAR(128),
    candidate_model VARCHAR(128),
    baseline_prompt_version VARCHAR(128),
    candidate_prompt_version VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    started_ts TIMESTAMP,
    ended_ts TIMESTAMP,
    summary_json TEXT,
    report_uri VARCHAR(512),
    release_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_regression_run_app_env_created
    ON ac_regression_run(app_id, env, created_ts DESC);

CREATE INDEX IF NOT EXISTS idx_regression_run_status_created
    ON ac_regression_run(status, created_ts DESC);
