-- AILUROS call_event v1 columns for gateway ingest and aggregation

ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS span_id VARCHAR(64);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS app_id VARCHAR(64);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS route VARCHAR(255);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(128);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS streaming BOOLEAN;
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS request_ts TIMESTAMP;
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS response_ts TIMESTAMP;
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS error_type VARCHAR(128);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS http_status INT;
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS event_version VARCHAR(32);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS prompt_hash VARCHAR(64);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS user_tier VARCHAR(64);
ALTER TABLE ac_call ADD COLUMN IF NOT EXISTS metadata_json TEXT;

-- Backfill from existing fields for better continuity
UPDATE ac_call SET app_id = project_key WHERE app_id IS NULL;
UPDATE ac_call SET prompt_version = prompt_ref WHERE prompt_version IS NULL;
UPDATE ac_call SET request_ts = created_at WHERE request_ts IS NULL;
UPDATE ac_call SET response_ts = created_at WHERE response_ts IS NULL;
UPDATE ac_call SET event_version = 'legacy' WHERE event_version IS NULL;

CREATE INDEX IF NOT EXISTS idx_call_app_env_time ON ac_call(app_id, env, request_ts DESC);
CREATE INDEX IF NOT EXISTS idx_call_route_time ON ac_call(route, request_ts DESC);
CREATE INDEX IF NOT EXISTS idx_call_span ON ac_call(span_id);
