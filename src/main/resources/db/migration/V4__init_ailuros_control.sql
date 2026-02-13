-- Ailuros Control: LLM Call Observability & Governance
-- This migration creates the core tables for tracking, auditing, and governing LLM calls

-- Prompt Template Management
-- Stores versioned prompt templates for traceability
CREATE TABLE ac_prompt_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_prompt_template_version UNIQUE (project_key, name, version)
);

CREATE INDEX idx_prompt_template_sha ON ac_prompt_template(content_sha256);
CREATE INDEX idx_prompt_template_project ON ac_prompt_template(project_key, created_at DESC);

-- LLM Call Tracking
-- Central audit log for all LLM API calls with full request/response capture
CREATE TABLE ac_call (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id VARCHAR(64) NOT NULL UNIQUE,
    project_key VARCHAR(64) NOT NULL,
    env VARCHAR(16) NOT NULL DEFAULT 'prod',
    status VARCHAR(16) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(64) NOT NULL,
    temperature NUMERIC(4,3),
    top_p NUMERIC(4,3),
    prompt_template_id UUID,
    prompt_ref VARCHAR(160),
    request_text TEXT,
    request_sha256 CHAR(64),
    response_text TEXT,
    response_sha256 CHAR(64),
    tokens_prompt INT,
    tokens_completion INT,
    tokens_total INT,
    cost_estimate_usd NUMERIC(12,6),
    latency_ms INT,
    upstream_request_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_call_prompt_template FOREIGN KEY (prompt_template_id)
        REFERENCES ac_prompt_template(id) ON DELETE SET NULL
);

-- Critical indexes for query performance
CREATE INDEX idx_call_project_time ON ac_call(project_key, created_at DESC);
CREATE INDEX idx_call_model_time ON ac_call(model, created_at DESC);
CREATE INDEX idx_call_prompt_ref ON ac_call(prompt_ref, created_at DESC);
CREATE INDEX idx_call_status ON ac_call(status, created_at DESC);
CREATE INDEX idx_call_trace ON ac_call(trace_id);
CREATE INDEX idx_call_env ON ac_call(env, created_at DESC);
CREATE INDEX idx_call_provider ON ac_call(provider, created_at DESC);

-- Call Flagging & Review
-- Allows marking calls for human review (wrong output, risky content, etc.)
CREATE TABLE ac_call_flag (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL,
    flag_type VARCHAR(32) NOT NULL,
    note TEXT,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flag_call FOREIGN KEY (call_id)
        REFERENCES ac_call(id) ON DELETE CASCADE
);

CREATE INDEX idx_flag_call ON ac_call_flag(call_id);
CREATE INDEX idx_flag_type ON ac_call_flag(flag_type, created_at DESC);

-- Comments
COMMENT ON TABLE ac_prompt_template IS 'Versioned prompt templates for traceability and drift detection';
COMMENT ON TABLE ac_call IS 'Comprehensive audit log of all LLM API calls with full context';
COMMENT ON TABLE ac_call_flag IS 'Manual flags for calls requiring review or indicating issues';

COMMENT ON COLUMN ac_call.status IS 'Call status: ok, error, timeout, cancelled';
COMMENT ON COLUMN ac_call.env IS 'Environment: prod, stage, dev';
COMMENT ON COLUMN ac_call.prompt_ref IS 'Reference format: name@version or adhoc@sha';
COMMENT ON COLUMN ac_call.cost_estimate_usd IS 'Estimated cost based on token usage and model pricing';
COMMENT ON COLUMN ac_call_flag.flag_type IS 'Flag types: wrong (incorrect output), risky (safety concern), review (needs human review)';
