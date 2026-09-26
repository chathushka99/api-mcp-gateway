package com.example.mcp.model;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes an API operation prepared for execution as an MCP tool.
 *
 * @param apiName             configured name of the API
 * @param operationId         OpenAPI operation identifier, if supplied
 * @param httpMethod          HTTP method to invoke
 * @param path                operation path relative to the API base URL
 * @param description         human-readable operation description
 * @param baseUrl             validated API base URL
 * @param parameters          operation path, query, and header parameters
 * @param requestBodySchema   JSON schema for the optional request body
 * @param requestBodyRequired whether a request body is mandatory
 * @param requestContentType  content type used for the request body
 * @param responseSchema      JSON schema for a successful response body
 * @param authentication      authentication values for the target API
 * @param timeout             maximum operation duration
 * @param classification      whether the operation is read-only or mutating
 * @param mutatingAllowed     whether mutation execution is enabled
 * @param allowedHeaders      headers permitted for the operation
 */
public record ApiOperationDefinition( // ls
        String apiName, // ls
        String operationId, // ls
        String httpMethod, // ls
        String path, // ls
        String description, // ls
        URI baseUrl, // ls
        List<ParameterDefinition> parameters, // ls
        JsonNode requestBodySchema, // ls
        boolean requestBodyRequired, // ls
        String requestContentType, // ls
        JsonNode responseSchema, // ls
        AuthenticationConfiguration authentication, // ls
        Duration timeout, // ls
        Classification classification, // ls
        boolean mutatingAllowed, // ls
        List<String> allowedHeaders) {

    /**
     * Indicates whether an operation only reads data or may change it.
     */
    public enum Classification {
        READ, MUTATING
    }
}
