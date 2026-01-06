package com.mcpgateway.ratelimit;

import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-strategy rate limiting service for MCP Gateway
 *
 * Provides various rate limiting strategies:
 * - Per-user global limits
 * - Per-tool per-user limits
 * - Subscription-based quota management
 * - Custom rule-based limiting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisRateLimiter redisRateLimiter;
    private final ToolSubscriptionRepository subscriptionRepository;

    // Cache of rate limit rules
    private final Map<String, RateLimitRule> ruleCache = new ConcurrentHashMap<>();

    private static final String KEY_PREFIX = "mcp:ratelimit:";

    /**
     * Check global per-user rate limit
     *
     * @param userId User ID
     * @param limit Maximum requests per window
     * @param window Time window
     * @return Rate limit check result
     */
    public RateLimitResult checkUserGlobalLimit(UUID userId, long limit, Duration window) {
        String key = KEY_PREFIX + "user:" + userId + ":global";
        return redisRateLimiter.checkSlidingWindow(key, limit, window, 1);
    }

    /**
     * Check per-tool per-user rate limit
     *
     * @param userId User ID
     * @param toolId Tool ID
     * @param limit Maximum requests per window
     * @param window Time window
     * @return Rate limit check result
     */
    public RateLimitResult checkUserToolLimit(UUID userId, UUID toolId, long limit, Duration window) {
        String key = KEY_PREFIX + "user:" + userId + ":tool:" + toolId;
        return redisRateLimiter.checkSlidingWindow(key, limit, window, 1);
    }

    /**
     * Check subscription-based quota limit
     *
     * @param userId User ID
     * @param toolId Tool ID
     * @param cost Cost of this request (e.g., token count)
     * @return Rate limit check result
     */
    public RateLimitResult checkSubscriptionQuota(UUID userId, UUID toolId, int cost) {
        // Find active subscription
        Optional<ToolSubscription> subscriptionOpt = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE);

        if (subscriptionOpt.isEmpty()) {
            throw new IllegalStateException("No active subscription found for user " + userId + " and tool " + toolId);
        }

        ToolSubscription subscription = subscriptionOpt.get();

        // Different limits based on pricing model
        return switch (subscription.getTool().getPricingModel()) {
            case MONTHLY -> checkMonthlyQuota(subscription, cost);
            case PAY_AS_YOU_GO -> checkPayAsYouGoLimit(subscription, cost);
            case FREE_TIER -> checkFreeTierLimit(subscription, cost);
        };
    }

    /**
     * Check monthly subscription quota
     */
    private RateLimitResult checkMonthlyQuota(ToolSubscription subscription, int cost) {
        Integer remainingQuota = subscription.getRemainingQuota();
        if (remainingQuota == null) {
            remainingQuota = 10000; // Default monthly quota
        }

        String key = KEY_PREFIX + "subscription:" + subscription.getId() + ":monthly";

        // Use fixed window for monthly quota (resets monthly)
        long monthlyLimit = subscription.getMonthlyQuota() != null ?
                subscription.getMonthlyQuota() : 10000;

        return redisRateLimiter.checkFixedWindow(
                key,
                monthlyLimit,
                Duration.ofDays(30),
                cost
        );
    }

    /**
     * Check pay-as-you-go rate limit (still needs rate limiting to prevent abuse)
     */
    private RateLimitResult checkPayAsYouGoLimit(ToolSubscription subscription, int cost) {
        // Pay as you go still has burst protection
        String key = KEY_PREFIX + "subscription:" + subscription.getId() + ":payg";

        // Allow 1000 requests per minute for PAYG
        return redisRateLimiter.checkSlidingWindow(
                key,
                1000,
                Duration.ofMinutes(1),
                cost
        );
    }

    /**
     * Check free tier limit (most restrictive)
     */
    private RateLimitResult checkFreeTierLimit(ToolSubscription subscription, int cost) {
        String key = KEY_PREFIX + "subscription:" + subscription.getId() + ":free";

        // Free tier: 100 requests per day
        return redisRateLimiter.checkFixedWindow(
                key,
                100,
                Duration.ofDays(1),
                cost
        );
    }

    /**
     * Check custom rule-based rate limit
     *
     * @param ruleId Rule identifier
     * @param context Context variables for key interpolation
     * @return Rate limit check result
     */
    public RateLimitResult checkCustomRule(String ruleId, Map<String, String> context) {
        RateLimitRule rule = ruleCache.get(ruleId);
        if (rule == null) {
            log.warn("Rate limit rule not found: {}", ruleId);
            return RateLimitResult.allowed(Long.MAX_VALUE, Long.MAX_VALUE,
                    java.time.Instant.now().plusSeconds(3600), "no-rule");
        }

        // Interpolate key template with context
        String key = interpolateKey(rule.getKeyTemplate(), context);

        // Apply appropriate strategy
        return switch (rule.getStrategy()) {
            case SLIDING_WINDOW -> redisRateLimiter.checkSlidingWindow(
                    key, rule.getLimit(), rule.getWindow(), rule.getCostPerRequest());
            case TOKEN_BUCKET -> {
                double refillRate = (double) rule.getLimit() / rule.getWindow().getSeconds();
                yield redisRateLimiter.checkTokenBucket(
                        key, rule.getLimit(), refillRate, rule.getCostPerRequest());
            }
            case FIXED_WINDOW -> redisRateLimiter.checkFixedWindow(
                    key, rule.getLimit(), rule.getWindow(), rule.getCostPerRequest());
            case LEAKY_BUCKET -> redisRateLimiter.checkSlidingWindow(
                    key, rule.getLimit(), rule.getWindow(), rule.getCostPerRequest());
        };
    }

    /**
     * Register a custom rate limit rule
     *
     * @param rule Rate limit rule
     */
    public void registerRule(RateLimitRule rule) {
        ruleCache.put(rule.getRuleId(), rule);
        log.info("Registered rate limit rule: {}", rule.getRuleId());
    }

    /**
     * Remove a rate limit rule
     *
     * @param ruleId Rule identifier
     */
    public void unregisterRule(String ruleId) {
        ruleCache.remove(ruleId);
        log.info("Unregistered rate limit rule: {}", ruleId);
    }

    /**
     * Get all registered rules
     */
    public Collection<RateLimitRule> getAllRules() {
        return ruleCache.values();
    }

    /**
     * Interpolate key template with context variables
     */
    private String interpolateKey(String template, Map<String, String> context) {
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return KEY_PREFIX + result;
    }

    /**
     * Check if rate limit should be enforced based on conditions
     */
    private boolean shouldEnforce(RateLimitRule rule, Map<String, Object> requestContext) {
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            return true;
        }

        return rule.getConditions().stream().allMatch(condition ->
                evaluateCondition(condition, requestContext)
        );
    }

    /**
     * Evaluate a single condition
     */
    private boolean evaluateCondition(RateLimitRule.RateLimitCondition condition,
                                      Map<String, Object> context) {
        Object contextValue = context.get(condition.getField());
        if (contextValue == null) {
            return false;
        }

        return switch (condition.getOperator()) {
            case "equals" -> contextValue.equals(condition.getValue());
            case "greater_than" -> compareNumbers(contextValue, condition.getValue()) > 0;
            case "less_than" -> compareNumbers(contextValue, condition.getValue()) < 0;
            case "contains" -> contextValue.toString().contains(condition.getValue().toString());
            default -> false;
        };
    }

    private int compareNumbers(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        return 0;
    }

    /**
     * Get current usage statistics for monitoring
     *
     * @param userId User ID
     * @return Usage statistics
     */
    public Map<String, Long> getUserUsageStats(UUID userId) {
        Map<String, Long> stats = new HashMap<>();

        String globalKey = KEY_PREFIX + "user:" + userId + ":global";
        stats.put("global_usage", redisRateLimiter.getCurrentUsage(globalKey));

        return stats;
    }

    /**
     * Reset rate limit for a user (admin function)
     *
     * @param userId User ID
     */
    public void resetUserLimits(UUID userId) {
        String globalKey = KEY_PREFIX + "user:" + userId + ":global";
        redisRateLimiter.resetLimit(globalKey);
        log.info("Reset rate limits for user: {}", userId);
    }
}
