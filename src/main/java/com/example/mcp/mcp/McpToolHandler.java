package com.example.mcp.mcp;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.mcp.exception.GatewayException;
import com.example.mcp.executor.GenericApiExecutor;
import com.example.mcp.model.ApiResponse;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.registry.ToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

/**
 * Handles MCP tool calls and records safe execution outcomes and metrics.
 */
@Component
public class McpToolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolHandler.class);

    private final ToolRegistry registry;
    private final GenericApiExecutor executor;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Creates the MCP handler and its execution, serialization, and metrics dependencies.
     *
     * @param registry      registry used to look up enabled tools
     * @param executor      executor for target API operations
     * @param objectMapper  mapper used for argument and result conversion
     * @param meterRegistry metrics registry for tool outcomes
     */
    public McpToolHandler(ToolRegistry registry, GenericApiExecutor executor, ObjectMapper objectMapper, // ls
                          MeterRegistry meterRegistry) {
        this.registry = registry;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Executes an MCP tool request and converts failures to safe tool results.
     *
     * @param toolName  requested tool name
     * @param arguments supplied tool arguments
     * @return asynchronous MCP tool result
     */
    public Mono<McpSchema.CallToolResult> handle(String toolName, Map<String, Object> arguments) {
        return Mono.defer(() -> { // ls
            final Timer.Sample sample = Timer.start(this.meterRegistry); // ls
            final GeneratedTool tool = this.registry.find(toolName).orElse(null); // ls
            final String metricToolName = tool == null ? "unknown" : tool.name(); // ls
            final Mono<ApiResponse> execution = tool == null // ls
                    ? Mono.error(new GatewayException(GatewayException.Code.UNKNOWN_TOOL, // ls
                    "Unknown or disabled MCP tool")) // ls
                    : this.executor.execute(tool.operation(), // ls
                    this.objectMapper.valueToTree(arguments == null ? Map.of() : arguments)); // ls
            return execution.map(response -> { // ls
                        final McpSchema.CallToolResult result = this.successResult(response); // ls
                        this.record(sample, metricToolName, "success", response.statusCode()); // ls
                        return result; // ls
                    }) // ls
                    .onErrorResume(error -> { // ls
                        final GatewayException gatewayError = safeError(error); // ls
                        this.record(sample, metricToolName, "failure", // ls
                                gatewayError.getStatusCode() == null ? 0 : gatewayError.getStatusCode()); // ls
                        if (gatewayError.getCode() == GatewayException.Code.TIMEOUT) { // ls
                            this.meterRegistry.counter("gateway.mcp.tool.timeouts", "tool", metricToolName).increment(); // ls
                        } // ls
                        LOGGER.warn("MCP tool execution failed tool={} error={}", metricToolName, gatewayError.getCode()); // ls
                        return Mono.just(this.errorResult(gatewayError.getMessage())); // ls
                    }); // ls
        });
    }

    /**
     * Converts a successful target API response to a structured MCP result.
     *
     * @param response target API response
     * @return structured success result
     */
    private McpSchema.CallToolResult successResult(ApiResponse response) {
        final Map<String, Object> structured = new java.util.LinkedHashMap<>();
        structured.put("statusCode", response.statusCode());
        if (response.body() != null) {
            structured.put("body", this.objectMapper.convertValue(response.body(), Object.class));
        }
        return McpSchema.CallToolResult.builder() // ls
                .structuredContent(structured) // ls
                .textContent(List.of(this.serialize(structured))) // ls
                .build();
    }

    /**
     * Creates an MCP error result without exposing target implementation details.
     *
     * @param message safe error message
     * @return error-marked MCP result
     */
    private McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder() // ls
                .textContent(List.of(message)) // ls
                .isError(true) // ls
                .build();
    }

    /**
     * Records a tool call counter and execution duration.
     *
     * @param sample   timer sample started at invocation
     * @param toolName normalized tool metric label
     * @param outcome  execution outcome label
     * @param status   HTTP status or zero when unavailable
     */
    private void record(Timer.Sample sample, String toolName, String outcome, int status) {
        final String statusTag = status == 0 ? "none" : Integer.toString(status);
        this.meterRegistry.counter("gateway.mcp.tool.calls", "tool", toolName, // ls
                "outcome", outcome, "status", statusTag).increment();
        sample.stop(Timer.builder("gateway.mcp.tool.duration") // ls
                .tag("tool", toolName) // ls
                .tag("outcome", outcome) // ls
                .register(this.meterRegistry));
    }

    /**
     * Serializes a result for the text representation of an MCP tool response.
     *
     * @param result structured result to serialize
     * @return JSON text
     */
    private String serialize(Object result) {
        try {
            return this.objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize MCP tool result", exception);
        }
    }

    /**
     * Retains classified gateway errors and masks unexpected failures.
     *
     * @param error failure raised during tool execution
     * @return safe gateway error
     */
    private static GatewayException safeError(Throwable error) {
        if (error instanceof GatewayException gatewayException) {
            return gatewayException;
        }
        return new GatewayException(GatewayException.Code.EXECUTION_FAILURE, // ls
                "Target API request could not be completed");
    }
}
