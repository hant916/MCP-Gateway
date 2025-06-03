-- Users table
CREATE TABLE users (
    id UUID NOT NULL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE users ALTER COLUMN id SET DEFAULT RANDOM_UUID();
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- Sessions table
CREATE TABLE sessions (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    transport_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT valid_transport_type CHECK (transport_type IN ('STDIO', 'SSE', 'WEBSOCKET', 'STREAMABLE_HTTP'))
);

ALTER TABLE sessions ALTER COLUMN id SET DEFAULT RANDOM_UUID();
ALTER TABLE sessions ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sessions ALTER COLUMN last_active_at SET DEFAULT CURRENT_TIMESTAMP;

-- API Specifications table
CREATE TABLE api_specifications (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    spec_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT valid_spec_type CHECK (spec_type IN ('OPENAPI', 'MCP_TEMPLATE'))
);

ALTER TABLE api_specifications ALTER COLUMN id SET DEFAULT RANDOM_UUID();
ALTER TABLE api_specifications ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE api_specifications ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- MCP Tools table
CREATE TABLE mcp_tools (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parameters JSON,
    api_spec_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (api_spec_id) REFERENCES api_specifications(id)
);

ALTER TABLE mcp_tools ALTER COLUMN id SET DEFAULT RANDOM_UUID();
ALTER TABLE mcp_tools ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE mcp_tools ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- Message Logs table
CREATE TABLE message_logs (
    id UUID NOT NULL PRIMARY KEY,
    session_id UUID NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT valid_message_type CHECK (message_type IN ('REQUEST', 'RESPONSE', 'ERROR'))
);

ALTER TABLE message_logs ALTER COLUMN id SET DEFAULT RANDOM_UUID();
ALTER TABLE message_logs ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

-- Create indexes
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_token ON sessions(session_token);
CREATE INDEX idx_message_logs_session_id ON message_logs(session_id);
CREATE INDEX idx_mcp_tools_api_spec_id ON mcp_tools(api_spec_id);

-- Create triggers for updated_at
CREATE TRIGGER users_updated_at_trigger 
    BEFORE UPDATE ON users
    FOR EACH ROW 
    CALL "com.mcpgateway.trigger.UpdatedAtTriggerHandler";

CREATE TRIGGER api_specifications_updated_at_trigger 
    BEFORE UPDATE ON api_specifications
    FOR EACH ROW 
    CALL "com.mcpgateway.trigger.UpdatedAtTriggerHandler";

CREATE TRIGGER mcp_tools_updated_at_trigger 
    BEFORE UPDATE ON mcp_tools
    FOR EACH ROW 
    CALL "com.mcpgateway.trigger.UpdatedAtTriggerHandler"; 