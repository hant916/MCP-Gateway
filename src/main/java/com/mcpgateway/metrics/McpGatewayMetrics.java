package com.mcpgateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Custom metrics for MCP Gateway
 */
@Component
@RequiredArgsConstructor
public class McpGatewayMetrics {

    private final MeterRegistry meterRegistry;

    // Tool execution metrics
    public void recordToolExecution(String toolName, boolean success, long durationMs) {
        Counter.builder("mcp.tool.executions")
                .tag("tool", toolName)
                .tag("status", success ? "success" : "failure")
                .description("Total number of tool executions")
                .register(meterRegistry)
                .increment();

        Timer.builder("mcp.tool.execution.duration")
                .tag("tool", toolName)
                .description("Tool execution duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Session metrics
    public void recordSessionCreated(String transport) {
        Counter.builder("mcp.sessions.created")
                .tag("transport", transport)
                .description("Total number of sessions created")
                .register(meterRegistry)
                .increment();
    }

    public void recordSessionClosed(String transport, long durationSeconds) {
        Counter.builder("mcp.sessions.closed")
                .tag("transport", transport)
                .description("Total number of sessions closed")
                .register(meterRegistry)
                .increment();

        Timer.builder("mcp.session.duration")
                .tag("transport", transport)
                .description("Session duration")
                .register(meterRegistry)
                .record(Duration.ofSeconds(durationSeconds));
    }

    // API request metrics
    public void recordApiRequest(String endpoint, String method, int statusCode, long durationMs) {
        Counter.builder("mcp.api.requests")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", String.valueOf(statusCode))
                .description("Total API requests")
                .register(meterRegistry)
                .increment();

        Timer.builder("mcp.api.request.duration")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .description("API request duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Marketplace metrics
    public void recordToolSubscription(String toolId, String action) {
        Counter.builder("mcp.marketplace.subscriptions")
                .tag("tool_id", toolId)
                .tag("action", action) // subscribe, unsubscribe
                .description("Tool subscription actions")
                .register(meterRegistry)
                .increment();
    }

    public void recordToolReview(String toolId, int rating) {
        Counter.builder("mcp.marketplace.reviews")
                .tag("tool_id", toolId)
                .tag("rating", String.valueOf(rating))
                .description("Tool reviews")
                .register(meterRegistry)
                .increment();
    }

    // Payment metrics
    public void recordPayment(String status, double amount, String currency) {
        Counter.builder("mcp.payments.total")
                .tag("status", status)
                .tag("currency", currency)
                .description("Total payments")
                .register(meterRegistry)
                .increment();

        meterRegistry.summary("mcp.payments.amount")
                .tag("status", status)
                .tag("currency", currency)
                .record(amount);
    }

    // Rate limiting metrics
    public void recordRateLimitExceeded(String key, String endpoint) {
        Counter.builder("mcp.rate_limit.exceeded")
                .tag("key", key)
                .tag("endpoint", endpoint)
                .description("Rate limit exceeded count")
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimitAllowed(String key, String endpoint) {
        Counter.builder("mcp.rate_limit.allowed")
                .tag("key", key)
                .tag("endpoint", endpoint)
                .description("Rate limit allowed count")
                .register(meterRegistry)
                .increment();
    }

    // Error metrics
    public void recordError(String errorType, String source) {
        Counter.builder("mcp.errors")
                .tag("type", errorType)
                .tag("source", source)
                .description("Total errors")
                .register(meterRegistry)
                .increment();
    }

    // Quota metrics
    public void recordQuotaUsage(String userId, String toolId, int quotaUsed, int quotaRemaining) {
        meterRegistry.gauge("mcp.quota.remaining",
                io.micrometer.core.instrument.Tags.of("user_id", userId, "tool_id", toolId),
                quotaRemaining);

        Counter.builder("mcp.quota.usage")
                .tag("user_id", userId)
                .tag("tool_id", toolId)
                .description("Quota usage")
                .register(meterRegistry)
                .increment(quotaUsed);
    }

    // Webhook metrics
    public void recordWebhookDelivery(String webhookType, boolean success, long durationMs) {
        Counter.builder("mcp.webhooks.delivered")
                .tag("type", webhookType)
                .tag("status", success ? "success" : "failure")
                .description("Webhook deliveries")
                .register(meterRegistry)
                .increment();

        Timer.builder("mcp.webhook.delivery.duration")
                .tag("type", webhookType)
                .description("Webhook delivery duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Active connections gauge
    public void setActiveConnections(String transport, int count) {
        meterRegistry.gauge("mcp.connections.active",
                io.micrometer.core.instrument.Tags.of("transport", transport),
                count);
    }

    // Database connection pool metrics
    public void recordDatabaseQuery(String operation, long durationMs) {
        Timer.builder("mcp.database.query.duration")
                .tag("operation", operation)
                .description("Database query duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
