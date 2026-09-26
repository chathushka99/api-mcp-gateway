package com.example.mcp.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes a parameter accepted by a generated API tool.
 *
 * @param name     parameter name
 * @param location HTTP request location
 * @param required whether the parameter is mandatory
 * @param schema   JSON schema used to validate the value
 */
public record ParameterDefinition(String name, Location location, boolean required, JsonNode schema) {

    /**
     * Supported locations for OpenAPI parameters.
     */
    public enum Location {
        PATH, QUERY, HEADER
    }
}
