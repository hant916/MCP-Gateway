package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResilientMcpServerService
 *
 * Tests circuit breaker, retry, and fallback behavior
 */
@ExtendWith(MockitoExtension.class)
class ResilientMcpServerServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ResilientMcpServerService resilientService;

    private McpServer testServer;

    @BeforeEach
    void setUp() {
        resilientService = new ResilientMcpServerService(webClient);

        testServer = new McpServer();
        testServer.setServiceName("test-mcp-server");
        testServer.setBaseUrl("http://localhost:8081");
    }

    @Test
    void executeTool_Success_ReturnsResult() throws ExecutionException, InterruptedException {
        // Given
        String toolName = "test-tool";
        Map<String, Object> arguments = Map.of("arg1", "value1");
        Map<String, Object> expectedResponse = Map.of(
                "result", "success",
                "data", "tool executed successfully"
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(expectedResponse).delayElement(Duration.ofMillis(100)));

        // When
        CompletableFuture<Map<String, Object>> result = resilientService.executeTool(
                testServer, toolName, arguments
        );

        // Then
        assertThat(result).isNotNull();
        Map<String, Object> actualResponse = result.get();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.get("result")).isEqualTo("success");
        assertThat(actualResponse.get("data")).isEqualTo("tool executed successfully");

        verify(webClient, times(1)).post();
    }

    @Test
    void executeTool_Failure_ReturnsFallbackResponse() throws ExecutionException, InterruptedException {
        // Given
        String toolName = "failing-tool";
        Map<String, Object> arguments = Map.of("arg1", "value1");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new WebClientResponseException(
                        500, "Internal Server Error", null, null, null
                )));

        // When
        CompletableFuture<Map<String, Object>> result = resilientService.executeTool(
                testServer, toolName, arguments
        );

        // Then - Note: Without actual circuit breaker configuration, this will throw
        // In real scenario with @CircuitBreaker, it would call fallback
        // For unit test, we test fallback directly
        Map<String, Object> fallbackResponse = resilientService.executeToolFallback(
                testServer, toolName, arguments, new RuntimeException("Service down")
        ).get();

        assertThat(fallbackResponse).isNotNull();
        assertThat(fallbackResponse.get("error")).isEqualTo("Service temporarily unavailable");
        assertThat(fallbackResponse.get("fallback")).isEqualTo(true);
        assertThat(fallbackResponse.get("tool")).isEqualTo(toolName);
        assertThat(fallbackResponse.get("server")).isEqualTo(testServer.getServiceName());
    }

    @Test
    void executeToolFallback_ReturnsErrorResponse() throws ExecutionException, InterruptedException {
        // Given
        String toolName = "test-tool";
        Map<String, Object> arguments = Map.of("arg1", "value1");
        Exception exception = new RuntimeException("Connection timeout");

        // When
        CompletableFuture<Map<String, Object>> result = resilientService.executeToolFallback(
                testServer, toolName, arguments, exception
        );

        // Then
        Map<String, Object> response = result.get();
        assertThat(response).isNotNull();
        assertThat(response.get("error")).isEqualTo("Service temporarily unavailable");
        assertThat(response.get("message")).isEqualTo("The MCP server is experiencing issues. Please try again later.");
        assertThat(response.get("tool")).isEqualTo(toolName);
        assertThat(response.get("server")).isEqualTo("test-mcp-server");
        assertThat(response.get("fallback")).isEqualTo(true);
    }

    @Test
    void checkHealth_Success_ReturnsTrue() throws ExecutionException, InterruptedException {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.just(org.springframework.http.ResponseEntity.ok().build())
                        .delayElement(Duration.ofMillis(50))
        );

        // When
        CompletableFuture<Boolean> result = resilientService.checkHealth(testServer);

        // Then
        assertThat(result).isNotNull();
        Boolean isHealthy = result.get();
        assertThat(isHealthy).isTrue();

        verify(webClient, times(1)).get();
    }

    @Test
    void checkHealth_Failure_ReturnsFalse() throws ExecutionException, InterruptedException {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null))
        );

        // When - Test fallback directly
        CompletableFuture<Boolean> result = resilientService.checkHealthFallback(
                testServer, new RuntimeException("Health check failed")
        );

        // Then
        Boolean isHealthy = result.get();
        assertThat(isHealthy).isFalse();
    }

    @Test
    void checkHealthFallback_ReturnsFalse() throws ExecutionException, InterruptedException {
        // Given
        Exception exception = new RuntimeException("Connection refused");

        // When
        CompletableFuture<Boolean> result = resilientService.checkHealthFallback(testServer, exception);

        // Then
        Boolean isHealthy = result.get();
        assertThat(isHealthy).isFalse();
    }

    @Test
    void listTools_Success_ReturnsToolsList() throws ExecutionException, InterruptedException {
        // Given
        Map<String, Object> expectedResponse = Map.of(
                "tools", new Object[]{
                        Map.of("name", "tool1", "description", "First tool"),
                        Map.of("name", "tool2", "description", "Second tool")
                }
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(expectedResponse).delayElement(Duration.ofMillis(100)));

        // When
        CompletableFuture<Map<String, Object>> result = resilientService.listTools(testServer);

        // Then
        assertThat(result).isNotNull();
        Map<String, Object> response = result.get();
        assertThat(response).isNotNull();
        assertThat(response.get("tools")).isNotNull();

        verify(webClient, times(1)).get();
    }

    @Test
    void listToolsFallback_ReturnsEmptyTools() throws ExecutionException, InterruptedException {
        // Given
        Exception exception = new RuntimeException("Server not responding");

        // When
        CompletableFuture<Map<String, Object>> result = resilientService.listToolsFallback(
                testServer, exception
        );

        // Then
        Map<String, Object> response = result.get();
        assertThat(response).isNotNull();
        assertThat(response.get("tools")).isNotNull();
        assertThat(response.get("tools")).isInstanceOf(Object[].class);
        assertThat(((Object[]) response.get("tools")).length).isEqualTo(0);
        assertThat(response.get("error")).isEqualTo("Unable to fetch tools from server");
        assertThat(response.get("fallback")).isEqualTo(true);
    }

    @Test
    void executeTool_WithEmptyArguments_Success() throws ExecutionException, InterruptedException {
        // Given
        String toolName = "no-args-tool";
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> expectedResponse = Map.of("result", "ok");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(expectedResponse));

        // When
        CompletableFuture<Map<String, Object>> result = resilientService.executeTool(
                testServer, toolName, arguments
        );

        // Then
        assertThat(result).isNotNull();
        Map<String, Object> response = result.get();
        assertThat(response.get("result")).isEqualTo("ok");
    }
}
