# 🚀 MCP Gateway

A comprehensive Spring Boot-based MCP (Model Context Protocol) Gateway that provides secure, scalable API management with session-based transport management and pay-for-usage billing.

## ✨ Key Features

### 🔐 Authentication & Security
- **JWT Authentication**: Secure token-based authentication
- **API Key Support**: Multiple authentication methods
- **User Registration**: Complete user management system

### 🌐 Transport Protocols
- **SSE (Server-Sent Events)**: Real-time bidirectional streaming ✅ **Fully Implemented**
- **Streamable HTTP**: MCP standard remote transport (2025) ✅ **Fully Implemented**
- **WebSocket**: Full-duplex real-time communication ✅ **Fully Implemented**
- **STDIO**: Local process communication ✅ **Fully Implemented**

### 💰 Pay-for-Usage Billing
- **Real-time Usage Tracking**: Automatic API call recording
- **Flexible Billing Rules**: Multiple pricing models
- **Comprehensive Analytics**: Usage statistics and cost analysis
- **REST API**: Complete billing management interface

### 📡 SSE Proxy Features
- **Bidirectional SSE**: Real-time event streaming between client and upstream servers
- **Session Management**: Links sessions to specific MCP servers
- **Message Forwarding**: Intelligent message routing with format adaptation
- **Error Handling**: Comprehensive error handling and connection cleanup

### 🛡️ Production-Ready Features

#### Resilience & Fault Tolerance
- **Circuit Breaker**: Resilience4j-based fault tolerance for upstream services
- **Health Checks**: Custom health indicators for upstream MCP servers
- **Retry Logic**: Automatic retry with exponential backoff
- **Timeout Protection**: Configurable timeouts for all external calls
- **Graceful Degradation**: Fallback responses when services are unavailable

#### Performance & Scalability
- **Local Caching**: Caffeine-based caching for tools, users, and subscriptions
- **Database Read-Write Splitting**: Automatic routing to master/replica datasources
- **Connection Pooling**: HikariCP with optimized settings
- **Async Event Processing**: Non-blocking event handlers for webhooks and notifications

#### Observability & Monitoring
- **Distributed Tracing**: OpenTelemetry + Zipkin integration
- **Prometheus Metrics**: Comprehensive business and system metrics
- **Grafana Dashboards**: Pre-built dashboards for system health, business metrics, and circuit breakers
- **Structured Logging**: JSON-formatted logs with trace IDs
- **Audit Trail**: Complete audit logging for compliance (SOC 2, GDPR)

#### Security & Compliance
- **Audit Logging**: Full audit trail for all critical operations
- **API Key Management**: Secure API key creation and validation
- **Rate Limiting**: Per-user and global rate limits
- **Input Validation**: Comprehensive request validation
- **Secrets Management**: Kubernetes secrets integration

#### DevOps & Deployment
- **Kubernetes Ready**: Production-ready K8s manifests with HPA, ingress, and RBAC
- **CI/CD Pipeline**: Automated testing, security scanning, and deployment
- **One-Click Deployment**: Automated deployment scripts for staging and production
- **Database Migrations**: Flyway-based schema versioning
- **Monitoring Stack**: Docker Compose setup for Prometheus, Grafana, AlertManager, and Zipkin

## 🏗️ Architecture

```
Client ←→ MCP Gateway ←→ Upstream MCP Server
           ↓
    Billing System + Database
```

### Core Components
- **SessionService**: Manages sessions and coordinates connections
- **McpServerConnectionService**: Handles upstream connections and message forwarding
- **UsageBillingService**: Real-time usage tracking and cost calculation
- **BillingController**: REST API for billing queries and analytics

## 🚀 Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.8.x or higher
- Git

### Installation & Running

1. **Clone and build**:
```bash
git clone [repository-url]
cd mcp-gateway
mvn clean install
```

2. **Run the application**:
```bash
mvn spring-boot:run
```

3. **Access the application**:
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`

## 📖 API Usage

### 1. Authentication
```bash
# Register user
POST /api/v1/auth/register
{
    "username": "testuser",
    "password": "password123",
    "email": "test@example.com"
}

# Authenticate
POST /api/v1/auth/authenticate
{
    "username": "testuser",
    "password": "password123"
}
```

### 2. Register MCP Server
```bash
POST /api/v1/mcp-server/register
Authorization: Bearer {jwt_token}
{
    "serviceName": "Awesome MCP Server",
    "description": "MCP server with billing support",
    "transport": {
        "type": "SSE",
        "config": {
            "serviceEndpoint": "https://your-mcp-server.com/sse",
            "messageEndpoint": "https://your-mcp-server.com/sse/message",
            "sessionIdLocation": "QUERY_PARAM",
            "sessionIdParamName": "sessionId"
        }
    }
}
```

### 3. Create Session and Connect

#### Option 1: SSE (Server-Sent Events)
```bash
# Create session
POST /api/v1/mcp-server/{serverId}/sessions
{
    "transportType": "SSE"
}

# Establish SSE connection
GET /api/v1/sessions/{sessionId}/sse
Accept: text/event-stream

# Send messages
POST /api/v1/sse/message?sessionId={sessionId}
{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": "request-1"
}
```

#### Option 2: Streamable HTTP (Recommended for MCP 2025)
```bash
# Create session
POST /api/v1/mcp-server/{serverId}/sessions
{
    "transportType": "STREAMABLE_HTTP"
}

# Establish Streamable HTTP connection
GET /api/v1/sessions/{sessionId}/streamable-http
Accept: application/x-ndjson

# Send messages
POST /api/v1/streamable-http/message?sessionId={sessionId}
{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {"name": "example_tool"},
    "id": "req-1"
}
```

#### Option 3: WebSocket (Full-Duplex)
```bash
# Create session
POST /api/v1/mcp-server/{serverId}/sessions
{
    "transportType": "WEBSOCKET"
}

# Connect via WebSocket
ws://localhost:8080/ws/sessions/{sessionId}

# Send/receive messages over WebSocket bidirectionally
```

#### Option 4: STDIO (Local Process)
```bash
# Create session
POST /api/v1/mcp-server/{serverId}/sessions
{
    "transportType": "STDIO"
}

# Establish STDIO connection (starts local process)
GET /api/v1/sessions/{sessionId}/stdio
Accept: text/plain

# Send messages to process stdin
POST /api/v1/stdio/message?sessionId={sessionId}
{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {},
    "id": "init-1"
}
```

### 4. Billing & Analytics
```bash
# Get current cost
GET /api/v1/billing/cost/current?userId={userId}

# View usage records
GET /api/v1/billing/usage?userId={userId}&startTime=2024-01-01T00:00:00Z

# Usage summary
GET /api/v1/billing/usage/summary?userId={userId}
```

## 💳 Billing System

### Default Pricing
- **SSE Messages**: $0.001 per message
- **Session Creation**: $0.005 per session
- **SSE Connection**: $0.002 per connection
- **Streamable HTTP**: $0.003 per request
- **Default**: $0.001 per API call

### Features
- ✅ Real-time usage tracking
- ✅ Flexible pricing rules with pattern matching
- ✅ Multiple billing models (per-call, per-data, per-time)
- ✅ Usage analytics and reporting
- ✅ Automatic cost calculation

## 🔧 Configuration

Key configuration in `application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:mcpdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 

jwt:
  secret: your-secret-key
  expiration: 86400000

mcp:
  session:
    expiration: 3600
```

## 📚 Documentation

### Core Documentation
- **[📮 Postman Collection Guide](docs/POSTMAN_COLLECTION_GUIDE.md)** - Complete testing guide
- **[💰 Billing System](docs/BILLING_SYSTEM_README.md)** - Detailed billing documentation
- **[📋 API Documentation](docs/api-docs.yaml)** - OpenAPI specification
- **[🏗️ Architecture](docs/ARCHITECTURE.md)** - System architecture and design
- **[📊 Data Model](docs/DATA_MODEL.md)** - Database schema and relationships

### Operational Documentation
- **[🚀 Deployment Guide](scripts/README.md)** - Automated deployment scripts and procedures
- **[📊 Monitoring Guide](grafana/README.md)** - Grafana dashboards and Prometheus metrics
- **[🔧 Troubleshooting Guide](docs/TROUBLESHOOTING.md)** - Common issues and solutions
- **[✅ Production Checklist](docs/PRODUCTION_DEPLOYMENT_CHECKLIST.md)** - Pre-deployment validation
- **[⚡ Performance Testing](performance-tests/README.md)** - Load testing and benchmarks

## 🧪 Testing

### Using Postman
1. Import `mcp-gateway-complete.postman_collection.json`
2. Set `base_url` to `http://localhost:8080`
3. Run the "Complete SSE Workflow with Billing" for end-to-end testing

### Manual Testing
```bash
# Health check
curl http://localhost:8080/api/v1/billing/health

# Test with authentication
curl -H "Authorization: Bearer {token}" \
     http://localhost:8080/api/v1/billing/cost/current?userId={userId}
```

## 🏗️ Project Structure

```
src/main/java/com/mcpgateway/
├── controller/          # REST API controllers
│   ├── auth/           # Authentication endpoints
│   ├── billing/        # Billing and usage APIs
│   └── transport/      # Session transport endpoints
├── service/            # Business logic
│   ├── SessionService  # Session management
│   ├── McpServerConnectionService  # Upstream connections
│   └── UsageBillingService  # Billing logic
├── domain/             # JPA entities
├── dto/                # Data transfer objects
├── repository/         # Data access layer
└── config/             # Configuration classes

src/main/resources/
├── db/migration/       # Database migrations
└── application.yml     # Application configuration
```

## 🔀 Transport Protocol Selection Guide

| Transport | Use Case | Pros | Cons |
|-----------|----------|------|------|
| **Streamable HTTP** | Remote MCP servers (recommended for 2025) | Standard HTTP, firewall-friendly, simple | HTTP overhead |
| **SSE** | Remote servers (legacy support) | Real-time, HTTP-based, widely supported | One-way server push |
| **WebSocket** | Real-time bidirectional | Full-duplex, low latency | More complex, firewall issues |
| **STDIO** | Local MCP processes/tools | Direct process communication, fast | Local only, process management needed |

### When to Use Each Transport:

- **Use Streamable HTTP**: For production remote MCP servers (MCP standard 2025)
- **Use SSE**: For legacy MCP servers or when backward compatibility is needed
- **Use WebSocket**: When you need true bidirectional real-time communication
- **Use STDIO**: For local MCP tools, CLI utilities, or subprocess communication

## 📊 Monitoring & Observability

### Start Monitoring Stack
```bash
# Start Prometheus, Grafana, AlertManager, and Zipkin
docker-compose -f docker-compose-monitoring.yml up -d

# Access dashboards
# Grafana: http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
# Zipkin: http://localhost:9411
# AlertManager: http://localhost:9093
```

### Available Grafana Dashboards
- **System Health**: CPU, memory, JVM metrics, error rates
- **Business Metrics**: Payments, revenue, subscriptions, user activity
- **Circuit Breakers**: Resilience patterns and fault tolerance monitoring

### Key Metrics
```promql
# Request rate
rate(http_server_requests_seconds_count[1m])

# Response time (p95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[1m])

# Circuit breaker state
resilience4j_circuitbreaker_state
```

## 🚀 Deployment

### Quick Deploy to Staging
```bash
# One-command deployment
./scripts/deploy.sh staging

# With options
./scripts/deploy.sh staging --skip-tests --dry-run
```

### Production Deployment
```bash
# Pre-deployment checks
./scripts/migrate-db.sh production --dry-run
./scripts/deploy.sh production --dry-run

# Deploy
./scripts/deploy.sh production

# Verify health
./scripts/health-check.sh production
```

### Emergency Rollback
```bash
# Rollback to previous version
./scripts/rollback.sh production

# Rollback to specific revision
./scripts/rollback.sh production --revision 3
```

### Database Operations
```bash
# Backup
./scripts/backup.sh production

# Migration
./scripts/migrate-db.sh production

# Restore (if needed)
./scripts/restore.sh production backups/production/backup_20240115.sql.gz
```

## 🔧 Troubleshooting

Common issues and solutions:

| Issue | Quick Fix |
|-------|-----------|
| Application won't start | Check `DATABASE_URL` and `JWT_SECRET_KEY` env vars |
| Slow response times | Review database indexes, enable caching |
| Circuit breaker open | Check upstream service health, wait for auto-recovery |
| Deployment failed | Run `kubectl describe pod <name>` for details |
| High memory usage | Generate heap dump: `jmap -dump:live,format=b,file=heap.hprof <PID>` |

**Full Guide:** See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)

## ⚡ Performance Testing

### Run Load Tests
```bash
# Gatling (Payment flow)
cd performance-tests/gatling
mvn gatling:test

# JMeter (API load test)
jmeter -n -t performance-tests/jmeter/api-load-test.jmx \
  -l results/test.jtl \
  -e -o results/report
```

### Performance Targets
- **Response Time (p95):** < 500ms
- **Throughput:** > 200 req/s
- **Error Rate:** < 1%
- **Concurrent Users:** 100+ (staging), 1000+ (production)

## 🔮 Features Ready for Extension

- **OAuth2 Integration**: Structure in place
- **Microservices**: Billing system ready for separation
- **Real-time Notifications**: Event-driven architecture
- **Multi-tenancy**: Database schema supports tenant isolation
- **GraphQL API**: RESTful foundation ready for GraphQL layer
- **Webhook Retry**: Event-driven system can add retry logic

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Built with**: Spring Boot 3.2.3, Java 21, H2 Database, JWT Authentication, Reactive WebClient 