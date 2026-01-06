# Rate Limiting & Quota Management

## Overview

MCP Gateway implements comprehensive rate limiting and quota management to protect against abuse, ensure fair resource allocation, and support the platform's business model.

## Features

- **Multiple Rate Limiting Strategies**: Sliding window, token bucket, fixed window, leaky bucket
- **Distributed Rate Limiting**: Redis-based coordination across gateway instances
- **Subscription Tiers**: FREE, BASIC, PRO, ENTERPRISE with different quotas
- **Flexible Key Types**: Per-user, per-IP, per-tool, global, custom
- **Automatic Header Injection**: Standard rate limit headers in responses
- **Graceful Failure**: Fail-open mode if Redis is unavailable

## Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP Request
       ▼
┌─────────────────────┐
│  @RateLimit         │  ◄─── Annotation on controller method
│  Annotation         │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  RateLimitAspect    │  ◄─── AOP intercepts method call
│  (AOP)              │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  RateLimitService   │  ◄─── Business logic & strategy selection
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  RedisRateLimiter   │  ◄─── Lua scripts for atomic operations
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│      Redis          │  ◄─── Distributed state storage
└─────────────────────┘
```

## Quick Start

### 1. Enable Redis

```yaml
# application.yml
spring.data.redis:
  host: localhost
  port: 6379
  password: your-redis-password
```

### 2. Add Rate Limiting to Controllers

```java
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    @PostMapping("/call")
    @RateLimit(
        limit = 100,                          // 100 requests
        window = 60,                          // per 60 seconds
        windowUnit = ChronoUnit.SECONDS,
        key = "user",                         // per user
        errorMessage = "Too many tool calls"
    )
    public ResponseEntity<?> callTool(@RequestBody ToolCallRequest request) {
        // Your logic here
    }
}
```

### 3. Response Headers

Successful response includes:
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1735689600
```

Rate limit exceeded response (429):
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 45
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1735689600

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Too many tool calls",
  "limit": 100,
  "resetTime": "2026-01-06T10:00:00Z",
  "retryAfterSeconds": 45
}
```

## Rate Limiting Strategies

### 1. Sliding Window (Recommended)

Most accurate rate limiting using a rolling time window.

```java
@RateLimit(
    strategy = RateLimitStrategy.SLIDING_WINDOW,
    limit = 100,
    window = 60,
    windowUnit = ChronoUnit.SECONDS
)
```

**Pros**: Accurate, prevents boundary issues
**Cons**: Slightly more Redis operations
**Use Case**: General API rate limiting

### 2. Token Bucket

Allows burst traffic with token refill.

```java
@RateLimit(
    strategy = RateLimitStrategy.TOKEN_BUCKET,
    limit = 1000,  // bucket capacity
    window = 60,   // refill period
    cost = 10      // tokens per request
)
```

**Pros**: Allows bursts, flexible
**Cons**: More complex
**Use Case**: APIs with variable request costs

### 3. Fixed Window

Simple counter that resets at fixed intervals.

```java
@RateLimit(
    strategy = RateLimitStrategy.FIXED_WINDOW,
    limit = 100,
    window = 60
)
```

**Pros**: Simple, fast
**Cons**: Can allow 2x limit at window boundaries
**Use Case**: Non-critical endpoints

### 4. Leaky Bucket

Smooths traffic flow by processing at constant rate.

```java
@RateLimit(
    strategy = RateLimitStrategy.LEAKY_BUCKET,
    limit = 100,
    window = 60
)
```

**Pros**: Smooth traffic
**Cons**: May queue requests
**Use Case**: Backend protection

## Key Types

### Per-User

Rate limit per authenticated user (default).

```java
@RateLimit(key = "user", limit = 1000, window = 3600)
```

### Per-IP

Rate limit by client IP address.

```java
@RateLimit(key = "ip", limit = 100, window = 60)
```

### Per-User Per-Tool

Rate limit for specific user-tool combination.

```java
@RateLimit(key = "user:tool", limit = 50, window = 60)
```

### Global

Single limit across all users.

```java
@RateLimit(key = "global", limit = 10000, window = 60)
```

### Custom Expression

Use placeholders for dynamic keys.

```java
@RateLimit(key = "user:{userId}:region:{region}", limit = 100, window = 60)
```

## Subscription Quotas

### Tier Configuration

| Tier       | Monthly Quota | Daily Limit | Per-Minute | Concurrent | Priority |
|------------|---------------|-------------|------------|------------|----------|
| **FREE**   | 100           | 10          | 2          | 1          | No       |
| **BASIC**  | 1,000         | 100         | 10         | 3          | No       |
| **PRO**    | 10,000        | 1,000       | 50         | 10         | Yes      |
| **ENTERPRISE** | Unlimited | Unlimited   | 1,000      | 100        | Yes      |

### Programmatic Quota Check

```java
@Service
@RequiredArgsConstructor
public class ToolExecutionService {

    private final SubscriptionQuotaService quotaService;

    public ToolResult executeTool(UUID userId, UUID toolId, int estimatedTokens) {
        // Check quota before execution
        QuotaCheckResult result = quotaService.checkQuota(userId, toolId, estimatedTokens);

        if (!result.isAllowed()) {
            throw new QuotaExceededException(
                result.getReason(),
                result.getResetTime()
            );
        }

        // Execute tool
        ToolResult toolResult = doExecuteTool(toolId);

        // Consume actual quota
        quotaService.consumeQuota(userId, toolId, toolResult.getActualTokens());

        return toolResult;
    }
}
```

### Get Quota Usage

```java
QuotaUsageStats stats = quotaService.getQuotaUsage(userId, toolId);

System.out.println("Tier: " + stats.getTier());
System.out.println("Used: " + stats.getUsedQuota() + " / " + stats.getMonthlyQuota());
System.out.println("Usage: " + stats.getUsagePercentage() + "%");
System.out.println("Resets: " + stats.getResetTime());
```

## Configuration

### Environment Variables

```bash
# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_DATABASE=0

# Rate Limiting
RATE_LIMIT_ENABLED=true
RATE_LIMIT_GLOBAL=10000
RATE_LIMIT_GLOBAL_WINDOW=3600
RATE_LIMIT_PER_USER=1000
RATE_LIMIT_PER_USER_WINDOW=3600
```

### Application Configuration

```yaml
mcp:
  rate-limit:
    enabled: true

    global:
      limit: 10000
      window-seconds: 3600

    per-user:
      limit: 1000
      window-seconds: 3600

    subscriptions:
      pro:
        monthly-quota: 10000
        daily-limit: 1000
        per-minute-limit: 50
```

## Advanced Usage

### Custom Rate Limit Rules

```java
@Configuration
public class RateLimitRules {

    @Bean
    public RateLimitRule expensiveOperationRule() {
        return RateLimitRule.builder()
            .ruleId("expensive-operations")
            .limit(10)
            .window(Duration.ofMinutes(5))
            .strategy(RateLimitStrategy.SLIDING_WINDOW)
            .keyTemplate("user:{userId}:expensive")
            .costPerRequest(10)  // Each request costs 10 tokens
            .conditions(List.of(
                RateLimitRule.RateLimitCondition.builder()
                    .field("estimatedCost")
                    .operator("greater_than")
                    .value(100)
                    .build()
            ))
            .errorMessage("Too many expensive operations")
            .build();
    }

    @PostConstruct
    public void registerRules() {
        rateLimitService.registerRule(expensiveOperationRule());
    }
}
```

### Programmatic Rate Limiting

```java
@Service
@RequiredArgsConstructor
public class MyService {

    private final RateLimitService rateLimitService;

    public void processRequest(UUID userId, String operation) {
        // Check rate limit programmatically
        RateLimitResult result = rateLimitService.checkUserGlobalLimit(
            userId,
            100,  // limit
            Duration.ofMinutes(1)
        );

        if (!result.isAllowed()) {
            throw new RateLimitExceededException(result);
        }

        // Process request
        doProcess(operation);
    }
}
```

## Monitoring & Metrics

### Prometheus Metrics

```yaml
# Rate limit metrics
mcp_rate_limit_requests_total{user_id="...",status="allowed|rejected"}
mcp_rate_limit_quota_remaining{user_id="...",tool_id="...",tier="..."}
mcp_rate_limit_reset_time{user_id="...",rule="..."}
```

### Get Usage Statistics

```java
Map<String, Long> stats = rateLimitService.getUserUsageStats(userId);
System.out.println("Global usage: " + stats.get("global_usage"));
```

### Reset Limits (Admin)

```java
// Reset all limits for a user
rateLimitService.resetUserLimits(userId);

// Reset specific key
redisRateLimiter.resetLimit("mcp:ratelimit:user:" + userId + ":global");
```

## Best Practices

### 1. Choose Appropriate Limits

```java
// Public endpoints: strict limits
@RateLimit(limit = 10, window = 60, key = "ip")

// Authenticated reads: moderate limits
@RateLimit(limit = 1000, window = 3600, key = "user")

// Expensive operations: low limits
@RateLimit(limit = 10, window = 300, key = "user", cost = 10)

// Free tier endpoints: very strict
@RateLimit(limit = 100, window = 86400, key = "user")
```

### 2. Use Meaningful Error Messages

```java
@RateLimit(
    limit = 50,
    window = 60,
    errorMessage = "Tool call limit exceeded. Upgrade to PRO for higher limits."
)
```

### 3. Monitor Rate Limit Hits

```java
@Slf4j
@Aspect
@Component
public class RateLimitMonitor {

    @AfterThrowing(
        pointcut = "@annotation(com.mcpgateway.ratelimit.RateLimit)",
        throwing = "ex"
    )
    public void logRateLimitExceeded(JoinPoint joinPoint, RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}, user: {}, limit: {}",
            joinPoint.getSignature().getName(),
            getCurrentUserId(),
            ex.getLimit()
        );

        // Send alert if threshold exceeded
        if (getRateLimitHitRate() > 0.8) {
            alertingService.sendAlert("High rate limit hit rate detected");
        }
    }
}
```

### 4. Graceful Degradation

```java
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public RedisRateLimiter gracefulRedisRateLimiter(RedisTemplate<String, String> redisTemplate) {
        return new RedisRateLimiter(redisTemplate) {
            @Override
            public RateLimitResult checkSlidingWindow(String key, long limit, Duration window, int cost) {
                try {
                    return super.checkSlidingWindow(key, limit, window, cost);
                } catch (Exception e) {
                    log.error("Redis rate limiter failed, allowing request", e);
                    // Fail open - allow request if Redis fails
                    return RateLimitResult.allowed(limit, limit, Instant.now().plus(window), "fallback");
                }
            }
        };
    }
}
```

## Troubleshooting

### Rate Limits Not Working

1. **Check Redis Connection**
   ```bash
   redis-cli -h localhost -p 6379 ping
   ```

2. **Verify AspectJ is Enabled**
   ```java
   @SpringBootApplication
   @EnableAspectJAutoProxy  // <-- Must be present
   public class Application { }
   ```

3. **Check Logs**
   ```yaml
   logging:
     level:
       com.mcpgateway.ratelimit: DEBUG
   ```

### False Positives

- **Clock Skew**: Ensure all gateway instances have synchronized clocks (NTP)
- **Redis Eviction**: Set `maxmemory-policy` to `allkeys-lru` in Redis

### Performance Issues

- **Redis Latency**: Use Redis cluster for high-traffic scenarios
- **Network Latency**: Deploy Redis close to gateway instances
- **Lua Script Caching**: Ensure Redis Lua script caching is enabled

## Testing

### Unit Tests

```java
@Test
void rateLimitShouldRejectAfterLimitExceeded() {
    // Allow first 100 requests
    for (int i = 0; i < 100; i++) {
        RateLimitResult result = rateLimiter.checkSlidingWindow(
            "test:user:123", 100, Duration.ofMinutes(1), 1
        );
        assertTrue(result.isAllowed());
    }

    // 101st request should be rejected
    RateLimitResult result = rateLimiter.checkSlidingWindow(
        "test:user:123", 100, Duration.ofMinutes(1), 1
    );
    assertFalse(result.isAllowed());
}
```

### Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiShouldEnforceRateLimit() throws Exception {
        // Make 100 requests (should succeed)
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk());
        }

        // 101st request should be rate limited
        mockMvc.perform(get("/api/tools"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }
}
```

## Migration Guide

### From No Rate Limiting

1. Add Redis dependency to `pom.xml`
2. Configure Redis connection in `application.yml`
3. Add `@RateLimit` annotations to critical endpoints
4. Monitor rate limit metrics
5. Gradually tighten limits based on actual usage

### From Other Rate Limiting Solutions

Replace existing rate limiting with:

```java
// Before (custom implementation)
if (requestCount > limit) {
    throw new TooManyRequestsException();
}

// After (declarative)
@RateLimit(limit = 100, window = 60)
public ResponseEntity<?> myEndpoint() { }
```

## FAQ

**Q: What happens if Redis goes down?**
A: The system fails open - requests are allowed to protect availability.

**Q: Can I have different limits for different subscription tiers?**
A: Yes, use `SubscriptionQuotaService` for tier-based quotas.

**Q: How do I test rate limiting locally?**
A: Use embedded Redis or test containers with `@SpringBootTest`.

**Q: Can rate limits be changed without redeployment?**
A: Yes, use environment variables or external configuration service.

**Q: How accurate is sliding window?**
A: Very accurate - uses millisecond timestamps and sorted sets.

## References

- [Rate Limiting Strategies](https://en.wikipedia.org/wiki/Rate_limiting)
- [Redis Lua Scripting](https://redis.io/docs/interact/programmability/eval-intro/)
- [Spring AOP Documentation](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)

---

For questions or issues, please contact the MCP Gateway team or file an issue on GitHub.
