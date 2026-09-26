package com.example.mcp.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.mcp.mcp.McpToolHandler;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.example.mcp.model.AuthenticationConfiguration;
import com.example.mcp.model.AuthenticationConfiguration.Location;
import com.example.mcp.model.AuthenticationConfiguration.Type;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.registry.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies gateway bean creation and conversion of generated tools to MCP specifications.
 */
class GatewayConfigurationTest {

    /**
     * Creates a mapper, applies valid HTTP limits, and builds both operation classifications.
     *
     * @throws Exception if the generated schemas cannot be decoded
     */
    @Test
    @DisplayName("Builds configured HTTP and MCP tool beans")
    void buildsHttpAndMcpToolBeans() throws Exception {
        // prepare //
        final GatewayConfiguration configuration = new GatewayConfiguration();
        final GatewayProperties properties = new GatewayProperties();
        final ObjectMapper mapper = configuration.gatewayObjectMapper();
        final WebClient webClient = configuration.gatewayWebClient(properties);
        final ToolRegistry registry = mock(ToolRegistry.class);
        final McpToolHandler handler = mock(McpToolHandler.class);
        when(registry.list()).thenReturn(List.of( // ls
                this.generatedTool(Classification.READ), // ls
                this.generatedTool(Classification.MUTATING)));

        // act //
        final List<McpServerFeatures.AsyncToolSpecification> specifications = // ls
                configuration.generatedMcpTools(registry, handler, mapper);

        // assert //
        assertThat(webClient).isNotNull();
        assertThat(mapper.findAndRegisterModules()).isSameAs(mapper);
        assertThat(specifications).hasSize(2);
        assertThat(specifications).extracting(specification -> specification.tool().name()) // ls
                .containsExactly("read_tool", "mutating_tool");
    }

    /**
     * Rejects null, zero, and negative timeouts and invalid body-size limits.
     */
    @Test
    @DisplayName("Rejects non-positive client timeouts and body limits")
    void rejectsInvalidClientLimits() {
        // prepare //
        final GatewayConfiguration configuration = new GatewayConfiguration();
        final GatewayProperties properties = new GatewayProperties();

        // act //
        // assert //
        properties.getLimits().setConnectTimeout(null);
        assertThatThrownBy(() -> configuration.gatewayWebClient(properties)) // ls
                .isInstanceOf(IllegalArgumentException.class);
        properties.getLimits().setConnectTimeout(Duration.ZERO);
        assertThatThrownBy(() -> configuration.gatewayWebClient(properties)) // ls
                .isInstanceOf(IllegalArgumentException.class);
        properties.getLimits().setConnectTimeout(Duration.ofSeconds(-1));
        assertThatThrownBy(() -> configuration.gatewayWebClient(properties)) // ls
                .isInstanceOf(IllegalArgumentException.class);
        properties.getLimits().setConnectTimeout(Duration.ofSeconds(1));
        properties.getLimits().setMaxResponseBodyBytes(0);
        assertThatThrownBy(() -> configuration.gatewayWebClient(properties)) // ls
                .isInstanceOf(IllegalArgumentException.class);
        properties.getLimits().setMaxResponseBodyBytes(1);
        properties.getLimits().setMaxRequestBodyBytes(0);
        assertThatThrownBy(() -> configuration.gatewayWebClient(properties)) // ls
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Builds a minimal registered tool with the requested operation classification.
     *
     * @param classification classification used for MCP safety hints
     * @return generated tool fixture
     */
    private GeneratedTool generatedTool(Classification classification) {
        final String name = classification == Classification.READ ? "read_tool" : "mutating_tool";
        final ApiOperationDefinition operation = new ApiOperationDefinition( // ls
                "fixture-api", name, "GET", "/resource", "Fixture tool", URI.create("https://api.example.test"), // ls
                List.of(), null, false, "application/json", null, // ls
                new AuthenticationConfiguration(Type.NONE, null, null, Location.HEADER), // ls
                Duration.ofSeconds(1), classification, false, List.of()); // ls

        return new GeneratedTool( // ls
                name, "Fixture tool", "{\"type\":\"object\"}", "{\"type\":\"object\"}", operation);
    }
}
