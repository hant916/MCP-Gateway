package com.mcpgateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache Configuration
 *
 * Provides local in-memory caching with:
 * - Automatic eviction based on size and time
 * - Statistics collection for monitoring
 * - Integration with Micrometer metrics
 *
 * Cache Strategy:
 * - tools: Tool definitions (rarely change)
 * - users: User information for JWT validation
 * - servers: MCP server configurations
 * - subscriptions: User subscriptions
 * - apiKeys: API key metadata
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final MeterRegistry meterRegistry;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "tools", "users", "servers", "subscriptions", "apiKeys"
        );

        cacheManager.setCaffeine(caffeineCacheBuilder());

        // Register cache metrics with Prometheus
        registerCacheMetrics(cacheManager);

        log.info("Caffeine cache manager configured with caches: tools, users, servers, subscriptions, apiKeys");
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .evictionListener((key, value, cause) -> {
                    log.debug("Cache eviction: key={}, cause={}", key, cause);
                })
                .removalListener((key, value, cause) -> {
                    log.trace("Cache removal: key={}, cause={}", key, cause);
                });
    }

    private void registerCacheMetrics(CaffeineCacheManager cacheManager) {
        cacheManager.getCacheNames().forEach(cacheName -> {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>)
                            cacheManager.getCache(cacheName).getNativeCache();

            CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, cacheName);
            log.info("Registered metrics for cache: {}", cacheName);
        });
    }

    /**
     * Custom cache configuration for tools (longer TTL)
     */
    @Bean
    public Caffeine<Object, Object> toolsCaffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(15, TimeUnit.MINUTES) // Tools change infrequently
                .recordStats();
    }

    /**
     * Custom cache configuration for users (shorter TTL for security)
     */
    @Bean
    public Caffeine<Object, Object> usersCaffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(2, TimeUnit.MINUTES) // Shorter TTL for security
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .recordStats();
    }
}
