package com.example.mcp.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.mcp.mcp.McpToolHandler;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.registry.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

/**
 * Configures the HTTP client, JSON mapper, and generated MCP tools.
 */
@Configuration
public class GatewayConfiguration {

    /**
     * Creates the mapper used for gateway JSON conversion.
     *
     * @return a mapper with available Jackson modules registered
     */
    @Bean
    ObjectMapper gatewayObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Builds an outbound WebClient with configured connection and body limits.
     *
     * @param properties gateway limits
     * @return configured HTTP client
     */
    @Bean
    WebClient gatewayWebClient(GatewayProperties properties) {
        final Duration connectTimeout = properties.getLimits().getConnectTimeout();
        final int responseLimit = properties.getLimits().getMaxResponseBodyBytes();
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative() || // ls
                responseLimit <= 0 || properties.getLimits().getMaxRequestBodyBytes() <= 0) {
            throw new IllegalArgumentException("Gateway timeouts and body size limits must be positive");
        }
        final HttpClient httpClient = HttpClient.create() // ls
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()));
        return WebClient.builder() // ls
                .clientConnector(new ReactorClientHttpConnector(httpClient)) // ls
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(responseLimit)) // ls
                .build();
    }

    /**
     * Converts registered API operations into asynchronous MCP tool definitions.
     *
     * @param registry     registry of generated API tools
     * @param handler      handler for MCP tool invocations
     * @param objectMapper mapper used to decode generated schemas
     * @return immutable MCP tool specification list
     * @throws Exception if a generated schema cannot be decoded
     */
    @Bean
    List<McpServerFeatures.AsyncToolSpecification> generatedMcpTools( // ls
            ToolRegistry registry, McpToolHandler handler, ObjectMapper objectMapper) throws Exception {
        final List<McpServerFeatures.AsyncToolSpecification> specifications = new ArrayList<>();
        for (final GeneratedTool generated : registry.list()) {
            final Classification classification = generated.operation().classification();
            final Map<String, Object> inputSchema = objectMapper.readValue(generated.inputSchema(), // ls
                    new TypeReference<>() { // ls
                    });
            final Map<String, Object> outputSchema = objectMapper.readValue(generated.outputSchema(), // ls
                    new TypeReference<>() { // ls
                    });
            final McpSchema.Tool tool = McpSchema.Tool.builder() // ls
                    .name(generated.name()) // ls
                    .description(generated.description()) // ls
                    .inputSchema(inputSchema) // ls
                    .outputSchema(outputSchema) // ls
                    .annotations(McpSchema.ToolAnnotations.builder() // ls
                            .readOnlyHint(classification == Classification.READ) // ls
                            .destructiveHint(classification == Classification.MUTATING) // ls
                            .build()) // ls
                    .build();
            specifications.add(McpServerFeatures.AsyncToolSpecification.builder() // ls
                    .tool(tool) // ls
                    .callHandler((exchange, request) -> handler.handle(generated.name(), request.arguments())) // ls
                    .build());
        }
        return List.copyOf(specifications);
    }
}
