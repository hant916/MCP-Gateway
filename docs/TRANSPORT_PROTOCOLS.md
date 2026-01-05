# MCP Gateway Transport Protocols Guide

## Overview

MCP Gateway supports all standard MCP transport protocols, providing flexible connectivity options for different use cases. Each transport protocol is fully implemented with upstream server connectivity, message forwarding, and billing integration.

## Supported Transport Protocols

### 1. SSE (Server-Sent Events)

**Status**: ✅ Fully Implemented (Legacy Support)

**Use Case**: Remote MCP servers with server-to-client streaming

**Characteristics**:
- One-way server push with client POST for requests
- HTTP-based, firewall-friendly
- Automatic reconnection support
- Wide browser and client library support

**Endpoints**:
```
# Establish SSE connection
GET /api/v1/sessions/{sessionId}/sse
Accept: text/event-stream

# Send message to upstream
POST /api/v1/sse/message?sessionId={sessionId}
Content-Type: application/json
```

**Example Usage**:
```bash
# 1. Create session with SSE transport
curl -X POST http://localhost:8080/api/v1/mcp-server/{serverId}/sessions \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"transportType": "SSE"}'

# 2. Establish SSE connection
curl -N http://localhost:8080/api/v1/sessions/{sessionId}/sse \
  -H "Authorization: Bearer {token}" \
  -H "Accept: text/event-stream"

# 3. Send message (in another terminal)
curl -X POST "http://localhost:8080/api/v1/sse/message?sessionId={sessionId}" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": "req-1"
  }'
```

---

### 2. Streamable HTTP

**Status**: ✅ Fully Implemented (MCP Standard 2025)

**Use Case**: **Recommended for production remote MCP servers**

**Characteristics**:
- Official MCP remote transport (introduced March 2025)
- NDJSON (Newline Delimited JSON) format
- HTTP-based streaming
- Simple deployment and firewall-friendly
- Better than SSE for modern implementations

**Endpoints**:
```
# Establish Streamable HTTP connection
GET /api/v1/sessions/{sessionId}/streamable-http
Accept: application/x-ndjson

# Send message to upstream
POST /api/v1/streamable-http/message?sessionId={sessionId}
Content-Type: application/json
```

**Example Usage**:
```bash
# 1. Create session with Streamable HTTP transport
curl -X POST http://localhost:8080/api/v1/mcp-server/{serverId}/sessions \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"transportType": "STREAMABLE_HTTP"}'

# 2. Establish Streamable HTTP connection
curl -N http://localhost:8080/api/v1/sessions/{sessionId}/streamable-http \
  -H "Authorization: Bearer {token}" \
  -H "Accept: application/x-ndjson"

# 3. Send message
curl -X POST "http://localhost:8080/api/v1/streamable-http/message?sessionId={sessionId}" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {"name": "example_tool", "arguments": {}},
    "id": "req-1"
  }'
```

**MCP Server Configuration Example**:
```json
{
  "serviceName": "Production MCP Server",
  "description": "Main MCP service",
  "transport": {
    "type": "STREAMABLE_HTTP",
    "config": {
      "serviceEndpoint": "https://mcp-server.example.com/stream",
      "messageEndpoint": "https://mcp-server.example.com/message",
      "sessionIdLocation": "QUERY_PARAM",
      "sessionIdParamName": "sessionId"
    }
  }
}
```

---

### 3. WebSocket

**Status**: ✅ Fully Implemented

**Use Case**: Real-time bidirectional communication

**Characteristics**:
- Full-duplex communication
- Low latency
- Persistent connection
- Ideal for interactive applications
- May have firewall/proxy issues in some environments

**Endpoint**:
```
# WebSocket connection
ws://localhost:8080/ws/sessions/{sessionId}
```

**Example Usage (JavaScript)**:
```javascript
// 1. Create session (using REST API)
const response = await fetch('http://localhost:8080/api/v1/mcp-server/{serverId}/sessions', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer {token}',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ transportType: 'WEBSOCKET' })
});
const session = await response.json();

// 2. Connect via WebSocket
const ws = new WebSocket(`ws://localhost:8080/ws/sessions/${session.sessionId}`);

ws.onopen = () => {
  console.log('WebSocket connected');

  // 3. Send message
  ws.send(JSON.stringify({
    jsonrpc: '2.0',
    method: 'tools/list',
    params: {},
    id: 'req-1'
  }));
};

ws.onmessage = (event) => {
  const response = JSON.parse(event.data);
  console.log('Received:', response);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('WebSocket closed');
};
```

**MCP Server Configuration Example**:
```json
{
  "serviceName": "WebSocket MCP Server",
  "transport": {
    "type": "WEBSOCKET",
    "config": {
      "serviceEndpoint": "wss://mcp-server.example.com/ws",
      "sessionIdLocation": "PATH_PARAM"
    }
  }
}
```

---

### 4. STDIO (Standard Input/Output)

**Status**: ✅ Fully Implemented

**Use Case**: Local MCP processes and CLI tools

**Characteristics**:
- Direct process communication
- stdin/stdout based
- Ideal for local tools
- Fast and simple
- No network overhead
- Process lifecycle managed by gateway

**Endpoints**:
```
# Establish STDIO connection (starts process)
GET /api/v1/sessions/{sessionId}/stdio
Accept: text/plain

# Send message to process stdin
POST /api/v1/stdio/message?sessionId={sessionId}
Content-Type: application/json

# Close connection (stops process)
DELETE /api/v1/sessions/{sessionId}/stdio
```

**Example Usage**:
```bash
# 1. Create session with STDIO transport
curl -X POST http://localhost:8080/api/v1/mcp-server/{serverId}/sessions \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"transportType": "STDIO"}'

# 2. Establish STDIO connection (starts the process)
curl -N http://localhost:8080/api/v1/sessions/{sessionId}/stdio \
  -H "Authorization: Bearer {token}" \
  -H "Accept: text/plain"

# 3. Send message to process stdin
curl -X POST "http://localhost:8080/api/v1/stdio/message?sessionId={sessionId}" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {},
    "id": "init-1"
  }'
```

**MCP Server Configuration Example**:
```json
{
  "serviceName": "Local MCP CLI Tool",
  "transport": {
    "type": "STDIO",
    "config": {
      "serviceEndpoint": "node /path/to/mcp-tool/index.js",
      "sessionIdLocation": "HEADER"
    }
  }
}
```

---

## Transport Selection Guide

### Decision Matrix

| Requirement | Recommended Transport |
|-------------|----------------------|
| Remote MCP server (2025+) | **Streamable HTTP** |
| Remote MCP server (legacy) | SSE |
| Real-time bidirectional | WebSocket |
| Local MCP tool/CLI | **STDIO** |
| Firewall-friendly | Streamable HTTP or SSE |
| Lowest latency | WebSocket or STDIO |
| Simplest deployment | Streamable HTTP |

### Comparison Table

| Feature | SSE | Streamable HTTP | WebSocket | STDIO |
|---------|-----|-----------------|-----------|-------|
| **MCP Standard** | Legacy | ✅ 2025 | Proposed | ✅ Standard |
| **Direction** | Server→Client | Server→Client | Bidirectional | Bidirectional |
| **Protocol** | HTTP | HTTP | WebSocket | Process I/O |
| **Latency** | Medium | Medium | Low | Very Low |
| **Firewall** | ✅ Friendly | ✅ Friendly | ⚠️ May block | N/A (local) |
| **Use Case** | Legacy remote | **Modern remote** | Real-time apps | **Local tools** |
| **Complexity** | Low | Low | Medium | Low |

---

## Configuration Examples

### Session ID Placement

The gateway supports three ways to pass session IDs to upstream servers:

#### 1. Query Parameter (Most Common)
```json
{
  "sessionIdLocation": "QUERY_PARAM",
  "sessionIdParamName": "sessionId"
}
```
Result: `https://upstream.com/api?sessionId={uuid}`

#### 2. Header
```json
{
  "sessionIdLocation": "HEADER",
  "sessionIdParamName": "X-Session-ID"
}
```
Result: Header `X-Session-ID: {uuid}`

#### 3. Path Parameter
```json
{
  "sessionIdLocation": "PATH_PARAM"
}
```
Endpoint: `https://upstream.com/api/{sessionId}`

---

## Billing Integration

All transport protocols are fully integrated with the billing system:

- **Connection establishment**: Billed per connection
- **Message sending**: Billed per message
- **Data transfer**: Can bill by data size (configurable)
- **Processing time**: Can bill by processing duration (configurable)

Default pricing (customizable in billing rules):
- SSE Connection: $0.002
- Streamable HTTP Connection: $0.003
- WebSocket Connection: $0.002
- STDIO Process Start: $0.005
- Message Sending: $0.001

---

## Error Handling

All transports implement comprehensive error handling:

1. **Connection Errors**: Logged and reported to client
2. **Upstream Timeout**: Automatic cleanup and client notification
3. **Process Crash** (STDIO): Detected and reported
4. **Network Issues**: Retry logic and graceful degradation

---

## Best Practices

### 1. For Production Remote Servers
✅ Use **Streamable HTTP** (MCP 2025 standard)
- Simple HTTP-based
- Firewall-friendly
- Well-supported

### 2. For Legacy Systems
✅ Use **SSE**
- Backward compatible
- Reliable streaming

### 3. For Real-Time Apps
✅ Use **WebSocket**
- Full-duplex
- Low latency
- Best for interactive UIs

### 4. For Local Tools
✅ Use **STDIO**
- Direct process communication
- No network overhead
- Perfect for CLI utilities

---

## Troubleshooting

### SSE Connection Drops
- Check network stability
- Verify firewall allows long-lived HTTP connections
- Check upstream server SSE implementation

### Streamable HTTP No Response
- Verify Accept header is `application/x-ndjson`
- Check upstream server supports streaming
- Verify upstream endpoint is correct

### WebSocket Connection Fails
- Check firewall/proxy WebSocket support
- Verify ws:// or wss:// protocol
- Check WebSocket handshake headers

### STDIO Process Not Starting
- Verify command path is absolute
- Check process permissions
- Verify command is executable
- Check process logs for startup errors

---

## References

- [MCP Specification - Transports](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
- [MCP Streamable HTTP Transport](https://modelcontextprotocol.info/docs/concepts/transports/)
- [WebSocket Proposal (SEP-1288)](https://github.com/modelcontextprotocol/modelcontextprotocol/issues/1288)

---

**Document Version**: 2.0
**Last Updated**: 2026-01-03
**MCP Gateway Version**: 1.0.0
