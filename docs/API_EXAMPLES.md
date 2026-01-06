# MCP Gateway API Examples

Complete API usage examples with request/response samples.

## Table of Contents
- [Authentication](#authentication)
- [Marketplace](#marketplace)
- [Analytics](#analytics)
- [Admin](#admin)
- [Payments](#payments)
- [Webhooks](#webhooks)
- [Monitoring](#monitoring)

---

## Authentication

### Register a New User

**Request:**
```http
POST /api/v1/auth/register HTTP/1.1
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePassword123!",
  "fullName": "John Doe"
}
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "subscriptionTier": "FREE",
  "isActive": true,
  "emailVerified": false,
  "createdAt": "2024-01-01T12:00:00"
}
```

### Login

**Request:**
```http
POST /api/v1/auth/login HTTP/1.1
Content-Type: application/json

{
  "username": "johndoe",
  "password": "SecurePassword123!"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "expiresIn": 86400000,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

---

## Marketplace

### Browse Tools

**Request:**
```http
GET /api/v1/marketplace/tools?page=0&size=20&sortBy=popular HTTP/1.1
Authorization: Bearer <token>
```

**Response:**
```json
{
  "content": [
    {
      "id": "tool-001",
      "name": "Weather API",
      "description": "Get current weather data",
      "iconUrl": "https://example.com/icon.png",
      "category": {
        "id": "cat-001",
        "name": "Data & APIs"
      },
      "pricingModel": "MONTHLY",
      "price": 9.99,
      "averageRating": 4.5,
      "reviewCount": 128,
      "subscriberCount": 1543,
      "tags": ["weather", "api", "data"]
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

### Subscribe to a Tool

**Request:**
```http
POST /api/v1/marketplace/tools/tool-001/subscribe HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "monthlyQuota": 1000
}
```

**Response:**
```json
{
  "id": "sub-001",
  "toolId": "tool-001",
  "clientId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "monthlyQuota": 1000,
  "remainingQuota": 1000,
  "quotaResetAt": "2024-02-01T00:00:00"
}
```

### Create a Review

**Request:**
```http
POST /api/v1/marketplace/tools/tool-001/reviews HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "rating": 5,
  "title": "Excellent tool!",
  "comment": "Very reliable and easy to use. Highly recommended!"
}
```

**Response:**
```json
{
  "id": "review-001",
  "toolId": "tool-001",
  "toolName": "Weather API",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "rating": 5,
  "title": "Excellent tool!",
  "comment": "Very reliable and easy to use. Highly recommended!",
  "isVerifiedPurchase": true,
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

## Analytics

### Get Usage Statistics

**Request:**
```http
GET /api/v1/analytics/usage?startDate=2024-01-01&endDate=2024-01-31&groupBy=day HTTP/1.1
Authorization: Bearer <admin-token>
```

**Response:**
```json
{
  "totalRequests": 15234,
  "activeUsers": 342,
  "averageResponseTime": 156.7,
  "requestTrend": [
    {
      "date": "2024-01-01",
      "value": 523
    },
    {
      "date": "2024-01-02",
      "value": 612
    }
  ],
  "topTools": [
    {
      "toolId": "tool-001",
      "toolName": "Weather API",
      "requestCount": 3421,
      "uniqueUsers": 89
    }
  ],
  "usageByTransport": {
    "SSE": 5432,
    "WEBSOCKET": 7123,
    "HTTP": 2679
  }
}
```

### Get Revenue Statistics

**Request:**
```http
GET /api/v1/analytics/revenue?startDate=2024-01-01&endDate=2024-01-31 HTTP/1.1
Authorization: Bearer <admin-token>
```

**Response:**
```json
{
  "totalRevenue": 12543.50,
  "averageRevenuePerUser": 36.71,
  "monthlyRecurringRevenue": 8934.00,
  "revenueTrend": [
    {
      "date": "2024-01-01",
      "revenue": 423.50,
      "newSubscriptions": 12,
      "churnedSubscriptions": 2
    }
  ],
  "revenueByTier": {
    "BASIC": 3200.00,
    "PRO": 7500.00,
    "ENTERPRISE": 1843.50
  },
  "subscriptionStats": {
    "totalActiveSubscriptions": 342,
    "newSubscriptionsThisMonth": 45,
    "churnedSubscriptionsThisMonth": 8,
    "churnRate": 2.34
  }
}
```

---

## Admin

### Get All Users

**Request:**
```http
GET /api/v1/admin/users?page=0&size=20&sortBy=createdAt HTTP/1.1
Authorization: Bearer <admin-token>
```

**Response:**
```json
{
  "content": [
    {
      "id": "user-001",
      "username": "johndoe",
      "email": "john@example.com",
      "fullName": "John Doe",
      "subscriptionTier": "PRO",
      "isActive": true,
      "emailVerified": true,
      "totalSubscriptions": 3,
      "activeSubscriptions": 2,
      "totalRequests": 1234,
      "totalSpent": "149.97",
      "createdAt": "2024-01-01T12:00:00"
    }
  ],
  "totalElements": 342,
  "totalPages": 18
}
```

### Update User Quota

**Request:**
```http
PUT /api/v1/admin/quotas/sub-001 HTTP/1.1
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "monthlyQuota": 5000,
  "resetRemainingQuota": true
}
```

**Response:**
```json
{
  "subscriptionId": "sub-001",
  "userId": "user-001",
  "username": "johndoe",
  "toolId": "tool-001",
  "toolName": "Weather API",
  "monthlyQuota": 5000,
  "remainingQuota": 5000,
  "usedQuota": 0,
  "usagePercentage": 0.0,
  "status": "ACTIVE"
}
```

---

## Payments

### Create Payment Intent

**Request:**
```http
POST /api/v1/payments/create-intent HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "amount": 49.99,
  "currency": "USD",
  "toolId": "tool-001",
  "description": "Weather API Pro subscription"
}
```

**Response:**
```json
{
  "paymentIntentId": "pi_3ABC123xyz",
  "clientSecret": "pi_3ABC123xyz_secret_XYZ789",
  "amount": 49.99,
  "currency": "USD",
  "status": "requires_payment_method",
  "description": "Weather API Pro subscription"
}
```

### Get Payment History

**Request:**
```http
GET /api/v1/payments/history?page=0&size=20 HTTP/1.1
Authorization: Bearer <token>
```

**Response:**
```json
{
  "content": [
    {
      "paymentId": "pay-001",
      "paymentIntentId": "pi_3ABC123xyz",
      "amount": 49.99,
      "currency": "USD",
      "status": "succeeded",
      "description": "Weather API Pro subscription",
      "toolName": "Weather API",
      "createdAt": "2024-01-15T10:00:00",
      "updatedAt": "2024-01-15T10:00:15"
    }
  ],
  "totalElements": 12
}
```

---

## Webhooks

### Create Webhook

**Request:**
```http
POST /api/v1/webhooks HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "url": "https://myapp.com/webhooks/mcp",
  "events": [
    "payment.success",
    "subscription.created",
    "quota.exceeded"
  ],
  "description": "Main webhook endpoint",
  "retryCount": 3,
  "timeoutSeconds": 30
}
```

**Response:**
```json
{
  "id": "webhook-001",
  "url": "https://myapp.com/webhooks/mcp",
  "status": "ACTIVE",
  "events": [
    "payment.success",
    "subscription.created",
    "quota.exceeded"
  ],
  "description": "Main webhook endpoint",
  "isActive": true,
  "retryCount": 3,
  "timeoutSeconds": 30,
  "failureCount": 0,
  "successCount": 0,
  "createdAt": "2024-01-20T15:00:00"
}
```

### Webhook Payload Example

When an event occurs, your webhook endpoint will receive:

```http
POST /webhooks/mcp HTTP/1.1
Host: myapp.com
Content-Type: application/json
X-Webhook-Signature: base64-hmac-sha256-signature
X-Webhook-Event: payment.success
User-Agent: MCP-Gateway-Webhook/1.0

{
  "event": "payment.success",
  "timestamp": 1705761234567,
  "data": {
    "paymentId": "pay-001",
    "amount": 49.99,
    "currency": "USD",
    "userId": "user-001",
    "toolId": "tool-001"
  }
}
```

### Verify Webhook Signature (Example Code)

```python
import hmac
import hashlib
import base64

def verify_webhook(payload, signature, secret):
    expected_signature = base64.b64encode(
        hmac.new(
            secret.encode(),
            payload.encode(),
            hashlib.sha256
        ).digest()
    ).decode()

    return hmac.compare_digest(expected_signature, signature)
```

---

## Monitoring

### Prometheus Metrics

**Request:**
```http
GET /actuator/prometheus HTTP/1.1
```

**Response:**
```
# HELP mcp_tool_executions_total Total number of tool executions
# TYPE mcp_tool_executions_total counter
mcp_tool_executions_total{tool="Weather API",status="success"} 1543.0
mcp_tool_executions_total{tool="Weather API",status="failure"} 23.0

# HELP mcp_tool_execution_duration Tool execution duration
# TYPE mcp_tool_execution_duration histogram
mcp_tool_execution_duration_bucket{tool="Weather API",le="0.01"} 234.0
mcp_tool_execution_duration_bucket{tool="Weather API",le="0.05"} 1234.0
mcp_tool_execution_duration_bucket{tool="Weather API",le="0.1"} 1456.0

# HELP mcp_sessions_created_total Total number of sessions created
# TYPE mcp_sessions_created_total counter
mcp_sessions_created_total{transport="SSE"} 543.0
mcp_sessions_created_total{transport="WEBSOCKET"} 892.0

# HELP mcp_payments_total Total payments
# TYPE mcp_payments_total counter
mcp_payments_total{status="succeeded",currency="USD"} 234.0
mcp_payments_total{status="failed",currency="USD"} 12.0
```

### Health Check

**Request:**
```http
GET /actuator/health HTTP/1.1
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 123456789012,
        "threshold": 10485760
      }
    }
  }
}
```

---

## Error Handling

### Common Error Responses

**400 Bad Request:**
```json
{
  "timestamp": "2024-01-20T15:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for field 'email': must be a valid email address",
  "path": "/api/v1/auth/register"
}
```

**401 Unauthorized:**
```json
{
  "timestamp": "2024-01-20T15:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/v1/marketplace/tools"
}
```

**403 Forbidden:**
```json
{
  "timestamp": "2024-01-20T15:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required.",
  "path": "/api/v1/admin/users"
}
```

**429 Too Many Requests:**
```json
{
  "timestamp": "2024-01-20T15:30:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 45 seconds.",
  "path": "/api/v1/marketplace/tools"
}
```

---

## Additional Resources

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs
- **Metrics**: http://localhost:8080/actuator/prometheus
- **Health**: http://localhost:8080/actuator/health
