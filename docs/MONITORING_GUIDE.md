# MCP Gateway Monitoring Guide

Complete guide for monitoring MCP Gateway using Prometheus and Grafana.

## Table of Contents
- [Overview](#overview)
- [Prometheus Setup](#prometheus-setup)
- [Grafana Setup](#grafana-setup)
- [Available Metrics](#available-metrics)
- [Alerting Rules](#alerting-rules)
- [Dashboards](#dashboards)

---

## Overview

MCP Gateway exposes comprehensive metrics via Prometheus for monitoring:

- **Tool Execution Metrics** - Success/failure rates, duration, throughput
- **Session Metrics** - Active sessions, session duration by transport
- **API Metrics** - Request rates, latency, error rates
- **Business Metrics** - Subscriptions, payments, revenue
- **System Metrics** - JVM, database, Redis connections
- **Webhook Metrics** - Delivery success/failure rates

**Metrics Endpoint:** `http://localhost:8080/actuator/prometheus`

---

## Prometheus Setup

### 1. Install Prometheus

**Using Docker:**
```bash
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

**Using Docker Compose:**
```yaml
version: '3.8'

services:
  mcp-gateway:
    image: mcp-gateway:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on:
      - prometheus

volumes:
  prometheus-data:
  grafana-data:
```

### 2. Configure Prometheus

**prometheus.yml:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'mcp-gateway-production'
    environment: 'prod'

# Alertmanager configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - 'alertmanager:9093'

# Load rules once and periodically evaluate them
rule_files:
  - "alerts.yml"

# Scrape configurations
scrape_configs:
  - job_name: 'mcp-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['mcp-gateway:8080']
        labels:
          application: 'mcp-gateway'
          instance: 'mcp-gateway-1'

  - job_name: 'mcp-gateway-cluster'
    metrics_path: '/actuator/prometheus'
    dns_sd_configs:
      - names:
          - 'mcp-gateway.service.consul'
        type: 'A'
        port: 8080
```

### 3. Create Alert Rules

**alerts.yml:**
```yaml
groups:
  - name: mcp_gateway_alerts
    interval: 30s
    rules:
      # High error rate
      - alert: HighErrorRate
        expr: |
          rate(mcp_tool_executions_total{status="failure"}[5m]) /
          rate(mcp_tool_executions_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Tool execution error rate is {{ $value | humanizePercentage }} (threshold: 5%)"

      # High API latency
      - alert: HighAPILatency
        expr: |
          histogram_quantile(0.95,
            rate(mcp_api_request_duration_bucket[5m])
          ) > 1.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High API latency detected"
          description: "95th percentile API latency is {{ $value }}s (threshold: 1s)"

      # Payment failures
      - alert: HighPaymentFailureRate
        expr: |
          rate(mcp_payments_total{status="failed"}[10m]) /
          rate(mcp_payments_total[10m]) > 0.10
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "High payment failure rate"
          description: "Payment failure rate is {{ $value | humanizePercentage }} (threshold: 10%)"

      # Webhook delivery failures
      - alert: WebhookDeliveryFailures
        expr: |
          rate(mcp_webhooks_delivered_total{status="failure"}[5m]) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Webhook delivery failures detected"
          description: "Webhook failure rate: {{ $value }} failures/sec"

      # Rate limit exceeded
      - alert: RateLimitExceeded
        expr: rate(mcp_rate_limit_exceeded_total[5m]) > 10
        for: 5m
        labels:
          severity: info
        annotations:
          summary: "Rate limit frequently exceeded"
          description: "Rate limit exceeded {{ $value }} times/sec"

      # Database connection pool exhaustion
      - alert: DatabasePoolExhausted
        expr: |
          hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"
          description: "Using {{ $value | humanizePercentage }} of connection pool"

      # Low quota remaining
      - alert: LowQuotaRemaining
        expr: |
          mcp_quota_remaining < 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "User quota running low"
          description: "User {{ $labels.user_id }} has only {{ $value }} quota remaining"
```

---

## Grafana Setup

### 1. Add Prometheus Data Source

1. Open Grafana: http://localhost:3000
2. Login (default: admin/admin)
3. Go to **Configuration** > **Data Sources**
4. Click **Add data source**
5. Select **Prometheus**
6. Configure:
   - **URL:** `http://prometheus:9090`
   - **Access:** Server (default)
7. Click **Save & Test**

### 2. Import Dashboard

Create a new dashboard or import the following JSON configuration.

---

## Available Metrics

### Tool Execution Metrics

```promql
# Tool execution rate by tool
rate(mcp_tool_executions_total[5m])

# Tool execution success rate
rate(mcp_tool_executions_total{status="success"}[5m]) /
rate(mcp_tool_executions_total[5m])

# Average execution duration by tool
rate(mcp_tool_execution_duration_sum[5m]) /
rate(mcp_tool_execution_duration_count[5m])

# 95th percentile execution duration
histogram_quantile(0.95, rate(mcp_tool_execution_duration_bucket[5m]))
```

### Session Metrics

```promql
# Active sessions by transport
mcp_connections_active

# Session creation rate
rate(mcp_sessions_created_total[5m])

# Average session duration
rate(mcp_session_duration_sum[5m]) /
rate(mcp_session_duration_count[5m])
```

### API Metrics

```promql
# Request rate by endpoint
rate(mcp_api_requests_total[5m])

# Error rate
rate(mcp_api_requests_total{status=~"5.."}[5m])

# 95th percentile latency
histogram_quantile(0.95, rate(mcp_api_request_duration_bucket[5m]))

# Request rate by status code
sum(rate(mcp_api_requests_total[5m])) by (status)
```

### Business Metrics

```promql
# Subscription actions rate
rate(mcp_marketplace_subscriptions_total[5m])

# Payment success rate
rate(mcp_payments_total{status="succeeded"}[1h]) /
rate(mcp_payments_total[1h])

# Total revenue (last 24h)
sum(increase(mcp_payments_amount_sum{status="succeeded"}[24h]))

# Review submission rate
rate(mcp_marketplace_reviews_total[5m])
```

### System Metrics

```promql
# JVM memory usage
jvm_memory_used_bytes / jvm_memory_max_bytes

# Garbage collection time
rate(jvm_gc_pause_seconds_sum[5m])

# Thread count
jvm_threads_live_threads

# Database connections
hikaricp_connections_active

# Redis connection status
redis_connected_clients
```

### Webhook Metrics

```promql
# Webhook delivery rate
rate(mcp_webhooks_delivered_total[5m])

# Webhook success rate
rate(mcp_webhooks_delivered_total{status="success"}[5m]) /
rate(mcp_webhooks_delivered_total[5m])

# Average delivery duration
rate(mcp_webhook_delivery_duration_sum[5m]) /
rate(mcp_webhook_delivery_duration_count[5m])
```

---

## Sample Grafana Dashboard Panels

### 1. Tool Execution Overview

**Panel: Tool Execution Rate**
```promql
sum(rate(mcp_tool_executions_total[5m])) by (tool, status)
```

**Panel: Success Rate**
```promql
sum(rate(mcp_tool_executions_total{status="success"}[5m])) /
sum(rate(mcp_tool_executions_total[5m]))
```

**Panel: Average Duration**
```promql
histogram_quantile(0.50, sum(rate(mcp_tool_execution_duration_bucket[5m])) by (le, tool))
```

### 2. API Performance

**Panel: Request Rate**
```promql
sum(rate(mcp_api_requests_total[5m])) by (endpoint)
```

**Panel: Latency Percentiles**
```promql
histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
```

**Panel: Error Rate by Endpoint**
```promql
sum(rate(mcp_api_requests_total{status=~"5.."}[5m])) by (endpoint)
```

### 3. Business Metrics

**Panel: Active Subscriptions**
```promql
count(mcp_quota_remaining > 0)
```

**Panel: Revenue (24h)**
```promql
sum(increase(mcp_payments_amount_sum{status="succeeded"}[24h]))
```

**Panel: Subscription Churn Rate**
```promql
rate(mcp_marketplace_subscriptions_total{action="unsubscribe"}[1h]) /
rate(mcp_marketplace_subscriptions_total{action="subscribe"}[1h])
```

### 4. System Health

**Panel: JVM Heap Usage**
```promql
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

**Panel: Database Connection Pool**
```promql
hikaricp_connections_active
hikaricp_connections_idle
hikaricp_connections_max
```

**Panel: Rate Limit Status**
```promql
rate(mcp_rate_limit_exceeded_total[5m])
rate(mcp_rate_limit_allowed_total[5m])
```

---

## Useful Queries

### Top 10 Most Used Tools
```promql
topk(10, sum(rate(mcp_tool_executions_total[24h])) by (tool))
```

### Users Exceeding Quota
```promql
count(mcp_quota_remaining{user_id!=""} <= 0) by (user_id)
```

### Payment Conversion Funnel
```promql
# Payment intent created
sum(rate(mcp_payments_total{status="pending"}[1h]))

# Payments succeeded
sum(rate(mcp_payments_total{status="succeeded"}[1h]))

# Conversion rate
sum(rate(mcp_payments_total{status="succeeded"}[1h])) /
sum(rate(mcp_payments_total{status="pending"}[1h]))
```

### Webhook Health
```promql
# Webhooks with high failure rate (>10%)
(
  rate(mcp_webhooks_delivered_total{status="failure"}[10m]) /
  rate(mcp_webhooks_delivered_total[10m])
) > 0.1
```

---

## Best Practices

1. **Set up alerts** for critical metrics (error rates, payment failures)
2. **Monitor trends** over time to identify patterns
3. **Use dashboards** for real-time monitoring
4. **Set SLOs** (Service Level Objectives) for key metrics
5. **Review metrics** regularly to optimize performance
6. **Enable recording rules** for frequently-used expensive queries
7. **Retain data** for at least 15 days for troubleshooting
8. **Export dashboards** as code for version control

---

## Troubleshooting

### Metrics not showing up

1. Check actuator endpoint:
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```

2. Verify Prometheus is scraping:
   ```bash
   curl http://localhost:9090/api/v1/targets
   ```

3. Check application logs for errors

### High memory usage

Monitor JVM metrics:
```promql
jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9
```

Increase heap size:
```bash
java -Xmx2g -Xms1g -jar mcp-gateway.jar
```

### Slow queries

Enable SQL logging:
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

Monitor query performance:
```promql
rate(mcp_database_query_duration_sum[5m]) /
rate(mcp_database_query_duration_count[5m])
```

---

## Additional Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Tutorials](https://grafana.com/tutorials/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
