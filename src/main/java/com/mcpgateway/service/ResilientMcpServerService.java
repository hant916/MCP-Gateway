package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Resilient MCP Server Service with Circuit Breaker
 *
 * Demonstrates Circuit Breaker pattern for upstream MCP server calls:
 * - Automatic failure detection
 * - Circuit opens after threshold failures
 * - Automatic retry with exponential backoff
 * - Fallback responses when circuit is open
 * - Timeout protection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientMcpServerService {

    private final WebClient webClient;

    /**
     * Execute tool with circuit breaker protection
     *
     * Circuit breaker configuration from application.yml:
     * - instance: mcpServer
     * - failure rate threshold: 50%
     * - wait duration in open state: 10s
     * - sliding window size: 20 calls
     */
    @CircuitBreaker(name = "mcpServer", fallbackMethod = "executeTool Fallback")
    @Retry(name = "mcpServer")
    @TimeLimiter(name = "mcpServer")
    public CompletableFuture<Map<String, Object>> executeTool(
            McpServer server,
            String toolName,
            Map<String, Object> arguments
    ) {
        log.info("Executing tool {} on server {} with circuit breaker protection",
                toolName, server.getServiceName());

        return webClient.post()
                .uri(server.getBaseUrl() + "/tools/execute")
                .bodyValue(Map.of(
                        "tool", toolName,
                        "arguments", arguments
                ))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(30))
                .toFuture();
    }

    /**
     * Fallback method when circuit breaker is open or call fails
     */
    public CompletableFuture<Map<String, Object>> executeToolFallback(
            McpServer server,
            String toolName,
            Map<String, Object> arguments,
            Exception e
    ) {
        log.warn("Circuit breaker fallback triggered for tool {} on server {}: {}",
                toolName, server.getServiceName(), e.getMessage());

        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("error", "Service temporarily unavailable");
        fallbackResponse.put("message", "The MCP server is experiencing issues. Please try again later.");
        fallbackResponse.put("tool", toolName);
        fallbackResponse.put("server", server.getServiceName());
        fallbackResponse.put("fallback", true);

        return CompletableFuture.completedFuture(fallbackResponse);
    }

    /**
     * Health check with circuit breaker
     */
    @CircuitBreaker(name = "mcpServer", fallbackMethod = "checkHealthFallback")
    @Retry(name = "mcpServer")
    public CompletableFuture<Boolean> checkHealth(McpServer server) {
        return webClient.get()
                .uri(server.getBaseUrl() + "/health")
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .timeout(Duration.ofSeconds(5))
                .toFuture();
    }

    public CompletableFuture<Boolean> checkHealthFallback(McpServer server, Exception e) {
        log.debug("Health check fallback for server {}: {}", server.getServiceName(), e.getMessage());
        return CompletableFuture.completedFuture(false);
    }

    /**
     * List tools with circuit breaker protection
     */
    @CircuitBreaker(name = "mcpServer", fallbackMethod = "listToolsFallback")
    @Retry(name = "mcpServer", fallbackMethod = "listToolsFallback")
    @TimeLimiter(name = "mcpServer")
    public CompletableFuture<Map<String, Object>> listTools(McpServer server) {
        log.info("Listing tools from server {} with circuit breaker protection", server.getServiceName());

        return webClient.get()
                .uri(server.getBaseUrl() + "/tools/list")
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .toFuture();
    }

    public CompletableFuture<Map<String, Object>> listToolsFallback(McpServer server, Exception e) {
        log.warn("List tools fallback for server {}: {}", server.getServiceName(), e.getMessage());

        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("tools", new Object[0]);
        fallbackResponse.put("error", "Unable to fetch tools from server");
        fallbackResponse.put("fallback", true);

        return CompletableFuture.completedFuture(fallbackResponse);
    }
}
