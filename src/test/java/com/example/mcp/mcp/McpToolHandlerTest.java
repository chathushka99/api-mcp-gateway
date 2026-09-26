package com.example.mcp.mcp;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.mcp.executor.GenericApiExecutor;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.example.mcp.model.ApiResponse;
import com.example.mcp.model.AuthenticationConfiguration;
import com.example.mcp.model.AuthenticationConfiguration.Location;
import com.example.mcp.model.AuthenticationConfiguration.Type;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.registry.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies MCP result conversion for empty successes and unexpected failures.
 */
class McpToolHandlerTest {

    /**
     * Omits the optional body field when a target returns no response content.
     */
    @Test
    @DisplayName("Returns structured success for an empty response")
    void returnsStructuredEmptySuccess() {
        // prepare //
        final ToolRegistry registry = mock(ToolRegistry.class);
        final GenericApiExecutor executor = mock(GenericApiExecutor.class);
        final ObjectMapper objectMapper = new ObjectMapper();
        final GeneratedTool tool = this.generatedTool();
        when(registry.find("read_tool")).thenReturn(Optional.of(tool));
        when(executor.execute(tool.operation(), objectMapper.valueToTree(Map.of()))) // ls
                .thenReturn(Mono.just(new ApiResponse(204, null, "")));
        final McpToolHandler handler = new McpToolHandler( // ls
                registry, executor, objectMapper, new SimpleMeterRegistry());

        // act //
        final McpSchema.CallToolResult result = handler.handle("read_tool", null).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        final Map<?, ?> structuredContent = (Map<?, ?>) result.structuredContent();
        assertThat(structuredContent.get("statusCode")).isEqualTo(204);
        assertThat(structuredContent.containsKey("body")).isFalse();
    }

    /**
     * Masks unexpected runtime failures rather than returning implementation details.
     */
    @Test
    @DisplayName("Masks unexpected execution failures")
    void masksUnexpectedFailures() {
        // prepare //
        final ToolRegistry registry = mock(ToolRegistry.class);
        final GenericApiExecutor executor = mock(GenericApiExecutor.class);
        final ObjectMapper objectMapper = new ObjectMapper();
        final GeneratedTool tool = this.generatedTool();
        when(registry.find("read_tool")).thenReturn(Optional.of(tool));
        when(executor.execute(tool.operation(), objectMapper.valueToTree(Map.of()))) // ls
                .thenReturn(Mono.error(new IllegalStateException("sensitive implementation detail")));
        final McpToolHandler handler = new McpToolHandler( // ls
                registry, executor, objectMapper, new SimpleMeterRegistry());

        // act //
        final McpSchema.CallToolResult result = handler.handle("read_tool", Map.of()).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("Target API request could not be completed") // ls
                .doesNotContain("sensitive implementation detail");
    }

    /**
     * Builds a minimal tool fixture for handler tests.
     *
     * @return generated read-only tool
     */
    private GeneratedTool generatedTool() {
        final ApiOperationDefinition operation = new ApiOperationDefinition( // ls
                "fixture-api", "read", "GET", "/resource", "Fixture", URI.create("https://api.example.test"), // ls
                List.of(), null, false, "application/json", null, // ls
                new AuthenticationConfiguration(Type.NONE, null, null, Location.HEADER), // ls
                Duration.ofSeconds(1), Classification.READ, false, List.of());

        return new GeneratedTool( // ls
                "read_tool", "Fixture", "{\"type\":\"object\"}", "{\"type\":\"object\"}", operation);
    }
}
