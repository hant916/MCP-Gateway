package com.mcpgateway.ratelimit;

import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.ToolSubscription;
import com.mcpgateway.repository.ToolSubscriptionRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Subscription quota management service
 *
 * Manages usage quotas for different subscription tiers and pricing models
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionQuotaService {

    private final ToolSubscriptionRepository subscriptionRepository;
    private final RateLimitService rateLimitService;

    /**
     * Subscription tier configuration
     */
    @Data
    @Builder
    public static class SubscriptionTier {
        private String name;
        private Integer monthlyQuota;      // null = unlimited
        private Integer dailyLimit;        // Requests per day
        private Integer perMinuteLimit;    // Burst protection
        private Integer concurrentRequests; // Max concurrent requests
        private boolean priorityProcessing;
        private boolean advancedFeatures;
    }

    // Predefined subscription tiers
    public static final SubscriptionTier FREE_TIER = SubscriptionTier.builder()
            .name("FREE")
            .monthlyQuota(100)
            .dailyLimit(10)
            .perMinuteLimit(2)
            .concurrentRequests(1)
            .priorityProcessing(false)
            .advancedFeatures(false)
            .build();

    public static final SubscriptionTier BASIC_TIER = SubscriptionTier.builder()
            .name("BASIC")
            .monthlyQuota(1000)
            .dailyLimit(100)
            .perMinuteLimit(10)
            .concurrentRequests(3)
            .priorityProcessing(false)
            .advancedFeatures(false)
            .build();

    public static final SubscriptionTier PRO_TIER = SubscriptionTier.builder()
            .name("PRO")
            .monthlyQuota(10000)
            .dailyLimit(1000)
            .perMinuteLimit(50)
            .concurrentRequests(10)
            .priorityProcessing(true)
            .advancedFeatures(true)
            .build();

    public static final SubscriptionTier ENTERPRISE_TIER = SubscriptionTier.builder()
            .name("ENTERPRISE")
            .monthlyQuota(null)  // Unlimited
            .dailyLimit(null)    // Unlimited
            .perMinuteLimit(1000)
            .concurrentRequests(100)
            .priorityProcessing(true)
            .advancedFeatures(true)
            .build();

    /**
     * Check if user can make a request based on subscription quota
     *
     * @param userId User ID
     * @param toolId Tool ID
     * @param estimatedCost Estimated cost/tokens for this request
     * @return Quota check result
     */
    @Transactional
    public QuotaCheckResult checkQuota(UUID userId, UUID toolId, int estimatedCost) {
        // Find active subscription
        ToolSubscription subscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "No active subscription found for user " + userId + " and tool " + toolId));

        // Get subscription tier based on pricing model
        SubscriptionTier tier = getTierForSubscription(subscription);

        // Check monthly quota
        if (tier.getMonthlyQuota() != null) {
            Integer remaining = subscription.getRemainingQuota();
            if (remaining == null) {
                remaining = tier.getMonthlyQuota();
                subscription.setRemainingQuota(remaining);
                subscriptionRepository.save(subscription);
            }

            if (remaining < estimatedCost) {
                return QuotaCheckResult.builder()
                        .allowed(false)
                        .reason("Monthly quota exceeded")
                        .remainingQuota(remaining)
                        .resetTime(getNextMonthResetTime())
                        .tier(tier.getName())
                        .build();
            }
        }

        // Check daily limit using rate limiter
        if (tier.getDailyLimit() != null) {
            RateLimitResult dailyResult = rateLimitService.checkUserToolLimit(
                    userId, toolId, tier.getDailyLimit(), java.time.Duration.ofDays(1));

            if (!dailyResult.isAllowed()) {
                return QuotaCheckResult.builder()
                        .allowed(false)
                        .reason("Daily limit exceeded")
                        .remainingQuota(0L)
                        .resetTime(dailyResult.getResetTime())
                        .retryAfterSeconds(dailyResult.getRetryAfterSeconds())
                        .tier(tier.getName())
                        .build();
            }
        }

        // Check burst limit (per minute)
        if (tier.getPerMinuteLimit() != null) {
            RateLimitResult burstResult = rateLimitService.checkUserToolLimit(
                    userId, toolId, tier.getPerMinuteLimit(), java.time.Duration.ofMinutes(1));

            if (!burstResult.isAllowed()) {
                return QuotaCheckResult.builder()
                        .allowed(false)
                        .reason("Rate limit exceeded (too many requests)")
                        .remainingQuota((long) tier.getPerMinuteLimit())
                        .resetTime(burstResult.getResetTime())
                        .retryAfterSeconds(burstResult.getRetryAfterSeconds())
                        .tier(tier.getName())
                        .build();
            }
        }

        // All checks passed
        return QuotaCheckResult.builder()
                .allowed(true)
                .reason("Quota available")
                .remainingQuota(subscription.getRemainingQuota() != null ?
                        subscription.getRemainingQuota().longValue() : null)
                .tier(tier.getName())
                .priorityProcessing(tier.isPriorityProcessing())
                .build();
    }

    /**
     * Consume quota after successful request
     *
     * @param userId User ID
     * @param toolId Tool ID
     * @param actualCost Actual cost/tokens consumed
     */
    @Transactional
    public void consumeQuota(UUID userId, UUID toolId, int actualCost) {
        ToolSubscription subscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);

        if (subscription != null && subscription.getTool().getPricingModel() == McpTool.PricingModel.MONTHLY) {
            Integer remaining = subscription.getRemainingQuota();
            if (remaining != null && remaining >= actualCost) {
                subscription.setRemainingQuota(remaining - actualCost);
                subscriptionRepository.save(subscription);
                log.debug("Consumed {} quota for user {} tool {}, remaining: {}",
                        actualCost, userId, toolId, subscription.getRemainingQuota());
            }
        }
    }

    /**
     * Reset monthly quotas (called by scheduled job at month start)
     */
    @Transactional
    public void resetMonthlyQuotas() {
        subscriptionRepository.findAll().forEach(subscription -> {
            if (subscription.getStatus() == ToolSubscription.SubscriptionStatus.ACTIVE) {
                SubscriptionTier tier = getTierForSubscription(subscription);
                if (tier.getMonthlyQuota() != null) {
                    subscription.setRemainingQuota(tier.getMonthlyQuota());
                    subscriptionRepository.save(subscription);
                    log.info("Reset monthly quota for subscription: {}", subscription.getId());
                }
            }
        });
    }

    /**
     * Get quota usage statistics for a user
     *
     * @param userId User ID
     * @param toolId Tool ID
     * @return Quota usage statistics
     */
    public QuotaUsageStats getQuotaUsage(UUID userId, UUID toolId) {
        ToolSubscription subscription = subscriptionRepository
                .findByClientIdAndToolIdAndStatus(userId, toolId, ToolSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);

        if (subscription == null) {
            return QuotaUsageStats.builder()
                    .hasActiveSubscription(false)
                    .build();
        }

        SubscriptionTier tier = getTierForSubscription(subscription);
        Integer remaining = subscription.getRemainingQuota();
        Integer quota = tier.getMonthlyQuota();

        return QuotaUsageStats.builder()
                .hasActiveSubscription(true)
                .tier(tier.getName())
                .monthlyQuota(quota)
                .remainingQuota(remaining)
                .usedQuota(quota != null && remaining != null ? quota - remaining : null)
                .usagePercentage(quota != null && remaining != null ?
                        (double) (quota - remaining) / quota * 100 : null)
                .resetTime(getNextMonthResetTime())
                .build();
    }

    /**
     * Get tier configuration for a subscription
     */
    private SubscriptionTier getTierForSubscription(ToolSubscription subscription) {
        return switch (subscription.getTool().getPricingModel()) {
            case FREE_TIER -> FREE_TIER;
            case MONTHLY -> determineMonthlyTier(subscription);
            case PAY_AS_YOU_GO -> ENTERPRISE_TIER; // PAYG acts like enterprise (pay per use)
        };
    }

    /**
     * Determine monthly subscription tier based on quota
     */
    private SubscriptionTier determineMonthlyTier(ToolSubscription subscription) {
        Integer quota = subscription.getMonthlyQuota();
        if (quota == null) return ENTERPRISE_TIER;
        if (quota <= 100) return FREE_TIER;
        if (quota <= 1000) return BASIC_TIER;
        if (quota <= 10000) return PRO_TIER;
        return ENTERPRISE_TIER;
    }

    /**
     * Get next month reset time
     */
    private java.time.Instant getNextMonthResetTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonth = now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        return nextMonth.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    /**
     * Result of quota check
     */
    @Data
    @Builder
    public static class QuotaCheckResult {
        private boolean allowed;
        private String reason;
        private Long remainingQuota;
        private java.time.Instant resetTime;
        private Long retryAfterSeconds;
        private String tier;
        private boolean priorityProcessing;
    }

    /**
     * Quota usage statistics
     */
    @Data
    @Builder
    public static class QuotaUsageStats {
        private boolean hasActiveSubscription;
        private String tier;
        private Integer monthlyQuota;
        private Integer remainingQuota;
        private Integer usedQuota;
        private Double usagePercentage;
        private java.time.Instant resetTime;
    }
}
