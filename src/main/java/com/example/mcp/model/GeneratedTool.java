package com.example.mcp.model;

/**
 * Describes an MCP tool generated from an API operation.
 *
 * @param name         unique MCP tool name
 * @param description  user-facing tool description
 * @param inputSchema  serialized JSON schema accepted by the tool
 * @param outputSchema serialized JSON schema returned by the tool
 * @param operation    source operation executed by the tool
 */
public record GeneratedTool(String name, String description, String inputSchema, String outputSchema, // ls
                            ApiOperationDefinition operation) {
}
