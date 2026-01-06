# MCP Protocol Test Fixtures

This directory contains golden sample fixtures for testing the MCP protocol implementation.

## Purpose

These fixtures serve multiple purposes:
1. **Regression Testing**: Detect when protocol parsing breaks due to code changes
2. **Schema Validation**: Ensure new protocol versions are handled correctly
3. **Contract Testing**: Verify MCP gateway compliance with the protocol spec
4. **Edge Case Coverage**: Test unusual but valid protocol usage patterns
5. **Error Handling**: Validate proper rejection of invalid requests

## Directory Structure

```
test-fixtures/mcp/
├── requests/          # Valid request samples
├── responses/         # Valid response samples
├── invalid/           # Invalid/malformed request samples
└── README.md         # This file
```

## Fixture Categories

### Valid Requests (`requests/`)

These represent real-world, spec-compliant MCP requests:

- **valid-jsonrpc-tool-call.json** - Standard JSON-RPC 2.0 tool invocation
- **valid-custom-format-call.json** - Custom format tool call (legacy)
- **jsonrpc-list-tools.json** - List available tools
- **jsonrpc-initialize.json** - Session initialization with capabilities
- **complex-nested-params.json** - Deep object nesting in parameters
- **jsonrpc-with-unknown-fields.json** - Valid request with extra fields (forward compatibility)
- **batch-request.json** - Batch/multiple requests in one call
- **notification-no-id.json** - Notification (no response expected)

### Valid Responses (`responses/`)

Expected server responses for corresponding requests:

- **valid-jsonrpc-tool-call-response.json** - Successful tool execution result
- **jsonrpc-list-tools-response.json** - Tool listing response
- **jsonrpc-initialize-response.json** - Initialization response with server capabilities
- **jsonrpc-error-response.json** - Error response (method not found)
- **jsonrpc-invalid-params-error.json** - Parameter validation error
- **batch-response.json** - Batch response array

### Invalid Requests (`invalid/`)

These should be rejected or handled gracefully:

- **missing-jsonrpc-field.json** - Missing required `jsonrpc` field
- **missing-method.json** - Missing required `method` field
- **empty-id.json** - Empty string ID (should be non-empty or null)
- **null-id.json** - Null ID (valid for notifications only)
- **wrong-type-params.json** - `params` as string instead of object
- **params-array-instead-of-object.json** - `params` as array
- **non-existent-method.json** - Unknown/unsupported method
- **invalid-jsonrpc-version.json** - Wrong JSON-RPC version
- **malformed-json.txt** - Syntactically invalid JSON
- **missing-tool-name.json** - Custom format missing required field
- **huge-nested-depth.json** - Extremely deep object nesting (potential DoS)
- **extremely-large-payload.json** - Very large request (size limits)

## Usage

### Running Fixture Tests

```bash
# Run all protocol fixture tests
mvn test -Dtest=McpProtocolFixtureTest

# Run specific test
mvn test -Dtest=McpProtocolFixtureTest#testValidJsonRpcToolCall
```

### Adding New Fixtures

When adding new fixtures:

1. **Capture Real Traffic**: Use actual MCP server requests/responses when possible
2. **Minimal Examples**: Keep fixtures focused on one thing
3. **Naming Convention**: `{category}-{description}.json`
4. **Paired Samples**: Add both request and response for complete scenarios
5. **Update Tests**: Add test case in `McpProtocolFixtureTest.java`
6. **Document**: Add entry to this README

### Example: Adding a New Fixture

```bash
# 1. Create fixture file
cat > requests/new-feature-request.json << 'EOF'
{
  "jsonrpc": "2.0",
  "id": "test-new-feature",
  "method": "new/feature",
  "params": {...}
}
EOF

# 2. Create corresponding response
cat > responses/new-feature-response.json << 'EOF'
{
  "jsonrpc": "2.0",
  "id": "test-new-feature",
  "result": {...}
}
EOF

# 3. Add test in McpProtocolFixtureTest.java
@Test
void testNewFeature() throws IOException {
    Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/new-feature-request.json");
    String json = new String(resource.getInputStream().readAllBytes());
    MessageRequest request = objectMapper.readValue(json, MessageRequest.class);
    // Add assertions...
}
```

## Protocol Specification

These fixtures are based on:

- **JSON-RPC 2.0**: https://www.jsonrpc.org/specification
- **MCP Protocol**: Model Context Protocol specification (latest version)

### Key Protocol Rules

1. **JSON-RPC Format**:
   - Must have `jsonrpc: "2.0"`
   - Must have `method` (string)
   - Must have `id` (string, number, or null for notifications)
   - `params` is optional (object or array)

2. **MCP Custom Format**:
   - `type`: Message type (e.g., "tool_call")
   - `tool`: Tool name
   - `arguments`: Tool arguments (object)
   - `data`: Optional metadata

3. **Responses**:
   - Success: Contains `result`
   - Error: Contains `error` with `code` and `message`
   - Must echo back the request `id`

## Coverage Goals

Aim to maintain:
- ✅ **20-50 fixtures** covering common and edge cases
- ✅ **Protocol versions**: Test backward compatibility
- ✅ **Error scenarios**: At least one fixture per error code
- ✅ **Performance**: Include large/deep payloads
- ✅ **Security**: Path traversal, injection attempts

## Maintenance

### When to Update Fixtures

- **Protocol Version Change**: Add fixtures for new version
- **New Methods Added**: Add request/response pairs
- **Bug Found**: Add fixture that reproduces the bug
- **Breaking Change**: Update affected fixtures, keep old ones in `legacy/`

### Fixture Quality Checklist

- [ ] Valid JSON syntax
- [ ] Follows MCP spec
- [ ] Focused on single concern
- [ ] Includes comments if complex
- [ ] Has corresponding test case
- [ ] Documented in this README

## Automated Validation

The `McpProtocolFixtureTest` class automatically:

1. Loads all fixtures from directories
2. Parses them with Jackson ObjectMapper
3. Validates MessageRequest DTO mapping
4. Checks schema compliance
5. Tests error handling for invalid cases

### Current Test Coverage

- ✅ Valid request parsing (8 fixtures)
- ✅ Response parsing (4 fixtures)
- ✅ Invalid request handling (10 fixtures)
- ✅ Edge cases (deep nesting, large payloads)
- ✅ Protocol version compatibility
- ✅ Batch requests
- ✅ Notifications
- ✅ Unknown field tolerance

## Troubleshooting

### Test Failures

If fixture tests fail:

1. **Check fixture syntax**: Validate JSON with `jq` or online validator
2. **Review spec compliance**: Ensure fixture follows MCP/JSON-RPC spec
3. **Update DTO mapping**: MessageRequest may need new fields
4. **Check ObjectMapper config**: Jackson settings may affect parsing

### Adding Edge Cases

When encountering new edge cases in production:

1. Capture the problematic request/response
2. Anonymize sensitive data
3. Add as fixture with descriptive name
4. Create test that reproduces the issue
5. Fix code if needed
6. Verify test passes

## References

- [JSON-RPC 2.0 Spec](https://www.jsonrpc.org/specification)
- [MCP Protocol Docs](https://modelcontextprotocol.io/)
- [Jackson ObjectMapper](https://github.com/FasterXML/jackson-databind)
- [Spring Resource Loading](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#resources)
