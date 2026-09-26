package com.example.mcp.registry;

import com.example.mcp.config.GatewayProperties;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.openapi.OpenApiSpecificationLoader;
import com.example.mcp.openapi.OpenApiToolGenerator;
import com.example.mcp.security.TargetUrlPolicy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maintains generated tools and exposes only operations enabled by policy.
 */
@Component
public class ToolRegistry {

    private final Map<String, GeneratedTool> tools = new LinkedHashMap<>();
    private final Map<String, String> operationIds = new LinkedHashMap<>();

    /**
     * Loads each configured API and registers its generated tools.
     *
     * @param properties      gateway API and operation configuration
     * @param loader          loader for OpenAPI documents
     * @param generator       generator for MCP tool definitions
     * @param targetUrlPolicy validator for configured API destinations
     */
    public ToolRegistry(GatewayProperties properties, OpenApiSpecificationLoader loader, // ls
                        OpenApiToolGenerator generator, TargetUrlPolicy targetUrlPolicy) {
        if (properties.getApis() == null || properties.getApis().isEmpty()) {
            throw new IllegalArgumentException("At least one gateway.apis entry must be configured");
        }
        final Map<String, Boolean> apiNames = new LinkedHashMap<>();
        for (final GatewayProperties.Api api : properties.getApis()) {
            if (api.getName() == null || api.getName().isBlank() || api.getOpenapi() == null || // ls
                    api.getBaseUrl() == null || api.getTimeout() == null || api.getTimeout().isZero() || // ls
                    api.getTimeout().isNegative()) {
                throw new IllegalArgumentException("Each configured API requires a name, base URL, OpenAPI source, and positive timeout");
            }
            if (apiNames.putIfAbsent(api.getName(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate API name: " + api.getName());
            }
            final URI baseUrl = targetUrlPolicy.validateConfiguredBaseUrl(api.getBaseUrl());
            final List<GeneratedTool> generated = generator.generate(api.getName(), baseUrl, api, // ls
                    loader.load(api.getOpenapi()).openApi(), properties.getOperations().isAllowMutating());
            this.registerAll(generated);
        }
    }

    /**
     * Atomically registers a batch after checking tool and operation identifiers.
     *
     * @param generated tools to register
     */
    public synchronized void registerAll(List<GeneratedTool> generated) {
        final Map<String, GeneratedTool> pendingTools = new LinkedHashMap<>();
        final Map<String, String> pendingOperationIds = new LinkedHashMap<>();
        for (final GeneratedTool tool : generated) {
            final GeneratedTool existing = this.tools.get(tool.name());
            if (existing != null || pendingTools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate MCP tool name: " + tool.name());
            }
            final String operationId = tool.operation().operationId();
            if (operationId != null && !operationId.isBlank()) {
                String priorApi = this.operationIds.get(operationId);
                if (priorApi == null) {
                    priorApi = pendingOperationIds.putIfAbsent(operationId, tool.operation().apiName());
                }
                if (priorApi != null) {
                    throw new IllegalArgumentException("Conflicting OpenAPI operationId '" + operationId + // ls
                            "' in APIs " + priorApi + " and " + tool.operation().apiName());
                }
            }
        }
        this.tools.putAll(pendingTools);
        this.operationIds.putAll(pendingOperationIds);
    }

    /**
     * Finds a tool when it exists and is currently enabled.
     *
     * @param name MCP tool name
     * @return matching enabled tool, if present
     */
    public Optional<GeneratedTool> find(String name) {
        final GeneratedTool tool = this.tools.get(name);
        if (tool == null || !isEnabled(tool)) {
            return Optional.empty();
        }
        return Optional.of(tool);
    }

    /**
     * Lists enabled tools in stable name order.
     *
     * @return enabled generated tools
     */
    public List<GeneratedTool> list() {
        return this.tools.values().stream().filter(ToolRegistry::isEnabled) // ls
                .sorted(Comparator.comparing(GeneratedTool::name)).toList();
    }

    /**
     * Finds enabled tools matching their name, description, or API name.
     *
     * @param query text to search, or {@code null} for an empty query
     * @return matching tools
     */
    public List<GeneratedTool> search(String query) {
        final String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return this.list().stream().filter(tool -> tool.name().contains(normalized) || // ls
                tool.description().toLowerCase(Locale.ROOT).contains(normalized) || // ls
                tool.operation().apiName().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    /**
     * Determines whether an operation is available under current policy.
     *
     * @param tool generated tool to evaluate
     * @return {@code true} for read operations or explicitly enabled mutations
     */
    private static boolean isEnabled(GeneratedTool tool) {
        return tool.operation().classification() == ApiOperationDefinition.Classification.READ || // ls
                tool.operation().mutatingAllowed();
    }
}
