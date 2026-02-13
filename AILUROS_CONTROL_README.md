# Ailuros Control v0.1

## Enterprise-Grade LLM Call Observability & Governance

Ailuros Control is a production-ready observability and governance layer for LLM systems. It provides comprehensive tracking, auditing, and cost analysis for every LLM API call in your infrastructure.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Features](#features)
4. [Database Schema](#database-schema)
5. [API Reference](#api-reference)
6. [Integration Guide](#integration-guide)
7. [Dashboard UI](#dashboard-ui)
8. [Security](#security)
9. [Configuration](#configuration)
10. [Development](#development)

---

## Overview

### What is Ailuros Control?

Ailuros Control is an observability system that sits within your MCP Gateway to:

- **Track** every LLM API call with full context
- **Audit** requests and responses for compliance
- **Estimate** costs in real-time
- **Flag** problematic outputs for review
- **Compare** different model configurations
- **Analyze** usage patterns and trends

### Key Design Principles

- **Zero-latency impact**: Async auditing doesn't block requests
- **Fail-safe**: Audit failures never break production traffic
- **Privacy-aware**: Configurable PII handling and text truncation
- **Cost-conscious**: Estimates spend per call with detailed breakdowns
- **Production-grade**: Proper indexing, pagination, and query optimization

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      MCP Gateway                             │
│                                                              │
│  ┌───────────────┐                                          │
│  │   Request     │                                          │
│  │   Incoming    │                                          │
│  └───────┬───────┘                                          │
│          │                                                   │
│          v                                                   │
│  ┌───────────────┐          ┌──────────────────┐          │
│  │  TraceId      │          │  @AilurosAudit   │          │
│  │  Filter       │──────────│  Aspect (AOP)    │          │
│  └───────┬───────┘          └────────┬─────────┘          │
│          │                           │                      │
│          │                           v                      │
│          │                  ┌────────────────┐             │
│          │                  │ AilurosAudit   │             │
│          │                  │ Service        │             │
│          │                  └────────┬───────┘             │
│          │                           │                      │
│          v                           v                      │
│  ┌────────────────────────────────────────────┐            │
│  │           LLM Call Execution                │            │
│  │      (OpenAI, Anthropic, etc.)              │            │
│  └────────────────┬───────────────────────────┘            │
│                   │                                          │
│                   v                                          │
│         ┌──────────────────┐                                │
│         │  Cost Estimator  │                                │
│         └─────────┬────────┘                                │
│                   │                                          │
│                   v                                          │
│         ┌──────────────────┐                                │
│         │   PostgreSQL     │                                │
│         │  (ac_call, etc.) │                                │
│         └──────────────────┘                                │
│                                                              │
└──────────────────────────────────────────────────────────────┘
                           │
                           │ REST API
                           v
                  ┌─────────────────┐
                  │  Dashboard UI   │
                  │  (Next.js)      │
                  └─────────────────┘
```

### Components

1. **TraceIdFilter**: Generates unique trace IDs for request correlation
2. **@AilurosAudit Annotation**: Declarative auditing via AOP
3. **AuditService**: Async persistence of audit records
4. **CostEstimator**: Real-time cost calculation based on token usage
5. **Control Service**: Business logic for queries, flags, KPIs
6. **REST API**: `/api/ailuros/*` endpoints for dashboard
7. **Dashboard**: High-end dark-themed control console (separate Next.js app)

---

## Features

### ✅ Implemented in v0.1

#### Core Tracking
- [x] Trace ID generation for request correlation
- [x] Full request/response capture
- [x] Token usage tracking (prompt, completion, total)
- [x] Latency measurement
- [x] Cost estimation with 15+ model pricing tables
- [x] SHA-256 hashing for content verification

#### Observability
- [x] Advanced filtering (project, model, time range, status, etc.)
- [x] Flag management (wrong, risky, review)
- [x] Call comparison with diff generation
- [x] KPI dashboard (reliability, cost, latency, flags)
- [x] Daily/model cost aggregation

#### Data Management
- [x] Flyway database migrations
- [x] JPA entities with proper indexing
- [x] Paginated query results
- [x] Configurable text truncation
- [x] Async audit writes (zero request latency impact)

### 🚧 Planned for v0.2

- [ ] Replay functionality (re-execute calls with overrides)
- [ ] Prompt template versioning and drift detection
- [ ] Advanced diff algorithms (character-level, semantic)
- [ ] Budget management and alerts
- [ ] Retention policies and archival
- [ ] PII detection and redaction
- [ ] Integration with external cost tracking (Stripe, etc.)
- [ ] Multi-tenant support with RBAC
- [ ] Webhooks for flag notifications

---

## Database Schema

### Tables

#### `ac_prompt_template`
Versioned prompt templates for traceability.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| project_key | VARCHAR(64) | Project identifier |
| name | VARCHAR(128) | Template name |
| version | INT | Version number |
| content | TEXT | Template content |
| content_sha256 | CHAR(64) | SHA-256 hash |
| created_at | TIMESTAMPTZ | Creation timestamp |

**Indexes**: `(project_key, name, version)` UNIQUE, `(content_sha256)`

#### `ac_call`
Central audit log for all LLM calls.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| trace_id | VARCHAR(64) | Unique trace ID (UNIQUE) |
| project_key | VARCHAR(64) | Project identifier |
| env | VARCHAR(16) | Environment (prod/stage/dev) |
| status | VARCHAR(16) | Call status (ok/error/timeout/cancelled) |
| provider | VARCHAR(32) | LLM provider (openai/anthropic/etc.) |
| model | VARCHAR(64) | Model name |
| temperature | NUMERIC(4,3) | Sampling temperature |
| top_p | NUMERIC(4,3) | Top-p sampling |
| prompt_template_id | UUID | FK to prompt template (nullable) |
| prompt_ref | VARCHAR(160) | Reference: name@version or adhoc@sha |
| request_text | TEXT | Full request content |
| request_sha256 | CHAR(64) | Request hash |
| response_text | TEXT | Full response content |
| response_sha256 | CHAR(64) | Response hash |
| tokens_prompt | INT | Input tokens |
| tokens_completion | INT | Output tokens |
| tokens_total | INT | Total tokens |
| cost_estimate_usd | NUMERIC(12,6) | Estimated cost |
| latency_ms | INT | Response latency |
| upstream_request_id | VARCHAR(128) | Provider request ID |
| created_at | TIMESTAMPTZ | Call timestamp |

**Critical Indexes**:
- `(project_key, created_at DESC)`
- `(model, created_at DESC)`
- `(prompt_ref, created_at DESC)`
- `(status, created_at DESC)`
- `(trace_id)` UNIQUE

#### `ac_call_flag`
Manual flags for calls requiring review.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| call_id | UUID | FK to ac_call |
| flag_type | VARCHAR(32) | Flag type (wrong/risky/review) |
| note | TEXT | Optional notes |
| created_by | VARCHAR(64) | User who flagged |
| created_at | TIMESTAMPTZ | Flag timestamp |

**Indexes**: `(call_id)`, `(flag_type, created_at DESC)`

---

## API Reference

Base URL: `/api/ailuros`

### Endpoints

#### 1. Get Calls
```
GET /api/ailuros/calls
```

**Query Parameters**:
- `projectKey` (string): Filter by project (default: "default")
- `env` (string): Filter by environment
- `from` (ISO 8601): Start date (default: 30 days ago)
- `to` (ISO 8601): End date (default: now)
- `model` (string): Filter by model
- `provider` (string): Filter by provider
- `status` (string): Filter by status
- `page` (int): Page number (default: 0)
- `size` (int): Page size (default: 20)

**Response**: `Page<CallListDTO>`

```json
{
  "content": [
    {
      "id": "uuid",
      "traceId": "abc123...",
      "projectKey": "default",
      "env": "prod",
      "status": "ok",
      "provider": "openai",
      "model": "gpt-4",
      "promptRef": "summarize@v2",
      "tokensTotal": 1250,
      "costEstimateUsd": 0.0375,
      "latencyMs": 1850,
      "createdAt": "2024-01-15T10:30:00Z",
      "isFlagged": false,
      "flagCount": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1523,
  "totalPages": 77
}
```

#### 2. Get Call Detail
```
GET /api/ailuros/calls/{id}
```

**Response**: `CallDetailDTO` (includes full request/response text)

#### 3. Get Call by Trace ID
```
GET /api/ailuros/calls/trace/{traceId}
```

**Response**: `CallDetailDTO`

#### 4. Flag a Call
```
POST /api/ailuros/calls/{id}/flag
```

**Request Body**:
```json
{
  "flagType": "wrong",
  "note": "Hallucination detected in medical context",
  "createdBy": "reviewer@example.com"
}
```

**Response**: `FlagDTO`

#### 5. Compare Calls
```
GET /api/ailuros/compare?a={callIdA}&b={callIdB}
```

**Response**: `CompareDTO` (includes diff and summary)

#### 6. Get Cost Summary
```
GET /api/ailuros/cost/summary?projectKey=default&from=...&to=...
```

**Response**: `CostSummaryDTO`

```json
{
  "totalCost": 125.50,
  "forecastedCost": 180.00,
  "dailyCosts": [
    { "date": "2024-01-15", "cost": 12.50 }
  ],
  "costsByModel": [
    {
      "model": "gpt-4",
      "cost": 85.00,
      "callCount": 1250,
      "percentage": 67.7
    }
  ],
  "trend": {
    "currentPeriodCost": 125.50,
    "previousPeriodCost": 110.00,
    "changeAmount": 15.50,
    "changePercentage": 14.09,
    "trend": "up"
  }
}
```

#### 7. Get Overview KPIs
```
GET /api/ailuros/overview?projectKey=default&from=...&to=...
```

**Response**: `OverviewKpiDTO`

---

## Integration Guide

### Method 1: Declarative (Annotation-Based)

Add `@AilurosAudit` to any method that calls an LLM:

```java
@Service
public class CompletionService {

    @AilurosAudit(provider = "openai", model = "gpt-4", projectKey = "summarizer")
    public CompletionResponse complete(CompletionRequest request) {
        // Your LLM call implementation
        return openAIClient.complete(request);
    }
}
```

The aspect automatically captures:
- Request parameters (prompt, model, temperature)
- Response content (text, tokens, request ID)
- Latency and cost

### Method 2: Programmatic (Service-Based)

For more control, use `AilurosAuditService` directly:

```java
@Service
public class CompletionService {

    private final AilurosAuditService auditService;
    private final OpenAIClient openAIClient;

    public CompletionResponse complete(CompletionRequest request) {
        // Start audit
        var audit = auditService.startAudit()
            .provider("openai")
            .model(request.getModel())
            .projectKey("summarizer")
            .temperature(request.getTemperature())
            .requestText(request.getPrompt());

        try {
            // Execute call
            CompletionResponse response = openAIClient.complete(request);

            // Complete audit
            audit.responseText(response.getText())
                 .tokens(response.getUsage().getPromptTokens(),
                        response.getUsage().getCompletionTokens())
                 .upstreamRequestId(response.getId())
                 .complete();

            return response;

        } catch (Exception e) {
            audit.completeWithError(e.getMessage());
            throw e;
        }
    }
}
```

### Method 3: Filter/Interceptor (for proxied calls)

If your gateway proxies LLM calls transparently, add auditing in the proxy layer:

```java
@Component
public class LLMProxyInterceptor {

    private final AilurosAuditService auditService;

    public void intercept(HttpRequest request, HttpResponse response) {
        auditService.startAudit()
            .provider(extractProvider(request))
            .model(extractModel(request))
            .requestText(request.getBody())
            .responseText(response.getBody())
            .tokens(extractPromptTokens(response), extractCompletionTokens(response))
            .complete();
    }
}
```

---

## Dashboard UI

### Overview Page

**4 KPI Cards**:
1. **Reliability**: `100 - error_rate%` (green ≥95%, yellow 90-95%, red <90%)
2. **Flagged Calls**: Count + percentage
3. **Total Cost**: $ with trend indicator
4. **p95 Latency**: ms with trend indicator

**2 Charts**:
1. **Cost Over Time**: Line chart (daily breakdown)
2. **Drift Count Over Time**: Bar chart (flagged calls per day)

**Recent Flagged Calls Table**: Last 10 flagged calls with quick actions

### Calls Page

**Table Columns**:
- Time
- Trace ID
- Prompt Ref
- Model
- Tokens
- Cost
- Latency
- Status
- Flags

**Filters** (top of page):
- Project dropdown
- Environment dropdown
- Date range picker
- Model dropdown
- Status dropdown

**Click Row → Drawer Opens**:

**Tabs**:
1. **Summary**: Metadata, tokens, cost, latency
2. **Request**: Code viewer with syntax highlighting
3. **Response**: Code viewer with syntax highlighting
4. **Compare**: Select another call, view side-by-side diff

**Actions**:
- Flag button (opens modal)
- Replay button (v0.2)
- Copy trace ID
- Export JSON

### Cost Page

**Top Section**:
- Total spend (current period)
- Forecasted spend (linear projection)
- Budget bar (used/remaining)

**Charts**:
1. **Daily Cost**: Area chart
2. **Cost by Model**: Pie chart

**Table**: Top cost drivers (model, call count, total cost, %)

---

## Security

See [SECURITY_NOTES.md](./SECURITY_NOTES.md) for detailed security considerations.

### Quick Security Checklist

- [ ] Configure text truncation limits (`MAX_TEXT_LENGTH` in `AilurosAuditService`)
- [ ] Enable PII detection/redaction (v0.2 feature)
- [ ] Restrict API access with authentication
- [ ] Enable HTTPS for all API traffic
- [ ] Configure database encryption at rest
- [ ] Set up audit log retention policies
- [ ] Review and sanitize logs before sharing

### PII Handling

v0.1 provides basic text truncation. For production:

1. **Store-only-hash mode**: Set `request_text` and `response_text` to `NULL`, keep hashes
2. **Truncation**: Limit to first N characters (configurable)
3. **PII detection** (v0.2): Automatically redact emails, SSNs, credit cards

---

## Configuration

### Application Properties

```yaml
# Ailuros Control Configuration
ailuros:
  enabled: true
  async: true
  max-text-length: 50000
  retention-days: 30
  cost-estimation:
    enabled: true
    custom-pricing: false
```

### Database Connection

Ensure PostgreSQL is configured in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mcpgateway
    username: postgres
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### Async Configuration

Async auditing is enabled by default via `@EnableAsync` in `AilurosConfig`.

---

## Development

### Running Locally

1. **Start PostgreSQL**:
   ```bash
   docker run -d -p 5432:5432 \
     -e POSTGRES_DB=mcpgateway \
     -e POSTGRES_PASSWORD=postgres \
     postgres:15
   ```

2. **Run migrations**:
   ```bash
   mvn flyway:migrate
   ```

3. **Start application**:
   ```bash
   mvn spring-boot:run
   ```

4. **Test API**:
   ```bash
   curl http://localhost:8080/api/ailuros/health
   # Output: "Ailuros Control v0.1 operational"
   ```

### Adding Custom Model Pricing

Edit `CostEstimator.java`:

```java
table.put("custom-model", new ModelPricing(
    new BigDecimal("5.00"),    // Input price per 1M tokens
    new BigDecimal("15.00")    // Output price per 1M tokens
));
```

### Database Migrations

Create new migration: `V5__add_custom_feature.sql`

```sql
-- Add custom columns
ALTER TABLE ac_call ADD COLUMN custom_field VARCHAR(255);
```

---

## Troubleshooting

### Audit records not appearing

1. Check async execution is enabled (`@EnableAsync`)
2. Check database connection
3. Review logs for exceptions (audit failures are logged but don't break requests)
4. Verify `AilurosAuditService` is autowired correctly

### Cost estimates are zero

1. Check model name matches pricing table (case-insensitive, fuzzy match)
2. Add custom pricing for your model in `CostEstimator`
3. Verify token counts are being extracted correctly

### High database load

1. Check indexes are created (see schema)
2. Configure text truncation to reduce row size
3. Enable pagination for all queries (default: 20 per page)
4. Consider archival for old records

---

## Contributing

Ailuros Control is part of the MCP Gateway project. Contributions welcome!

### v0.2 Roadmap

Priority features for next release:

1. **Replay**: Re-execute calls with parameter overrides
2. **Drift Detection**: Compare responses against baseline templates
3. **Budget Management**: Set cost limits, trigger alerts
4. **Advanced Diff**: Character-level, semantic similarity
5. **Webhooks**: Notify external systems on flags/errors
6. **PII Detection**: Automatic redaction of sensitive data

---

## License

© 2024 MCP Gateway Project. All rights reserved.

---

## Support

For issues or questions:
- GitHub Issues: [mcp-gateway/issues](https://github.com/mcp-gateway/issues)
- Email: support@mcpgateway.com
- Docs: [docs.mcpgateway.com/ailuros](https://docs.mcpgateway.com/ailuros)
