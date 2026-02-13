-- V006__create_billing_tables.sql
-- 创建计费相关的数据表 (H2 Compatible Version)

-- 使用记录表
CREATE TABLE usage_records (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    api_endpoint VARCHAR(255) NOT NULL,
    http_method VARCHAR(10),
    status_code INTEGER NOT NULL,
    request_size BIGINT,
    response_size BIGINT,
    processing_ms INTEGER,
    cost_amount DECIMAL(10,4),
    message_type VARCHAR(50),
    error_message VARCHAR(500),
    client_ip VARCHAR(45),
    user_agent VARCHAR(255),
    billing_status VARCHAR(20) DEFAULT 'SUCCESS',
    
    -- 外键约束
    CONSTRAINT fk_usage_records_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_usage_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 计费规则表
CREATE TABLE billing_rules (
    id UUID PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL UNIQUE,
    api_pattern VARCHAR(255) NOT NULL,
    http_method VARCHAR(10),
    cost_per_call DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    cost_per_kb DECIMAL(10,6),
    cost_per_second DECIMAL(10,6),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    description VARCHAR(500),
    rule_type VARCHAR(20) NOT NULL DEFAULT 'PER_CALL',
    bill_failed_calls BOOLEAN DEFAULT FALSE,
    minimum_cost DECIMAL(10,4),
    maximum_cost DECIMAL(10,4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以提升查询性能
-- 使用记录表索引
CREATE INDEX idx_usage_records_session_id ON usage_records(session_id);
CREATE INDEX idx_usage_records_user_id ON usage_records(user_id);
CREATE INDEX idx_usage_records_timestamp ON usage_records(timestamp);
CREATE INDEX idx_usage_records_api_endpoint ON usage_records(api_endpoint);
CREATE INDEX idx_usage_records_status_code ON usage_records(status_code);
CREATE INDEX idx_usage_records_billing_status ON usage_records(billing_status);
CREATE INDEX idx_usage_records_user_timestamp ON usage_records(user_id, timestamp);

-- 计费规则表索引
CREATE INDEX idx_billing_rules_api_pattern ON billing_rules(api_pattern);
CREATE INDEX idx_billing_rules_is_active ON billing_rules(is_active);
CREATE INDEX idx_billing_rules_rule_type ON billing_rules(rule_type);
CREATE INDEX idx_billing_rules_priority ON billing_rules(priority DESC);
CREATE INDEX idx_billing_rules_active_priority ON billing_rules(is_active, priority DESC);

-- 插入默认计费规则
INSERT INTO billing_rules (id, rule_name, api_pattern, cost_per_call, description, priority) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'Default SSE Message Rule', '/api/v1/sse/message', 0.001, '默认SSE消息计费规则', 1),
('550e8400-e29b-41d4-a716-446655440001', 'Session Creation Rule', '/api/v1/mcp-server/*/sessions', 0.005, '会话创建计费规则', 2),
('550e8400-e29b-41d4-a716-446655440002', 'SSE Connection Rule', '/api/v1/sessions/*/sse', 0.002, 'SSE连接建立计费规则', 2),
('550e8400-e29b-41d4-a716-446655440003', 'Streamable HTTP Rule', '/api/v1/sessions/*/streamable-http', 0.003, '流式HTTP请求计费规则', 2),
('550e8400-e29b-41d4-a716-446655440004', 'Default Fallback Rule', '*', 0.001, '默认兜底计费规则', 0);

-- 添加约束检查 (H2 compatible)
ALTER TABLE billing_rules ADD CONSTRAINT chk_billing_rules_cost_positive 
    CHECK (cost_per_call >= 0);

ALTER TABLE billing_rules ADD CONSTRAINT chk_billing_rules_priority_range 
    CHECK (priority >= 0 AND priority <= 100);

ALTER TABLE usage_records ADD CONSTRAINT chk_usage_records_status_code_range 
    CHECK (status_code >= 100 AND status_code < 600); 