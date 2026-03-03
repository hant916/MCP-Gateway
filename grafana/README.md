# Grafana Dashboards for MCP Gateway

This directory contains pre-configured Grafana dashboards for monitoring MCP Gateway.

## 📊 Available Dashboards

### 1. System Health Dashboard (`system-health.json`)
Monitors overall system health and infrastructure metrics:
- **Application Status**: UP/DOWN status
- **CPU Usage**: Real-time CPU utilization gauge
- **Memory Usage**: JVM heap memory usage
- **Database Connections**: Active connection count
- **Request Rate**: HTTP requests per second
- **Response Time**: p95 latency metrics
- **Error Rates**: 4xx and 5xx error percentages
- **JVM Threads**: Live and daemon thread counts
- **Garbage Collection**: GC pause metrics

**Use Case:** Real-time system health monitoring, capacity planning

---

### 2. Business Metrics Dashboard (`business-metrics.json`)
Tracks key business KPIs and revenue metrics:
- **Payments**: Hourly payment count
- **Payment Failure Rate**: Percentage of failed payments
- **Total Revenue**: Sum of all payment amounts
- **New Subscriptions**: 24-hour subscription count
- **Payment Status Over Time**: Success/failure trends
- **Revenue Over Time**: Revenue trends
- **Subscription Status**: Active/trial/cancelled breakdown
- **Tool Execution**: Most used tools
- **Top Users**: Top 10 users by API calls
- **Payments by Currency**: Distribution of payment currencies

**Use Case:** Business analytics, revenue tracking, user engagement

---

### 3. Circuit Breaker Dashboard (`circuit-breaker.json`)
Monitors resilience patterns and fault tolerance:
- **Circuit Breaker States**: OPEN/CLOSED/HALF_OPEN status
- **Failure Rates**: Real-time failure rate gauges
- **Call Metrics**: Successful/failed/rejected calls
- **Failure Rate Trends**: Historical failure rate graph
- **Retry Metrics**: Success/failure with/without retries

**Monitored Circuit Breakers:**
- `mcpServer`: Upstream MCP server connections
- `stripePayment`: Payment processing

**Use Case:** Fault detection, resilience monitoring, debugging outages

---

## 🚀 Quick Start

### 1. Start Monitoring Stack

```bash
# Start Prometheus, Grafana, AlertManager, and Zipkin
docker-compose -f docker-compose-monitoring.yml up -d
```

### 2. Access Grafana

- **URL**: http://localhost:3000
- **Default Username**: `admin`
- **Default Password**: `admin`
- **Change password on first login**

### 3. View Dashboards

Dashboards are automatically provisioned and available in the "MCP Gateway" folder.

### 4. Verify Data

Ensure the MCP Gateway application is running and exposing metrics at:
- http://localhost:8080/actuator/prometheus

---

## 📝 Dashboard Configuration

### Auto-Provisioning

Dashboards are automatically loaded from:
```
grafana/
├── provisioning/
│   ├── dashboards.yml      # Dashboard provider config
│   └── datasources.yml     # Prometheus datasource config
└── dashboards/
    ├── system-health.json
    ├── business-metrics.json
    └── circuit-breaker.json
```

### Manual Import (Alternative)

If auto-provisioning doesn't work:

1. Open Grafana UI
2. Go to **Dashboards** → **Import**
3. Upload JSON file or paste JSON content
4. Select **Prometheus** as datasource
5. Click **Import**

---

## 🔔 Alerts Integration

Dashboards work with Prometheus alerts defined in `prometheus/alerts.yml`:

- Critical alerts trigger when thresholds are exceeded
- Alerts sent to AlertManager for routing
- AlertManager routes to Slack, Email, or Webhooks

**Configure AlertManager:**
Edit `prometheus/alertmanager.yml` to set:
- Slack webhook URL
- Email SMTP settings
- Webhook endpoints

---

## 📈 Metrics Glossary

### System Metrics
- `up`: Service availability (1 = up, 0 = down)
- `process_cpu_usage`: CPU utilization percentage
- `jvm_memory_used_bytes`: JVM memory usage
- `hikaricp_connections_active`: Database connection pool
- `http_server_requests_seconds_count`: HTTP request rate
- `http_server_requests_seconds_bucket`: Response time histogram

### Business Metrics
- `payment_total`: Total payment count
- `payment_failure_total`: Failed payment count
- `payment_amount_sum`: Total revenue
- `subscription_created_total`: New subscriptions
- `active_subscriptions`: Active subscription count
- `tool_execution_total`: Tool usage count
- `api_calls_total`: API call count per user

### Resilience Metrics
- `resilience4j_circuitbreaker_state`: Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `resilience4j_circuitbreaker_failure_rate`: Failure rate percentage
- `resilience4j_circuitbreaker_calls_seconds_count`: Call counts by result
- `resilience4j_circuitbreaker_not_permitted_calls_total`: Rejected calls
- `resilience4j_retry_calls_total`: Retry statistics

---

## 🎨 Customization

### Adding New Panels

1. Edit dashboard JSON file
2. Add new panel in `panels` array
3. Configure:
   - `gridPos`: Panel position and size
   - `targets`: Prometheus queries
   - `fieldConfig`: Thresholds, units, colors
   - `options`: Display options

### Example Panel:

```json
{
  "id": 100,
  "title": "My Custom Metric",
  "type": "stat",
  "gridPos": {"h": 4, "w": 6, "x": 0, "y": 0},
  "targets": [
    {
      "expr": "my_custom_metric",
      "refId": "A"
    }
  ]
}
```

### Creating New Dashboards

1. Create new JSON file in `grafana/dashboards/`
2. Use existing dashboards as templates
3. Update `uid` and `title` fields
4. Restart Grafana or wait for auto-reload (10s interval)

---

## 🔍 Troubleshooting

### No Data in Dashboards

**Check:**
1. MCP Gateway is running: `curl http://localhost:8080/actuator/health`
2. Metrics are exposed: `curl http://localhost:8080/actuator/prometheus`
3. Prometheus is scraping: http://localhost:9090/targets
4. Prometheus datasource is configured in Grafana

### Dashboards Not Loading

**Check:**
1. JSON syntax is valid
2. Dashboard files are in `grafana/dashboards/`
3. Grafana logs: `docker logs mcp-grafana`
4. Provisioning config: `grafana/provisioning/dashboards.yml`

### Incorrect Metrics

**Check:**
1. Prometheus query syntax
2. Metric names match actual exports
3. Label filters are correct
4. Time range is appropriate

---

## 📚 Resources

- [Grafana Documentation](https://grafana.com/docs/)
- [Prometheus Query Language (PromQL)](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/best-practices/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Resilience4j Metrics](https://resilience4j.readme.io/docs/micrometer)

---

## 🎯 Next Steps

1. **Configure Alerts**: Update `prometheus/alertmanager.yml` with real endpoints
2. **Add Custom Metrics**: Instrument your code with Micrometer
3. **Create Team Dashboards**: Build role-specific views (DevOps, Business, Support)
4. **Set Up Retention**: Configure Prometheus retention policy
5. **Enable Auth**: Secure Grafana with LDAP/OAuth2

---

**Need Help?** Check the troubleshooting guide or contact the DevOps team.
