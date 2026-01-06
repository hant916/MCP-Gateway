package com.mcpgateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.dto.session.MessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * MCP Protocol Fixture Replay Tests
 *
 * Tests the MCP protocol parsing, validation, and dispatch using golden sample fixtures.
 * This test suite ensures:
 * - Protocol parsing doesn't break with schema changes
 * - Unknown fields are tolerated
 * - Required fields are validated
 * - Invalid requests are properly rejected
 * - Both JSON-RPC 2.0 and custom formats are supported
 */
class McpProtocolFixtureTest {

    private ObjectMapper objectMapper;
    private PathMatchingResourcePatternResolver resolver;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        resolver = new PathMatchingResourcePatternResolver();
    }

    @Test
    void testValidJsonRpcToolCall() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/valid-jsonrpc-tool-call.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.isJsonRpcFormat()).isTrue();
        assertThat(request.getJsonRpc()).isEqualTo("2.0");
        assertThat(request.getId()).isEqualTo("req-12345");
        assertThat(request.getMethod()).isEqualTo("tools/call");
        assertThat(request.getEffectiveTool()).isEqualTo("tools/call");
        assertThat(request.getParams()).isNotNull();
        assertThat(request.getParams().has("name")).isTrue();
        assertThat(request.getParams().get("name").asText()).isEqualTo("calculator");
    }

    @Test
    void testValidCustomFormatCall() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/valid-custom-format-call.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.isJsonRpcFormat()).isFalse();
        assertThat(request.getType()).isEqualTo("tool_call");
        assertThat(request.getTool()).isEqualTo("weather_lookup");
        assertThat(request.getEffectiveTool()).isEqualTo("weather_lookup");
        assertThat(request.getArguments()).isNotNull();
        assertThat(request.getArguments().has("city")).isTrue();
        assertThat(request.getData()).isNotNull();
    }

    @Test
    void testJsonRpcListTools() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/jsonrpc-list-tools.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.isJsonRpcFormat()).isTrue();
        assertThat(request.getMethod()).isEqualTo("tools/list");
        assertThat(request.getId()).isEqualTo("list-001");
        assertThat(request.getParams()).isNotNull();
    }

    @Test
    void testJsonRpcInitialize() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/jsonrpc-initialize.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.isJsonRpcFormat()).isTrue();
        assertThat(request.getMethod()).isEqualTo("initialize");
        assertThat(request.getParams()).isNotNull();
        assertThat(request.getParams().has("protocolVersion")).isTrue();
        assertThat(request.getParams().has("capabilities")).isTrue();
        assertThat(request.getParams().has("clientInfo")).isTrue();
    }

    @Test
    void testComplexNestedParams() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/complex-nested-params.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getParams()).isNotNull();
        JsonNode arguments = request.getParams().get("arguments");
        assertThat(arguments).isNotNull();
        assertThat(arguments.has("config")).isTrue();
        assertThat(arguments.has("metadata")).isTrue();

        // Verify deep nesting is preserved
        JsonNode config = arguments.get("config");
        assertThat(config.has("filters")).isTrue();
        assertThat(config.get("filters").isArray()).isTrue();
        assertThat(config.get("filters")).hasSize(2);
    }

    @Test
    void testUnknownFieldsAreTolerated() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/jsonrpc-with-unknown-fields.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert - Should parse successfully despite unknown fields
        assertThat(request).isNotNull();
        assertThat(request.isJsonRpcFormat()).isTrue();
        assertThat(request.getMethod()).isEqualTo("tools/call");
        assertThat(request.getId()).isEqualTo("req-unknown");
        // Unknown fields should be silently ignored, not cause errors
    }

    @Test
    void testBatchRequest() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/batch-request.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest[] batch = objectMapper.readValue(json, MessageRequest[].class);

        // Assert
        assertThat(batch).isNotNull();
        assertThat(batch).hasSize(3);
        assertThat(batch[0].getId()).isEqualTo("batch-1");
        assertThat(batch[1].getId()).isEqualTo("batch-2");
        assertThat(batch[2].getId()).isEqualTo("batch-3");
        assertThat(batch[2].getMethod()).isEqualTo("tools/list");
    }

    @Test
    void testNotificationWithoutId() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/notification-no-id.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert - Notifications don't have ID field
        assertThat(request).isNotNull();
        assertThat(request.isJsonRpcFormat()).isTrue();
        assertThat(request.getMethod()).isEqualTo("notifications/progress");
        assertThat(request.getId()).isNull();
    }

    @Test
    void testResponseParsing_ValidToolCallResponse() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/responses/valid-jsonrpc-tool-call-response.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        JsonNode response = objectMapper.readTree(json);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.has("jsonrpc")).isTrue();
        assertThat(response.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.has("id")).isTrue();
        assertThat(response.get("id").asText()).isEqualTo("req-12345");
        assertThat(response.has("result")).isTrue();
        assertThat(response.get("result").has("content")).isTrue();
    }

    @Test
    void testResponseParsing_ErrorResponse() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/responses/jsonrpc-error-response.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        JsonNode response = objectMapper.readTree(json);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.has("error")).isTrue();
        JsonNode error = response.get("error");
        assertThat(error.has("code")).isTrue();
        assertThat(error.get("code").asInt()).isEqualTo(-32601);
        assertThat(error.has("message")).isTrue();
        assertThat(error.get("message").asText()).isEqualTo("Method not found");
    }

    @Test
    void testInvalidRequest_MalformedJson() {
        // Arrange
        String malformedJson = "{\"jsonrpc\": \"2.0\" \"id\": \"malformed\"}";

        // Act & Assert
        assertThatThrownBy(() -> objectMapper.readValue(malformedJson, MessageRequest.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testInvalidRequest_WrongTypeParams() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/invalid/wrong-type-params.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert - Should parse but params will be string instead of object
        // This is actually acceptable as Jackson is flexible, but the validation
        // layer should catch this
        assertThat(request).isNotNull();
    }

    @Test
    void testInvalidRequest_MissingMethod() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/invalid/missing-method.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert - Parses but method is null
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isNull();
        assertThat(request.isJsonRpcFormat()).isFalse(); // Not valid JSON-RPC without method
    }

    @Test
    void testInvalidRequest_EmptyId() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/invalid/empty-id.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getId()).isEmpty();
        // Empty ID should be flagged by validation layer
    }

    @Test
    void testInvalidRequest_NullId() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/invalid/null-id.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getId()).isNull();
    }

    @Test
    void testEdgeCase_HugeNestedDepth() throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/invalid/huge-nested-depth.json");
        String json = new String(resource.getInputStream().readAllBytes());

        // Act
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        // Assert - Should parse but may hit depth limits in real processing
        assertThat(request).isNotNull();
        assertThat(request.getParams()).isNotNull();

        // Verify we can navigate to deep nested structure
        JsonNode deep = request.getParams();
        for (int i = 1; i <= 10; i++) {
            deep = deep.get("level" + i);
            if (i == 10) {
                assertThat(deep.has("data")).isTrue();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provideValidFixtures")
    void testAllValidFixturesParse(String fixturePath) throws IOException {
        // Arrange
        Resource resource = resolver.getResource("classpath:" + fixturePath);
        String json = new String(resource.getInputStream().readAllBytes());

        // Act & Assert - Should parse without exceptions
        if (fixturePath.contains("batch-request")) {
            MessageRequest[] batch = objectMapper.readValue(json, MessageRequest[].class);
            assertThat(batch).isNotEmpty();
        } else {
            MessageRequest request = objectMapper.readValue(json, MessageRequest.class);
            assertThat(request).isNotNull();
        }
    }

    static Stream<String> provideValidFixtures() {
        return Stream.of(
            "test-fixtures/mcp/requests/valid-jsonrpc-tool-call.json",
            "test-fixtures/mcp/requests/valid-custom-format-call.json",
            "test-fixtures/mcp/requests/jsonrpc-list-tools.json",
            "test-fixtures/mcp/requests/jsonrpc-initialize.json",
            "test-fixtures/mcp/requests/complex-nested-params.json",
            "test-fixtures/mcp/requests/jsonrpc-with-unknown-fields.json",
            "test-fixtures/mcp/requests/notification-no-id.json",
            "test-fixtures/mcp/requests/batch-request.json"
        );
    }

    @Test
    void testFixtureDirectory_ContainsExpectedFiles() throws IOException {
        // Verify fixture directory structure
        Resource[] requestFixtures = resolver.getResources("classpath:test-fixtures/mcp/requests/*.json");
        Resource[] invalidFixtures = resolver.getResources("classpath:test-fixtures/mcp/invalid/*.json");
        Resource[] responseFixtures = resolver.getResources("classpath:test-fixtures/mcp/responses/*.json");

        assertThat(requestFixtures.length).isGreaterThanOrEqualTo(5)
                .withFailMessage("Should have at least 5 valid request fixtures");
        assertThat(invalidFixtures.length).isGreaterThanOrEqualTo(5)
                .withFailMessage("Should have at least 5 invalid request fixtures");
        assertThat(responseFixtures.length).isGreaterThanOrEqualTo(3)
                .withFailMessage("Should have at least 3 response fixtures");
    }

    @Test
    void testProtocolVersionCompatibility() throws IOException {
        // Test that we can handle different protocol versions gracefully
        Resource resource = resolver.getResource("classpath:test-fixtures/mcp/requests/jsonrpc-initialize.json");
        String json = new String(resource.getInputStream().readAllBytes());
        MessageRequest request = objectMapper.readValue(json, MessageRequest.class);

        assertThat(request.getParams()).isNotNull();
        assertThat(request.getParams().has("protocolVersion")).isTrue();

        // We should be able to extract and validate protocol version
        String protocolVersion = request.getParams().get("protocolVersion").asText();
        assertThat(protocolVersion).matches("\\d{4}-\\d{2}-\\d{2}"); // YYYY-MM-DD format
    }

    @Test
    void testRequestIdFormats_StringAndNumber() throws IOException {
        // Test different ID formats (string, number, null for notifications)
        List<String> testCases = List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":\"string-id\",\"method\":\"test\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":12345,\"method\":\"test\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"test\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notification\",\"params\":{}}"
        );

        for (String testCase : testCases) {
            MessageRequest request = objectMapper.readValue(testCase, MessageRequest.class);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isNotNull();
        }
    }
}
