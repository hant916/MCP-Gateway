# MCP Gateway API Documentation

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication

### JWT Authentication
Include the JWT token in the Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

### Authentication Endpoints

#### Register User
```http
POST /auth/register
Content-Type: application/json

{
    "username": "string",
    "password": "string", 
    "email": "string"
}

Response: 200 OK
{
    "token": "string",
    "username": "string",
    "userId": "uuid"
}
```

#### Authenticate
```http
POST /auth/authenticate
Content-Type: application/json

{
    "username": "string",
    "password": "string"
}

Response: 200 OK
{
    "token": "string", 
    "username": "string",
    "userId": "uuid"
}
```

## MCP Server Management

### Register MCP Server
```http
POST /mcp-server/register
Authorization: Bearer <token>
Content-Type: application/json

{
    "serviceName": "string",
    "description": "string",
    "iconUrl": "string",
    "repositoryUrl": "string",
    "transport": {
        "type": "SSE|WEBSOCKET|STREAMABLE_HTTP|STDIO",
        "config": {
            "serviceEndpoint": "string",
            "messageEndpoint": "string",
            "sessionIdLocation": "QUERY_PARAM|HEADER|PATH_PARAM",
            "sessionIdParamName": "string"
        }
    }
}

Response: 200 OK
{
    "serverId": "uuid",
    "status": "REGISTERED|ACTIVE|INACTIVE|ERROR",
    "serviceUrl": "string"
}
```

### Get MCP Server Configuration
```http
GET /mcp-server/{serverId}
Authorization: Bearer <token>

Response: 200 OK
{
    "serverId": "uuid",
    "serviceName": "string",
    "description": "string",
    "transport": { ... },
    "status": "string"
}
```

### Test MCP Server Connection
```http
POST /mcp-server/{serverId}/test-connection
Authorization: Bearer <token>

Response: 200 OK
{
    "status": "string",
    "message": "string"
}
```

### Update MCP Server Status
```http
PUT /mcp-server/{serverId}/status
Authorization: Bearer <token>
Content-Type: application/json

{
    "status": "REGISTERED|ACTIVE|INACTIVE|ERROR"
}

Response: 200 OK
```

## Session Management

### Create Session
```http
POST /mcp-server/{serverId}/sessions
Authorization: Bearer <token>
Content-Type: application/json

{
    "transportType": "SSE|WEBSOCKET|STREAMABLE_HTTP|STDIO"
}

Response: 200 OK
{
    "sessionId": "uuid",
    "transportType": "string",
    "status": "CREATED|CONNECTED|ACTIVE|EXPIRED|CLOSED",
    "endpoints": {
        "sse": "string",
        "message": "string"
    },
    "expiresAt": "datetime"
}
```

### Get Session Status
```http
GET /sessions/{sessionId}/status
Authorization: Bearer <token>

Response: 200 OK
{
    "sessionId": "uuid",
    "status": "string",
    "transportType": "string",
    "isExpired": boolean
}
```

## Session Transport

### Establish SSE Connection
```http
GET /sessions/{sessionId}/sse
Authorization: Bearer <token>
Accept: text/event-stream

Response: 200 OK (SSE Stream)
Content-Type: text/event-stream
```

### Send SSE Message
```http
POST /sse/message?sessionId={sessionId}
Authorization: Bearer <token>
Content-Type: application/json

# Standard Format
{
    "type": "invoke_tool",
    "tool": "string",
    "arguments": {
        "key": "value"
    }
}

# JSON-RPC Format (Recommended)
{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {
        "category": "string"
    },
    "id": "string"
}

Response: 200 OK
{
    "status": "Message sent"
}
```

### Streamable HTTP Request
```http
POST /sessions/{sessionId}/streamable-http
Authorization: Bearer <token>
Content-Type: application/json

{
    "type": "invoke_tool",
    "tool": "string",
    "arguments": { ... }
}

Response: 200 OK
Content-Type: application/octet-stream
```

## Billing System

### Health Check
```http
GET /billing/health
Authorization: Bearer <token>

Response: 200 OK
{
    "status": "healthy",
    "service": "billing",
    "timestamp": "datetime"
}
```

### Get Current Cost
```http
GET /billing/cost/current?userId={userId}
Authorization: Bearer <token>

Response: 200 OK
{
    "userId": "uuid",
    "currentCost": 0.0150,
    "currency": "USD",
    "timestamp": "datetime"
}
```

### Get Usage Records
```http
GET /billing/usage?userId={userId}&startTime={datetime}&endTime={datetime}&page=0&size=20
Authorization: Bearer <token>

Response: 200 OK
{
    "content": [
        {
            "id": "uuid",
            "sessionId": "uuid",
            "userId": "uuid",
            "timestamp": "datetime",
            "apiEndpoint": "string",
            "httpMethod": "string",
            "statusCode": 200,
            "requestSize": 1024,
            "responseSize": 2048,
            "processingMs": 150,
            "costAmount": 0.001,
            "messageType": "string",
            "billingStatus": "SUCCESS|FAILED|PENDING"
        }
    ],
    "pageable": { ... },
    "totalElements": 1,
    "totalPages": 1
}
```

### Get Session Usage Records
```http
GET /billing/usage/session/{sessionId}
Authorization: Bearer <token>

Response: 200 OK
[
    {
        "id": "uuid",
        "sessionId": "uuid",
        "apiEndpoint": "string",
        "costAmount": 0.001,
        "timestamp": "datetime"
    }
]
```

### Get Usage Summary
```http
GET /billing/usage/summary?userId={userId}&startTime={datetime}&endTime={datetime}
Authorization: Bearer <token>

Response: 200 OK
{
    "userId": "uuid",
    "totalCalls": 45,
    "totalCost": 0.0510,
    "periodStart": "datetime",
    "periodEnd": "datetime",
    "apiEndpointStats": [
        {
            "endpoint": "/api/v1/sse/message",
            "calls": 30,
            "cost": 0.030
        }
    ],
    "dailyStats": [
        {
            "date": "2024-01-15",
            "calls": 15,
            "cost": 0.015
        }
    ],
    "statusStats": {
        "200": 40,
        "400": 3,
        "500": 2
    }
}
```

### Test Record Usage
```http
POST /billing/test/record?sessionId={sessionId}&apiEndpoint=/api/v1/test&httpMethod=POST&statusCode=200&requestSize=1024&responseSize=2048&processingMs=150
Authorization: Bearer <token>

Response: 200 OK
{
    "status": "success",
    "sessionId": "uuid",
    "apiEndpoint": "string",
    "costAmount": 0.001
}
```

## Error Responses

All endpoints return errors in the following format:

```json
{
    "error": "string",
    "message": "string", 
    "timestamp": "datetime"
}
```

### Common HTTP Status Codes
- `200 OK` - Success
- `201 Created` - Resource created
- `400 Bad Request` - Invalid request
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - Access denied
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error

## Rate Limiting

API requests are subject to rate limiting:
- Default: 100 requests per minute per user
- Headers included in responses:
  - `X-RateLimit-Limit`: Request limit
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time

## Pagination

List endpoints support pagination:
- `page`: Page number (0-based, default: 0)
- `size`: Page size (default: 20, max: 100)
- `sort`: Sort criteria (e.g., `timestamp,desc`)

## Timestamps

All timestamps are in ISO 8601 format with timezone:
```
2024-01-15T10:30:00Z
``` 