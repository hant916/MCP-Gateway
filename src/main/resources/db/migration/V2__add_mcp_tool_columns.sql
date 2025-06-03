-- Add columns to mcp_tools table
ALTER TABLE mcp_tools ADD COLUMN api_specification_id UUID;
ALTER TABLE mcp_tools ADD COLUMN builder_id UUID;
ALTER TABLE mcp_tools ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE mcp_tools ADD COLUMN price DECIMAL(10, 2);
ALTER TABLE mcp_tools ADD COLUMN pricing_model VARCHAR(50);
ALTER TABLE mcp_tools ADD COLUMN usage_quota INTEGER;
ALTER TABLE mcp_tools ADD COLUMN version VARCHAR(50);

-- Add foreign key constraints
ALTER TABLE mcp_tools ADD CONSTRAINT fk_mcp_tools_api_specification 
    FOREIGN KEY (api_specification_id) REFERENCES api_specifications(id);

ALTER TABLE mcp_tools ADD CONSTRAINT fk_mcp_tools_builder 
    FOREIGN KEY (builder_id) REFERENCES users(id);

-- Add check constraints
ALTER TABLE mcp_tools ADD CONSTRAINT valid_tool_status 
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED'));

ALTER TABLE mcp_tools ADD CONSTRAINT valid_pricing_model 
    CHECK (pricing_model IN ('MONTHLY', 'PAY_PER_USE', 'FREE'));

-- Create indexes
CREATE INDEX idx_mcp_tools_api_specification_id ON mcp_tools(api_specification_id);
CREATE INDEX idx_mcp_tools_builder_id ON mcp_tools(builder_id); 