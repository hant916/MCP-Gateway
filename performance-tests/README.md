# Performance Testing

Performance and load testing for MCP Gateway.

## 🎯 Test Scenarios

### 1. Payment Flow (Gatling)
**File:** `gatling/PaymentFlowSimulation.scala`

**Scenario:**
1. User registration
2. Authentication
3. Create payment intent
4. View payment history

**Load Profile:**
- Ramp up: 1 → 100 users over 5 minutes
- Duration: 10 minutes
- Target: 100 concurrent users

**Assertions:**
- Max response time < 3s
- Mean response time < 1s
- Success rate > 95%

**Run:**
```bash
cd performance-tests/gatling
mvn gatling:test -Dgatling.simulationClass=simulations.PaymentFlowSimulation
```

---

### 2. API Load Test (JMeter)
**File:** `jmeter/api-load-test.jmx`

**Scenario:**
- User registration
- Get payment history
- Various API endpoints

**Load Profile:**
- Threads: 1000 concurrent users
- Ramp-up: 5 minutes
- Duration: 10 minutes

**Run:**
```bash
jmeter -n -t jmeter/api-load-test.jmx \
  -l results/api-load-test-results.jtl \
  -e -o results/api-load-test-report
```

---

## 📊 Test Results Analysis

### Expected Performance

#### Under Normal Load (100 concurrent users)
- **Response Time (p95):** < 500ms
- **Response Time (p99):** < 1000ms
- **Throughput:** > 200 req/s
- **Error Rate:** < 1%
- **CPU Usage:** < 70%
- **Memory Usage:** < 80%

#### Under Peak Load (1000 concurrent users)
- **Response Time (p95):** < 2000ms
- **Response Time (p99):** < 3000ms
- **Throughput:** > 1000 req/s
- **Error Rate:** < 5%
- **CPU Usage:** < 85%
- **Memory Usage:** < 90%

---

## 🚀 Quick Start

### Prerequisites

**Gatling:**
```bash
# Install via Maven (included in project)
mvn clean install
```

**JMeter:**
```bash
# Download and install
wget https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.5.tgz
tar -xzf apache-jmeter-5.5.tgz
export PATH=$PATH:$PWD/apache-jmeter-5.5/bin
```

---

### Running Tests

#### 1. Start Application
```bash
# Start with production profile
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Or use Docker
docker-compose up -d
```

#### 2. Run Gatling Tests
```bash
cd performance-tests/gatling
mvn gatling:test

# View reports
open target/gatling/*/index.html
```

#### 3. Run JMeter Tests
```bash
cd performance-tests

# GUI mode (for test development)
jmeter -t jmeter/api-load-test.jmx

# CLI mode (for actual testing)
jmeter -n -t jmeter/api-load-test.jmx \
  -Jthreads=100 \
  -Jramp_time=60 \
  -Jduration=300 \
  -l results/test-$(date +%Y%m%d_%H%M%S).jtl \
  -e -o results/report-$(date +%Y%m%d_%H%M%S)
```

---

## 📈 Monitoring During Tests

### Grafana Dashboards
Monitor in real-time:
- **System Health:** http://localhost:3000/d/mcp-system-health
- **Performance:** Response times, throughput, error rates
- **Resources:** CPU, memory, database connections

### Prometheus Queries
```promql
# Request rate
rate(http_server_requests_seconds_count[1m])

# Response time p95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[1m])
```

### Application Logs
```bash
# Tail logs during test
kubectl logs -f deployment/mcp-gateway -n mcp-gateway-staging

# Or local
tail -f logs/spring.log
```

---

## 🔍 Performance Tuning

### JVM Options
Add to `JAVA_OPTS`:
```bash
-Xms2g -Xmx4g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:ParallelGCThreads=4 \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

### Database Connection Pool
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Cache Configuration
```yaml
spring:
  cache:
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=15m,recordStats
```

---

## 📝 Test Scenarios

### Scenario 1: Steady State Load
- **Users:** 100
- **Duration:** 30 minutes
- **Goal:** Verify stability under constant load

### Scenario 2: Spike Test
- **Users:** 0 → 500 → 0
- **Duration:** Spike for 5 minutes
- **Goal:** Test auto-scaling and recovery

### Scenario 3: Stress Test
- **Users:** Ramp up until failure
- **Goal:** Find breaking point

### Scenario 4: Endurance Test
- **Users:** 200
- **Duration:** 4 hours
- **Goal:** Detect memory leaks

---

## 🎯 Performance Benchmarks

| Metric | Target | Good | Needs Improvement |
|--------|--------|------|-------------------|
| Response Time (p95) | < 500ms | < 1000ms | > 1000ms |
| Response Time (p99) | < 1000ms | < 2000ms | > 2000ms |
| Throughput | > 200 req/s | > 100 req/s | < 100 req/s |
| Error Rate | < 1% | < 5% | > 5% |
| CPU Usage | < 70% | < 85% | > 85% |
| Memory Usage | < 80% | < 90% | > 90% |

---

## 🐛 Troubleshooting

### High Response Times
- Check database query performance
- Review slow query logs
- Enable query caching
- Optimize N+1 query problems

### High Error Rates
- Check application logs
- Review circuit breaker status
- Verify database connection pool
- Check for timeout issues

### Memory Issues
- Generate heap dump
- Analyze with VisualVM or MAT
- Check for memory leaks
- Review cache sizes

### CPU Bottlenecks
- Profile with JProfiler or YourKit
- Check GC logs
- Review thread pools
- Optimize hot code paths

---

## 📚 Resources

- [Gatling Documentation](https://gatling.io/docs/current/)
- [JMeter Documentation](https://jmeter.apache.org/usermanual/)
- [Performance Testing Best Practices](https://martinfowler.com/articles/practical-test-pyramid.html#PerformanceTests)

---

**Tip:** Always run performance tests on a production-like environment for accurate results.
