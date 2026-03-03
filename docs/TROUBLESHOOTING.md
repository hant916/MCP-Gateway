# 🔧 Troubleshooting Guide

Comprehensive troubleshooting guide for MCP Gateway.

## 📑 Quick Navigation

- [Application Won't Start](#application-wont-start)
- [Database Connection Issues](#database-connection-issues)
- [High CPU/Memory Usage](#high-cpumemory-usage)
- [Slow Response Times](#slow-response-times)
- [Payment Processing Failures](#payment-processing-failures)
- [Circuit Breaker Issues](#circuit-breaker-issues)
- [Deployment Failures](#deployment-failures)
- [Health Check Failures](#health-check-failures)
- [Distributed Tracing Issues](#distributed-tracing-issues)
- [Kubernetes Pod Issues](#kubernetes-pod-issues)

---

## Application Won't Start

### Symptom
Application fails to start or crashes immediately after startup.

### Common Causes & Solutions

#### 1. Port Already in Use
**Error:**
```
java.net.BindException: Address already in use
```

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080
kill -9 <PID>

# Or change port
export SERVER_PORT=8081
mvn spring-boot:run
```

#### 2. Database Connection Failed
**Error:**
```
org.postgresql.util.PSQLException: Connection refused
```

**Solution:**
```bash
# Check database is running
docker ps | grep postgres

# Verify connection
psql -h localhost -U mcpgateway -d mcpgateway

# Check environment variables
echo $DATABASE_URL
echo $DATABASE_USERNAME
echo $DATABASE_PASSWORD
```

#### 3. Missing Configuration
**Error:**
```
java.lang.IllegalArgumentException: JWT_SECRET_KEY is not configured
```

**Solution:**
```bash
# Set required environment variables
export JWT_SECRET_KEY=$(openssl rand -hex 32)
export DATABASE_PASSWORD=your_password
export STRIPE_API_KEY=your_stripe_key

# Or use .env file
cp .env.example .env
# Edit .env with your values
```

#### 4. Flyway Migration Failed
**Error:**
```
org.flywaydb.core.api.FlywayException: Validate failed
```

**Solution:**
```bash
# Check migration history
./scripts/migrate-db.sh staging --validate

# Repair if needed
./scripts/migrate-db.sh staging --repair

# Re-run migration
./scripts/migrate-db.sh staging
```

---

## Database Connection Issues

### Symptom
"Unable to acquire JDBC Connection" or connection timeouts.

### Diagnostics

```bash
# Check database status
kubectl exec -it deployment/mcp-gateway -n mcp-gateway-production -- \
  curl http://localhost:8080/actuator/health | jq '.components.db'

# View connection pool metrics
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.connections.idle
```

### Solutions

#### 1. Connection Pool Exhausted
**Symptoms:**
- Slow queries
- Timeouts
- High wait times

**Solution:**
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Increase from 10
      minimum-idle: 5
      connection-timeout: 30000
```

#### 2. Database Replica Down
**Error:**
```
Connection to replica failed
```

**Solution:**
```bash
# Check replica health
kubectl get pods -n database

# Disable read-write splitting temporarily
export READ_WRITE_SPLITTING_ENABLED=false

# Or route all to master
kubectl set env deployment/mcp-gateway \
  READ_WRITE_SPLITTING_ENABLED=false \
  -n mcp-gateway-production
```

#### 3. Network Issues
**Solution:**
```bash
# Test connectivity
kubectl exec -it deployment/mcp-gateway -- \
  nc -zv postgres-service 5432

# Check DNS
kubectl exec -it deployment/mcp-gateway -- \
  nslookup postgres-service

# View network policies
kubectl get networkpolicies
```

---

## High CPU/Memory Usage

### Diagnostics

```bash
# Check resource usage
kubectl top pods -n mcp-gateway-production

# View JVM metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/process.cpu.usage

# Generate thread dump
jstack <PID> > thread-dump.txt

# Generate heap dump
jmap -dump:live,format=b,file=heapdump.hprof <PID>
```

### Solutions

#### 1. Memory Leak
**Symptoms:**
- Memory usage keeps increasing
- Frequent GC pauses
- OutOfMemoryError

**Solution:**
```bash
# Analyze heap dump
jhat heapdump.hprof
# Or use Eclipse MAT

# Common causes:
# - Unbounded caches
# - Resource leaks (unclosed connections)
# - Large objects in session

# Fix cache configuration
spring.cache.caffeine.spec: maximumSize=1000,expireAfterWrite=5m
```

#### 2. High GC Activity
**Solution:**
```bash
# Tune GC settings
export JAVA_OPTS="-XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:G1HeapRegionSize=16m"

# Increase heap size
export JAVA_OPTS="-Xms2g -Xmx4g"
```

#### 3. CPU Spike
**Solution:**
```bash
# Profile application
java -agentlib:hprof=cpu=samples MyApp

# Check for:
# - Infinite loops
# - Heavy computation in request threads
# - Inefficient algorithms

# Scale horizontally
kubectl scale deployment/mcp-gateway --replicas=5
```

---

## Slow Response Times

### Diagnostics

```bash
# Check response times
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/api/v1/health

# View slow queries
SELECT query, calls, mean_exec_time, max_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

# Check distributed traces
# Access Zipkin: http://localhost:9411
```

### Solutions

#### 1. Slow Database Queries
**Solution:**
```sql
-- Add missing indexes
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- Optimize N+1 queries
@EntityGraph(attributePaths = {"tool", "subscription"})
List<Payment> findByUserId(UUID userId);

-- Enable query caching
@Cacheable(value = "tools", key = "#id")
public McpToolDTO getTool(UUID id);
```

#### 2. External Service Latency
**Solution:**
```yaml
# Reduce timeout
resilience4j:
  timelimiter:
    instances:
      mcpServer:
        timeoutDuration: 3s  # Reduce from 30s

# Add caching
@Cacheable(value = "upstream-tools", ttl = "5m")
public List<Tool> listTools();
```

#### 3. Inefficient Code
**Solution:**
```bash
# Profile with JProfiler or YourKit
# Look for:
# - Unnecessary object creation
# - Synchronization bottlenecks
# - Inefficient data structures

# Use parallel processing
paymentStream.parallel()
  .filter(p -> p.isCompleted())
  .collect(Collectors.toList());
```

---

## Payment Processing Failures

### Symptom
Payments fail or get stuck in PENDING state.

### Diagnostics

```bash
# Check Stripe connectivity
curl https://api.stripe.com/v1/charges \
  -u sk_test_...:

# View payment errors in logs
kubectl logs deployment/mcp-gateway | grep "Payment.*ERROR"

# Check circuit breaker status
curl http://localhost:8080/actuator/health | jq '.components.circuitBreakers'
```

### Solutions

#### 1. Stripe API Down
**Symptom:** Circuit breaker OPEN for stripePayment

**Solution:**
```bash
# Check Stripe status
curl https://status.stripe.com/

# Use fallback
# Circuit breaker automatically prevents further calls

# Monitor recovery
watch -n 5 'curl -s http://localhost:8080/actuator/health | jq .components.circuitBreakers'
```

#### 2. Webhook Not Received
**Solution:**
```bash
# Check webhook endpoint
curl -X POST http://your-app.com/api/v1/webhooks/stripe \
  -H "Content-Type: application/json" \
  -d @test-webhook.json

# Verify webhook secret
echo $STRIPE_WEBHOOK_SECRET

# Check Stripe webhook logs
# https://dashboard.stripe.com/webhooks
```

#### 3. Payment Stuck in PENDING
**Solution:**
```sql
-- Find stuck payments
SELECT * FROM payments
WHERE status = 'PENDING'
  AND created_at < NOW() - INTERVAL '1 hour';

-- Manual reconciliation
UPDATE payments
SET status = 'FAILED',
    failure_reason = 'Timeout - manual intervention'
WHERE id = 'xxx';
```

---

## Circuit Breaker Issues

### Symptom
Circuit breaker frequently opening, causing service degradation.

### Diagnostics

```bash
# Check circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.failure.rate

# View Grafana dashboard
# http://localhost:3000/d/mcp-circuit-breaker
```

### Solutions

#### 1. Circuit Breaker Too Sensitive
**Solution:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      mcpServer:
        failureRateThreshold: 70  # Increase from 50
        slidingWindowSize: 50     # Increase from 20
```

#### 2. Upstream Service Unstable
**Solution:**
```bash
# Add retry with backoff
resilience4j:
  retry:
    instances:
      mcpServer:
        maxAttempts: 3
        waitDuration: 1s
        retryExceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException

# Implement bulkhead
resilience4j:
  bulkhead:
    instances:
      mcpServer:
        maxConcurrentCalls: 10
```

#### 3. Need Manual Reset
**Solution:**
```bash
# Force circuit breaker to CLOSED state
curl -X POST http://localhost:8080/actuator/circuitbreakers/mcpServer/state \
  -H "Content-Type: application/json" \
  -d '{"state": "CLOSED"}'
```

---

## Deployment Failures

### Symptom
Kubernetes deployment fails or pods crash loop.

### Diagnostics

```bash
# Check deployment status
kubectl get deployments -n mcp-gateway-production
kubectl describe deployment mcp-gateway -n mcp-gateway-production

# View pod logs
kubectl logs -l app=mcp-gateway -n mcp-gateway-production --tail=100

# Check events
kubectl get events -n mcp-gateway-production --sort-by='.lastTimestamp'
```

### Solutions

#### 1. Image Pull Error
**Error:**
```
Failed to pull image "ghcr.io/org/mcp-gateway:latest"
```

**Solution:**
```bash
# Check image exists
docker pull ghcr.io/org/mcp-gateway:latest

# Verify image pull secret
kubectl get secrets -n mcp-gateway-production
kubectl describe secret ghcr-credentials

# Recreate secret
kubectl create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=$GITHUB_USERNAME \
  --docker-password=$GITHUB_TOKEN \
  -n mcp-gateway-production
```

#### 2. Liveness Probe Failing
**Error:**
```
Liveness probe failed: HTTP probe failed
```

**Solution:**
```yaml
# Increase timeout
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 90  # Increase
  timeoutSeconds: 5        # Increase
  failureThreshold: 5      # Increase
```

#### 3. Insufficient Resources
**Error:**
```
0/3 nodes are available: insufficient cpu
```

**Solution:**
```yaml
# Reduce resource requests
resources:
  requests:
    memory: "256Mi"  # Reduce from 512Mi
    cpu: "250m"      # Reduce from 500m
  limits:
    memory: "512Mi"
    cpu: "500m"

# Or scale cluster
kubectl scale nodes --replicas=5
```

---

## Health Check Failures

### Symptom
`/actuator/health` returns DOWN status.

### Diagnostics

```bash
# Detailed health check
curl http://localhost:8080/actuator/health | jq

# Individual components
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/db
```

### Solutions

#### 1. Database Component DOWN
**Solution:**
```bash
# Check database connection
psql -h $DB_HOST -U $DB_USER -d mcpgateway

# Restart database
kubectl rollout restart statefulset/postgres

# Temporary disable health check
management:
  health:
    db:
      enabled: false
```

#### 2. Disk Space Component DOWN
**Solution:**
```bash
# Check disk usage
df -h

# Clean up logs
find /var/log -name "*.log" -mtime +7 -delete

# Increase disk threshold
management:
  health:
    diskspace:
      threshold: 1GB  # Reduce from 10GB
```

---

## Distributed Tracing Issues

### Symptom
Traces not appearing in Zipkin UI.

### Solutions

```bash
# Check Zipkin is running
curl http://localhost:9411/health

# Verify sampling rate
management.tracing.sampling.probability: 1.0  # 100% for debugging

# Check trace IDs in logs
grep "traceId" logs/spring.log

# Manual trace
curl -H "X-B3-TraceId: 0123456789abcdef" \
     http://localhost:8080/api/v1/health
```

---

## Kubernetes Pod Issues

### CrashLoopBackOff
**Solution:**
```bash
# View crashlogs
kubectl logs <pod-name> --previous

# Check init containers
kubectl describe pod <pod-name>

# Common causes:
# - Missing environment variables
# - Failed healthchecks
# - Out of memory
```

### Pending State
**Solution:**
```bash
# Check why pending
kubectl describe pod <pod-name>

# Common causes:
# - No nodes available
# - Resource constraints
# - PVC not bound
```

---

## 🆘 Emergency Procedures

### Production Down

1. **Check basic connectivity**
```bash
curl https://api.mcpgateway.com/actuator/health
```

2. **View error logs**
```bash
kubectl logs -l app=mcp-gateway -n mcp-gateway-production --tail=500
```

3. **Check alerts**
```bash
# AlertManager
curl http://localhost:9093/api/v2/alerts
```

4. **Rollback if needed**
```bash
./scripts/rollback.sh production --force
```

5. **Scale if needed**
```bash
kubectl scale deployment/mcp-gateway --replicas=10
```

---

## 📞 Getting Help

### Logs to Collect

```bash
# Application logs
kubectl logs deployment/mcp-gateway -n mcp-gateway-production > app.log

# Events
kubectl get events -n mcp-gateway-production > events.txt

# Resource usage
kubectl top pods -n mcp-gateway-production > resources.txt

# Health status
curl http://localhost:8080/actuator/health > health.json

# Thread dump
jstack <PID> > threads.txt

# Heap dump
jmap -dump:format=b,file=heap.hprof <PID>
```

### Support Contacts

- **DevOps Team:** devops@example.com
- **On-Call:** Check PagerDuty
- **Slack:** #mcp-gateway-support

---

## 📚 Additional Resources

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kubernetes Debugging Guide](https://kubernetes.io/docs/tasks/debug/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [PostgreSQL Performance Tuning](https://wiki.postgresql.org/wiki/Performance_Optimization)

---

**Remember:** Always check Grafana dashboards and Prometheus alerts before deep debugging!
