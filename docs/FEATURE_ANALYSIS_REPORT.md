# MCP-Gateway Feature Analysis Report

**Date:** 2026-02-14
**Branch:** `claude/analyze-ailuros-features-h8Hdl`
**Scope:** Full feature inventory, architecture analysis, and gap identification

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Authentication & Security](#2-authentication--security)
3. [Session & Transport Control](#3-session--transport-control)
4. [Billing & Usage Tracking](#4-billing--usage-tracking)
5. [Rate Limiting & Quota Management](#5-rate-limiting--quota-management)
6. [Admin Control Plane](#6-admin-control-plane)
7. [Analytics & Reporting](#7-analytics--reporting)
8. [Marketplace](#8-marketplace)
9. [Payments](#9-payments)
10. [Webhooks & Event System](#10-webhooks--event-system)
11. [Production Infrastructure](#11-production-infrastructure)
12. [Gap Analysis & Recommendations](#12-gap-analysis--recommendations)
13. [File Inventory](#13-file-inventory)

---

## 1. Executive Summary

MCP-Gateway is a **Spring Boot 3.2.3 / Java 21** application acting as a proxy and control plane between clients and upstream MCP (Model Context Protocol) servers. The codebase contains **174 Java source files** (~12,700 LOC) with **100+ REST API endpoints** organized across 20 controllers and 22 services.

### Architecture Overview

```
Client ←→ MCP Gateway ←→ Upstream MCP Server(s)
              │
    ┌─────────┼─────────┐
    │         │         │
 Billing   Session    Auth
 System    Manager   Layer
    │         │         │
    └─────────┼─────────┘
              │
         Database (H2/PostgreSQL)
```

### Feature Maturity Summary

| Feature Area | Status | Maturity |
|---|---|---|
| Authentication (JWT + API Key) | Functional | High |
| Transport Protocols (SSE, HTTP, WS, STDIO) | Functional | High |
| Billing & Usage Tracking | Functional | Medium |
| Rate Limiting (Redis + AOP) | Functional | High |
| Admin Control Plane | Functional | Medium |
| Analytics | Functional | Medium |
| Marketplace | Functional | Medium |
| Payments (Stripe) | Functional | Medium |
| Webhooks | Functional | Low-Medium |
| Event System | Functional | Low |
| CI/CD & Deployment | Configured | High |
| Monitoring (Prometheus/Grafana) | Configured | High |

---

## 2. Authentication & Security

### 2.1 Authentication Methods

**Dual-mode authentication** via filter chain:

1. **API Key Filter** (first priority) — checks `X-API-KEY` header
2. **JWT Filter** (second priority) — checks `Authorization: Bearer <token>` header

Both filters are fail-open: invalid credentials do not block the chain, allowing fallback to the next method. Unauthenticated requests reaching protected endpoints are rejected by Spring Security's authorization rules.

**Endpoints:**
- `POST /auth/register` — user registration with duplicate username/email check
- `POST /auth/authenticate` — returns JWT token (1-day TTL)
- `POST /api-keys/generate` — generates API key (one per user, replaces previous)

**JWT Configuration:**
- Algorithm: HS256 (HMAC-SHA256)
- Default TTL: 86,400,000ms (1 day)
- Refresh token TTL: 604,800,000ms (7 days) — configured but **not implemented**
- Password encoding: BCrypt

**Security Config (`SecurityConfig.java:86 lines`):**
- CSRF: Disabled (stateless API)
- Session management: STATELESS
- CORS: Configurable (dev: `*`, prod: restricted origins)

### 2.2 Security Gaps

| ID | Severity | Issue | Location |
|---|---|---|---|
| S-1 | CRITICAL | `@PreAuthorize("hasRole('USER')")` commented out on API key generation | `ApiKeyController.java:22` |
| S-2 | HIGH | No rate limiting on `/auth/authenticate` or `/auth/register` — brute force possible | `SecurityConfig.java:49` |
| S-3 | HIGH | Fail-open rate limiting when Redis is down | `RedisRateLimiter.java:159` |
| S-4 | HIGH | Default JWT secret is a hardcoded hex string in dev config | `application.yml:77` |
| S-5 | MEDIUM | JWT validation checks only username + expiration; no audience/issuer claims | `JwtService.java:51-54` |
| S-6 | MEDIUM | Refresh token endpoint not implemented despite configuration | — |
| S-7 | MEDIUM | X-Forwarded-For trusted without proxy whitelist — IP spoofing risk | `RateLimitAspect.java:173-184` |
| S-8 | MEDIUM | No password complexity requirements enforced | `AuthenticationService.java` |
| S-9 | LOW | Mixed security context repositories in stateless config | `SecurityConfig.java:42-46` |

---

## 3. Session & Transport Control

### 3.1 Session Lifecycle

```
CREATED → CONNECTED → ACTIVE → EXPIRED/CLOSED
```

- Token format: `session_` + UUID (no dashes)
- Default expiration: 1 hour
- `lastActiveAt` updated on each validation
- Stored in database via JPA

**Key files:**
- `SessionService.java` (252 lines) — lifecycle management, SSE emitter management
- `McpServerConnectionService.java` (699 lines) — upstream connection management for all 4 protocols

### 3.2 Transport Protocol Support

| Protocol | Controller | Transport Class | Status |
|---|---|---|---|
| SSE | `SseController` (39 lines) | `SseTransport` (75 lines) | Complete |
| Streamable HTTP | `StreamableHttpController` (90 lines) | `StreamableHttpTransport` (87 lines) | Complete |
| WebSocket | `WebSocketController` (126 lines) | `WebSocketTransport` (68 lines) | Complete |
| STDIO | `StdioController` (129 lines) | `StdioTransport` (93 lines) | Complete |

**Connection Management (McpServerConnectionService):**
```java
Map<UUID, Disposable>           upstreamConnections;     // SSE subscriptions
Map<UUID, BlockingQueue<String>> streamableHttpQueues;    // HTTP message queues
Map<UUID, Sinks.Many<String>>   websocketSinks;          // WS outgoing sinks
Map<UUID, Process>              stdioProcesses;           // OS processes
```

**URL Routing:** Session IDs can be placed in query params, path params, or headers depending on `McpServer.sessionIdLocation` configuration.

**Authentication to Upstream:** Supports API_KEY, BASIC_AUTH, OAUTH2 (not implemented), and NONE.

**Message Format Conversion:** Automatic detection and conversion between JSON-RPC format and standard format via `MessageRequest.isJsonRpcFormat()`.

### 3.3 Transport Gaps

| ID | Severity | Issue |
|---|---|---|
| T-1 | CRITICAL | `SessionService` message handlers for WebSocket/Streamable HTTP/STDIO are TODO stubs (only log) |
| T-2 | HIGH | STDIO `command.split("\\s+")` is vulnerable to command injection |
| T-3 | HIGH | No rate limiting on message endpoints — DoS after single session creation |
| T-4 | HIGH | In-memory connection maps have no size bounds — memory leak risk |
| T-5 | MEDIUM | Fire-and-forget message sending — no retry or acknowledgment |
| T-6 | MEDIUM | No circuit breaker for upstream connections |
| T-7 | MEDIUM | No session cleanup job for expired sessions |
| T-8 | MEDIUM | STDIO creates new BufferedWriter per message instead of caching |
| T-9 | LOW | OAuth2 auth type logged but not implemented |

---

## 4. Billing & Usage Tracking

### 4.1 Feature Overview

**Controller:** `BillingController.java` (129 lines)

| Endpoint | Description |
|---|---|
| `GET /billing/usage` | Paginated usage records with time range filtering |
| `GET /billing/usage/session/{sessionId}` | Session-specific usage |
| `GET /billing/usage/summary` | Aggregated cost summary by endpoint/day/status |
| `GET /billing/cost/current` | Cumulative user cost |
| `POST /billing/test/record` | Test/debug endpoint for manual recording |
| `GET /billing/health` | Service health check |

**Service:** `UsageBillingService.java` (273 lines)

- **Async recording** via `@Async` — non-blocking usage capture
- **Billing rule matching** — priority-based pattern matching against endpoint + method
- **Cost calculation** — based on request size, response size, and processing time
- **Duplicate detection** — post-hoc check (not prevention)

**Data captured per record:** sessionId, userId, apiEndpoint, httpMethod, statusCode, requestSize, responseSize, processingMs, clientIp, userAgent, messageType, errorMessage, costAmount

### 4.2 Default Pricing

| Operation | Cost |
|---|---|
| SSE Message | $0.001 |
| Session Creation | $0.005 |
| SSE Connection | $0.002 |
| Streamable HTTP | $0.003 |
| Default API Call | $0.001 |

### 4.3 Billing Gaps

| ID | Issue |
|---|---|
| B-1 | Test endpoint (`POST /billing/test/record`) should not exist in production |
| B-2 | No cost caps or daily spending limits |
| B-3 | No user notifications when approaching quota limits |
| B-4 | No refund mechanism |
| B-5 | No billing data export (CSV/PDF) |
| B-6 | Duplicate detection is post-hoc, not preventive |

---

## 5. Rate Limiting & Quota Management

### 5.1 Architecture

AOP-based via `@RateLimit` annotation → `RateLimitAspect` → `RedisRateLimiter` (Lua scripts)

**Four algorithms implemented:**

| Algorithm | Redis Structure | Use Case |
|---|---|---|
| Sliding Window | Sorted Set (ZSET) | Most accurate, default |
| Token Bucket | Hash Map (HSET) | Burst-friendly |
| Fixed Window | Counter (GET/INCRBY) | Simplest |
| Leaky Bucket | Alias → Sliding Window | N/A |

**Rate limit key strategies:** `user`, `ip`, `global`, `user:tool`, custom template interpolation

**Response headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`

### 5.2 Subscription Tiers

| Tier | Monthly Quota | Daily Limit | Per-Minute | Concurrent | Priority |
|---|---|---|---|---|---|
| FREE | 100 | 10 | 2 | 1 | No |
| BASIC | 1,000 | 100 | 10 | 3 | No |
| PRO | 10,000 | 1,000 | 50 | 10 | Yes |
| ENTERPRISE | Unlimited | Unlimited | 1,000 | 100 | Yes |

**Key files:**
- `RateLimitAspect.java` (186 lines) — AOP interceptor
- `RedisRateLimiter.java` (273 lines) — Redis Lua scripts
- `RateLimitService.java` (279 lines) — business logic
- `SubscriptionQuotaService.java` (305 lines) — tier-based quota checking

### 5.3 Applied Rate Limits by Endpoint

| Controller | Endpoint Type | Limit |
|---|---|---|
| Admin (list users) | Read | 200/min |
| Admin (update) | Write | 100/min |
| Admin (delete) | Delete | 50/min |
| Marketplace (browse) | Read | 1,000/min |
| Marketplace (search) | Read | 500/min |
| Marketplace (subscribe) | Write | 50/min |
| Marketplace (review) | Write | 20/hour |
| Analytics | Read | 100/min |
| Session creation | Write | 100/min |

---

## 6. Admin Control Plane

**Controller:** `AdminController.java` (149 lines)
**Service:** `AdminService.java` (246 lines)

All endpoints require `@PreAuthorize("hasRole('ADMIN')")`.

### 6.1 User Management

| Endpoint | Description |
|---|---|
| `GET /admin/users` | Paginated list with sorting (username, email, tier, loginDate, createdAt) |
| `GET /admin/users/{userId}` | User details + stats (subscriptions, requests, spend) |
| `PUT /admin/users/{userId}` | Update email, name, tier, active status |
| `DELETE /admin/users/{userId}` | Soft delete (deactivates user + subscriptions) |

### 6.2 Quota Management

| Endpoint | Description |
|---|---|
| `GET /admin/quotas` | List all quotas with filtering (all/active/exceeded) |
| `GET /admin/quotas/{subscriptionId}` | Get specific quota |
| `GET /admin/users/{userId}/quotas` | Get user's quotas |
| `PUT /admin/quotas/{subscriptionId}` | Update monthly quota |
| `POST /admin/quotas/{subscriptionId}/reset` | Reset remaining quota to monthly limit |

### 6.3 Admin Gaps

- No audit trail for admin actions
- No bulk operations
- No user search/filter (only sort)
- No user role management (can't change ADMIN/USER)
- No user impersonation capability

---

## 7. Analytics & Reporting

**Controller:** `AnalyticsController.java` (109 lines)
**Service:** `AnalyticsService.java` (329 lines)

All endpoints require ADMIN role. Rate limited at 100 req/min.

### 7.1 Available Analytics

**Usage Stats** (`GET /analytics/usage`):
- Total requests, active users
- Daily request/user trends
- Top 10 tools by usage
- Usage by transport type
- Usage by hour (24-point heatmap)
- Average response time

**Revenue Stats** (`GET /analytics/revenue`):
- Total revenue, ARPU
- MRR (Monthly Recurring Revenue)
- Daily revenue trend with new/churned subscriptions
- Revenue by subscription tier
- Top 10 tools by revenue
- Churn rate

**User Growth Stats** (`GET /analytics/users`):
- Total/new users in period
- Daily registration trend
- Active users with retention rate
- User distribution by tier

**Dashboard** (`GET /analytics/dashboard`):
- Composite of all three above for last 30 days

### 7.2 Analytics Gaps

- Dashboard hardcoded to 30-day window
- No YoY/MoM comparisons
- No cohort analysis
- No anomaly detection or alerting
- No data export capability
- Retention rate uses total users instead of cohort-based calculation

---

## 8. Marketplace

**Controller:** `MarketplaceController.java` (160 lines)
**Service:** `MarketplaceService.java` (345 lines)

### 8.1 Tool Management

| Endpoint | Description |
|---|---|
| `GET /marketplace/tools` | Browse with category, pricing model, rating filters |
| `GET /marketplace/tools/search` | Keyword search |
| `GET /marketplace/tools/{toolId}` | Detail with recent reviews and pricing tiers |
| `GET /marketplace/tools/popular` | Top by subscriber count |
| `GET /marketplace/tools/top-rated` | Top by average rating |
| `GET /marketplace/tools/recent` | Recently added |
| `GET /marketplace/categories` | All active categories |

**Sort options:** popular, rating, price_asc, price_desc, name, createdAt (default)

### 8.2 Subscriptions & Reviews

| Endpoint | Description |
|---|---|
| `POST /marketplace/tools/{toolId}/subscribe` | Subscribe (prevents duplicates) |
| `DELETE /marketplace/tools/{toolId}/subscribe` | Unsubscribe (immediate) |
| `POST /marketplace/tools/{toolId}/reviews` | Create review (set to PENDING) |
| `GET /marketplace/tools/{toolId}/reviews` | Get APPROVED reviews only |

**Review system:** Verified purchase flag based on active subscription. Reviews require approval but **no approval workflow exists**.

### 8.3 Pricing Tiers (Hardcoded)

| Model | Tiers |
|---|---|
| MONTHLY | Basic ($9.99, 1K calls), Pro ($49.99, 10K), Enterprise ($199.99, unlimited) |
| PAY_AS_YOU_GO | Single tier at tool price per call |
| FREE_TIER | 100 calls/month free |

### 8.4 Marketplace Gaps

- Review moderation workflow missing
- Pricing tiers hardcoded (should be per-tool configurable)
- No wishlist/favorites
- No recommendation engine
- No tool lifecycle management (DRAFT/ARCHIVED states missing)

---

## 9. Payments

**Controller:** `PaymentController.java` (156 lines)

### 9.1 Stripe Integration

| Endpoint | Description |
|---|---|
| `POST /payments/create-intent` | Create Stripe PaymentIntent |
| `GET /payments/history` | Payment history (user-scoped) |
| `GET /payments/{paymentId}` | Payment details (validates user ownership) |
| `POST /payments/webhook` | Stripe webhook receiver (no auth) |

**Webhook events handled:**
- `payment_intent.succeeded` → `handleSuccessfulPayment()`
- `payment_intent.payment_failed` → `handleFailedPayment()`

### 9.2 Payment Gaps

- Only 2 Stripe event types handled (missing refund, dispute)
- Webhook secret validation returns success if no secret configured
- No payment reconciliation
- No receipt/invoice generation
- No payment method management
- No currency conversion (USD-only assumed)

---

## 10. Webhooks & Event System

### 10.1 Webhook Management

**Controller:** `WebhookController.java` (148 lines)

| Endpoint | Description |
|---|---|
| `GET /webhooks` | Get user's webhooks |
| `POST /webhooks` | Create webhook |
| `PUT /webhooks/{webhookId}` | Update webhook |
| `DELETE /webhooks/{webhookId}` | Delete webhook |
| `POST /webhooks/{webhookId}/reactivate` | Reactivate suspended webhook |
| `GET /webhooks/{webhookId}/logs` | Get delivery logs |
| `GET /webhooks/events` | List available event types |

**Available events:** PAYMENT_SUCCESS, PAYMENT_FAILURE, SUBSCRIPTION_CREATED, SUBSCRIPTION_CANCELLED, QUOTA_EXCEEDED, TOOL_EXECUTED, SESSION_STARTED, SESSION_ENDED

### 10.2 Domain Events

| Event | Payload | Listeners |
|---|---|---|
| `UserRegisteredEvent` | userId, username, email, role | Audit log only (email/quota init marked as future) |
| `PaymentCreatedEvent` | paymentId, userId, amount, currency, status | Webhook + audit log |
| `ToolSubscribedEvent` | subscriptionId, userId, toolId, toolName | Access grant + webhook + audit log |

**Listener patterns:**
- All `@Async @EventListener` — non-blocking
- Exception suppression — failures logged, not rethrown
- Webhook delivery on relevant events

### 10.3 Event System Gaps

- Only 3 domain events defined (missing QuotaExceeded, PaymentFailed, SessionStarted, etc.)
- No webhook signing — receivers cannot verify authenticity
- No webhook test endpoint
- No dead-letter queue for failed deliveries
- No event sourcing or event replay capability
- Events stored as CSV strings instead of proper data structure

---

## 11. Production Infrastructure

### 11.1 Resilience

- **Circuit Breaker** (Resilience4j): Instances for mcpServer, stripePayment, webhook
- **Retry logic**: Automatic with exponential backoff
- **Timeout protection**: Configurable for all external calls

### 11.2 Performance

- **Caching** (Caffeine): tools, users, servers, subscriptions, apiKeys — max 1000 items, 5-min TTL
- **Connection pooling** (HikariCP): max 10 connections, 5-min idle
- **Database read-write splitting**: Optional master/replica routing
- **Async event processing**: Non-blocking listeners

### 11.3 Observability

- **Distributed tracing**: OpenTelemetry + Zipkin (B3 propagation)
- **Metrics**: Micrometer → Prometheus with custom business metrics
- **Dashboards**: 3 Grafana dashboards (system health, business, circuit breakers)
- **Audit logging**: `AuditLogService` for compliance (SOC 2, GDPR)
- **Structured logging**: JSON format with trace IDs

### 11.4 Deployment

- **Kubernetes**: Manifests with HPA, RBAC, Ingress
- **CI/CD**: GitHub Actions with automated testing and security scanning
- **Scripts**: deploy.sh, migrate-db.sh, health-check.sh, rollback.sh, backup.sh
- **Profiles**: dev (H2), prod (PostgreSQL), mysql

### 11.5 Testing

- 174 test files covering controllers, services, repositories
- Performance tests: Gatling (payment flow) + JMeter (API load)
- Targets: p95 < 500ms, throughput > 200 req/s, error rate < 1%

---

## 12. Gap Analysis & Recommendations

### 12.1 Priority Matrix

#### P0 — Critical (Fix Before Production)

| ID | Area | Issue | Recommendation |
|---|---|---|---|
| S-1 | Security | `@PreAuthorize` commented out on API key endpoint | Uncomment and test |
| S-2 | Security | No rate limit on auth endpoints | Add `@RateLimit(limit=5, window=60, key="ip")` |
| T-1 | Transport | SessionService message handlers are TODO stubs | Implement or remove dead code |
| T-2 | Security | STDIO command injection via naive `split()` | Use ProcessBuilder with proper argument parsing |
| B-1 | Billing | Test endpoint exposed in production | Remove or gate behind dev profile |
| S-3 | Security | Rate limiting fails open on Redis outage | Implement fail-closed or degraded mode |

#### P1 — High Priority

| ID | Area | Issue | Recommendation |
|---|---|---|---|
| T-3 | Transport | No rate limiting on message endpoints | Add per-session message rate limits |
| T-4 | Transport | Unbounded in-memory connection maps | Add eviction policy and max size |
| S-4 | Security | Hardcoded JWT secret in dev config | Generate random secret on startup |
| T-6 | Transport | No circuit breaker for upstream connections | Integrate Resilience4j on upstream calls |
| T-7 | Transport | No session cleanup job | Add scheduled cleanup for expired sessions |
| B-2 | Billing | No cost caps or spending limits | Add configurable daily/monthly caps |

#### P2 — Medium Priority

| ID | Area | Issue | Recommendation |
|---|---|---|---|
| S-5 | Security | JWT lacks audience/issuer validation | Add claims validation |
| S-6 | Security | Refresh token not implemented | Implement refresh token endpoint |
| S-7 | Security | X-Forwarded-For spoofing risk | Add proxy whitelist |
| S-8 | Security | No password complexity requirements | Add validation rules |
| B-3 | Billing | No user notifications on quota approach | Add threshold-based alerts |
| M-1 | Marketplace | Review moderation workflow missing | Add admin approval endpoint |
| M-2 | Marketplace | Pricing tiers hardcoded | Make configurable per tool |
| A-1 | Analytics | No comparison metrics (YoY/MoM) | Add period comparison |
| E-1 | Events | Only 3 domain events defined | Add QuotaExceeded, SessionStarted, etc. |
| W-1 | Webhooks | No webhook signing | Implement HMAC signature verification |

#### P3 — Low Priority / Future Enhancements

| ID | Area | Issue | Recommendation |
|---|---|---|---|
| A-2 | Analytics | No cohort analysis | Add cohort-based retention |
| A-3 | Analytics | No data export | Add CSV/PDF export |
| M-3 | Marketplace | No recommendation engine | Add collaborative filtering |
| P-1 | Payments | Only 2 Stripe events handled | Add refund, dispute events |
| P-2 | Payments | No receipt generation | Add invoice endpoint |
| E-2 | Events | No event sourcing | Persist events for audit trail |
| W-2 | Webhooks | No webhook test endpoint | Add test delivery endpoint |
| Admin-1 | Admin | No bulk operations | Add batch user/quota management |
| Admin-2 | Admin | No audit trail for admin actions | Log all admin operations |

### 12.2 Cross-Cutting Concerns

**Missing across multiple features:**
1. **Data export** — No CSV/PDF export anywhere (billing, analytics, admin)
2. **Audit trail** — Admin operations not logged; only event listeners log
3. **Bulk operations** — All CRUD is single-item only
4. **Notification system** — No email, SMS, or in-app notifications
5. **Multi-tenancy** — Schema supports it but not enforced
6. **API versioning** — All endpoints on `/api/v1/` but no versioning strategy documented

---

## 13. File Inventory

### Controllers (20 files)

| File | Lines | Purpose |
|---|---|---|
| `AuthController.java` | 39 | Registration + authentication |
| `ApiKeyController.java` | 35 | API key generation |
| `McpServerController.java` | ~80 | MCP server registration |
| `McpSessionController.java` | 43 | Session creation |
| `SessionTransportController.java` | 69 | Transport-agnostic endpoints |
| `SseController.java` | 39 | SSE subscribe + message |
| `StreamableHttpController.java` | 90 | Streamable HTTP endpoints |
| `WebSocketController.java` | 126 | WebSocket handler |
| `StdioController.java` | 129 | STDIO connection |
| `BillingController.java` | 129 | Usage + cost queries |
| `AdminController.java` | 149 | User + quota management |
| `AnalyticsController.java` | 109 | Usage/revenue/growth stats |
| `MarketplaceController.java` | 160 | Tool browsing + subscriptions |
| `PaymentController.java` | 156 | Stripe payments |
| `WebhookController.java` | 148 | Webhook CRUD + logs |

### Services (22 files)

| File | Lines | Purpose |
|---|---|---|
| `SessionService.java` | 252 | Session lifecycle + SSE |
| `McpServerConnectionService.java` | 699 | Upstream connection management |
| `UsageBillingService.java` | 273 | Usage tracking + cost calculation |
| `AuthenticationService.java` | 70 | Registration + auth logic |
| `ApiKeyService.java` | 49 | API key generation/validation |
| `AdminService.java` | 246 | User + quota admin |
| `AnalyticsService.java` | 329 | Usage/revenue/growth analytics |
| `MarketplaceService.java` | 345 | Tool browsing + subscriptions |

### Security (5 files)

| File | Lines | Purpose |
|---|---|---|
| `JwtService.java` | 76 | JWT generation/validation |
| `JwtAuthenticationFilter.java` | 108 | JWT filter |
| `ApiKeyAuthFilter.java` | 107 | API key filter |
| `SecurityConfig.java` | 86 | Spring Security config |
| `ApplicationConfig.java` | 44 | BCrypt + UserDetailsService |

### Rate Limiting (8 files)

| File | Lines | Purpose |
|---|---|---|
| `RateLimitAspect.java` | 186 | AOP interceptor |
| `RedisRateLimiter.java` | 273 | Redis Lua scripts (4 algorithms) |
| `RateLimitService.java` | 279 | Business logic |
| `SubscriptionQuotaService.java` | 305 | Tier-based quotas |
| `RateLimit.java` | 72 | Annotation definition |
| `RateLimitRule.java` | 90 | Rule configuration |
| `RateLimitResult.java` | 78 | Result model |
| `RateLimitExceptionHandler.java` | 60 | 429 response handler |

### Transport (5 files)

| File | Lines | Purpose |
|---|---|---|
| `McpTransport.java` | 9 | Interface |
| `SseTransport.java` | 75 | SSE emitter management |
| `WebSocketTransport.java` | 68 | WebSocket session registry |
| `StreamableHttpTransport.java` | 87 | BlockingQueue streaming |
| `StdioTransport.java` | 93 | Process I/O management |

### Events (4 files)

| File | Lines | Purpose |
|---|---|---|
| `DomainEvent.java` | 40 | Base event class |
| `UserRegisteredEvent.java` | 43 | User signup event |
| `PaymentCreatedEvent.java` | 48 | Payment event |
| `ToolSubscribedEvent.java` | 45 | Subscription event |

### Listeners (3 files)

| File | Lines | Purpose |
|---|---|---|
| `UserEventListener.java` | 98 | User registration handler |
| `PaymentEventListener.java` | 95 | Payment handler |
| `ToolSubscriptionEventListener.java` | 122 | Subscription handler |

### Domain Entities (19)

User, McpServer, Session, UsageRecord, BillingRule, McpTool, ToolSubscription, ToolCategory, ToolReview, ToolUsageRecord, ToolCallRecord, Payment, PaymentTransaction, WebhookConfig, WebhookLog, MessageLog, ApiKey, ApiSpecification, AuditLog

### Documentation (14 files)

README.md, ARCHITECTURE.md, API.md, API_EXAMPLES.md, DATA_MODEL.md, DEVELOPMENT.md, BILLING_SYSTEM_README.md, TRANSPORT_PROTOCOLS.md, RATE_LIMITING.md, MONITORING_GUIDE.md, PRODUCTION_DEPLOYMENT_CHECKLIST.md, TROUBLESHOOTING.md, POSTMAN_COLLECTION_GUIDE.md, FRONTEND_ROADMAP.md

---

*Report generated by automated codebase analysis.*
