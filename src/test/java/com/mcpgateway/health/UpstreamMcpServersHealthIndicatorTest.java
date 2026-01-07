package com.mcpgateway.health;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.repository.McpServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UpstreamMcpServersHealthIndicator
 *
 * Tests health check logic for upstream MCP servers
 */
@ExtendWith(MockitoExtension.class)
class UpstreamMcpServersHealthIndicatorTest {

    @Mock
    private McpServerRepository mcpServerRepository;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private UpstreamMcpServersHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new UpstreamMcpServersHealthIndicator(mcpServerRepository, webClient);
    }

    @Test
    void health_NoServersConfigured_ReturnsUp() {
        // Given
        when(mcpServerRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("message", "No MCP servers configured");
        assertThat(health.getDetails()).containsEntry("totalServers", 0);
    }

    @Test
    void health_AllServersHealthy_ReturnsUp() {
        // Given
        McpServer server1 = createMcpServer("server-1", "http://localhost:8081");
        McpServer server2 = createMcpServer("server-2", "http://localhost:8082");
        List<McpServer> servers = Arrays.asList(server1, server2);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock successful health checks
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.just(ResponseEntity.ok().build()).delayElement(Duration.ofMillis(100))
        );

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalServers", 2);
        assertThat(health.getDetails()).containsEntry("healthyServers", 2);
        assertThat(health.getDetails()).containsEntry("unhealthyServers", 0);
        assertThat(health.getDetails()).containsKey("healthyPercentage");
        assertThat(health.getDetails().get("healthyPercentage").toString()).contains("100.00");

        @SuppressWarnings("unchecked")
        Map<String, String> serverStatuses = (Map<String, String>) health.getDetails().get("servers");
        assertThat(serverStatuses).containsEntry("server-1", "UP");
        assertThat(serverStatuses).containsEntry("server-2", "UP");
    }

    @Test
    void health_AllServersUnhealthy_ReturnsDown() {
        // Given
        McpServer server1 = createMcpServer("server-1", "http://localhost:8081");
        McpServer server2 = createMcpServer("server-2", "http://localhost:8082");
        List<McpServer> servers = Arrays.asList(server1, server2);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock failed health checks
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null))
        );

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("totalServers", 2);
        assertThat(health.getDetails()).containsEntry("healthyServers", 0);
        assertThat(health.getDetails()).containsEntry("unhealthyServers", 2);
        assertThat(health.getDetails().get("healthyPercentage").toString()).contains("0.00");

        @SuppressWarnings("unchecked")
        Map<String, String> serverStatuses = (Map<String, String>) health.getDetails().get("servers");
        assertThat(serverStatuses).containsEntry("server-1", "DOWN");
        assertThat(serverStatuses).containsEntry("server-2", "DOWN");
    }

    @Test
    void health_HalfServersUnhealthy_ReturnsUp() {
        // Given - 50% threshold
        McpServer server1 = createMcpServer("server-1", "http://localhost:8081");
        McpServer server2 = createMcpServer("server-2", "http://localhost:8082");
        List<McpServer> servers = Arrays.asList(server1, server2);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock: first server healthy, second server unhealthy
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
                .thenReturn(Mono.just(ResponseEntity.ok().build()).delayElement(Duration.ofMillis(50)))
                .thenReturn(Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null)));

        // When
        Health health = healthIndicator.health();

        // Then - 50% is acceptable, so should be UP
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalServers", 2);
        assertThat(health.getDetails()).containsEntry("healthyServers", 1);
        assertThat(health.getDetails()).containsEntry("unhealthyServers", 1);
        assertThat(health.getDetails().get("healthyPercentage").toString()).contains("50.00");
    }

    @Test
    void health_BelowThreshold_ReturnsDown() {
        // Given - 3 servers, only 1 healthy (33% < 50% threshold)
        McpServer server1 = createMcpServer("server-1", "http://localhost:8081");
        McpServer server2 = createMcpServer("server-2", "http://localhost:8082");
        McpServer server3 = createMcpServer("server-3", "http://localhost:8083");
        List<McpServer> servers = Arrays.asList(server1, server2, server3);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock: first server healthy, others unhealthy
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
                .thenReturn(Mono.just(ResponseEntity.ok().build()).delayElement(Duration.ofMillis(50)))
                .thenReturn(Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null)))
                .thenReturn(Mono.error(new RuntimeException("Connection timeout")));

        // When
        Health health = healthIndicator.health();

        // Then - 33% < 50% threshold, should be DOWN
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("totalServers", 3);
        assertThat(health.getDetails()).containsEntry("healthyServers", 1);
        assertThat(health.getDetails()).containsEntry("unhealthyServers", 2);
        assertThat(health.getDetails().get("healthyPercentage").toString()).contains("33.33");
    }

    @Test
    void health_RepositoryException_ReturnsDown() {
        // Given
        when(mcpServerRepository.findAll()).thenThrow(new RuntimeException("Database connection failed"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("Database connection failed");
    }

    @Test
    void health_ServerWithTrailingSlash_BuildsCorrectUrl() {
        // Given
        McpServer server = createMcpServer("server-1", "http://localhost:8081/");
        List<McpServer> servers = Collections.singletonList(server);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock successful health check
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("http://localhost:8081/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.just(ResponseEntity.ok().build()).delayElement(Duration.ofMillis(50))
        );

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verify(requestHeadersUriSpec).uri("http://localhost:8081/health");
    }

    @Test
    void health_ServerWithoutTrailingSlash_BuildsCorrectUrl() {
        // Given
        McpServer server = createMcpServer("server-1", "http://localhost:8081");
        List<McpServer> servers = Collections.singletonList(server);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock successful health check
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("http://localhost:8081/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.just(ResponseEntity.ok().build()).delayElement(Duration.ofMillis(50))
        );

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verify(requestHeadersUriSpec).uri("http://localhost:8081/health");
    }

    @Test
    void health_HealthCheckTimeout_MarksServerUnhealthy() {
        // Given
        McpServer server = createMcpServer("slow-server", "http://localhost:8081");
        List<McpServer> servers = Collections.singletonList(server);

        when(mcpServerRepository.findAll()).thenReturn(servers);

        // Mock slow response (will timeout)
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.just(ResponseEntity.ok().build()).delayElement(Duration.ofSeconds(10)) // Exceeds 5s timeout
        );

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("unhealthyServers", 1);

        @SuppressWarnings("unchecked")
        Map<String, String> serverStatuses = (Map<String, String>) health.getDetails().get("servers");
        assertThat(serverStatuses).containsEntry("slow-server", "DOWN");
    }

    // Helper method
    private McpServer createMcpServer(String serviceName, String baseUrl) {
        McpServer server = new McpServer();
        server.setServiceName(serviceName);
        server.setBaseUrl(baseUrl);
        return server;
    }
}
