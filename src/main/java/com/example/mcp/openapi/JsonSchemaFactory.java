package com.example.mcp.openapi;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Converts OpenAPI schemas into the JSON Schema subset used by MCP tools.
 */
public class JsonSchemaFactory {

    private final ObjectMapper objectMapper;

    /**
     * Creates a schema converter backed by a Jackson mapper.
     *
     * @param objectMapper mapper for JSON tree construction and serialization
     */
    public JsonSchemaFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts an OpenAPI schema while retaining supported constraints.
     *
     * @param schema source OpenAPI schema
     * @return corresponding JSON schema node
     */
    public JsonNode fromOpenApi(Schema<?> schema) {
        if (schema == null) {
            return this.objectMapper.createObjectNode();
        }
        final ObjectNode result = this.objectMapper.createObjectNode();
        final String type = schema.getType();
        if (type != null) {
            result.put("type", type);
        }
        if (schema.getFormat() != null) {
            result.put("format", schema.getFormat());
        }
        if (schema.getDescription() != null) {
            result.put("description", schema.getDescription());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            final ArrayNode values = result.putArray("enum");
            schema.getEnum().forEach(value -> values.add(this.objectMapper.valueToTree(value)));
        }
        if (schema.getMinimum() != null) {
            result.set("minimum", this.objectMapper.valueToTree(schema.getMinimum()));
        }
        if (schema.getMaximum() != null) {
            result.set("maximum", this.objectMapper.valueToTree(schema.getMaximum()));
        }
        if (schema.getMinLength() != null) {
            result.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            result.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            result.put("pattern", schema.getPattern());
        }
        if (schema.getItems() != null) {
            result.set("items", this.fromOpenApi(schema.getItems()));
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            final ObjectNode properties = result.putObject("properties");
            for (final Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
                properties.set(property.getKey(), this.fromOpenApi(property.getValue()));
            }
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                final ArrayNode required = result.putArray("required");
                schema.getRequired().forEach(required::add);
            }
        }
        if (schema.getAdditionalProperties() instanceof Boolean additionalProperties) {
            result.put("additionalProperties", additionalProperties);
        } else if (schema.getAdditionalProperties() instanceof Schema<?> additionalSchema) {
            result.set("additionalProperties", this.fromOpenApi(additionalSchema));
        }
        return result;
    }

    /**
     * Serializes a schema node for registration with MCP.
     *
     * @param schema schema to serialize
     * @return compact JSON representation
     */
    public String serialize(JsonNode schema) {
        try {
            return this.objectMapper.writeValueAsString(schema);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize generated JSON schema", exception);
        }
    }

    /**
     * Builds an object schema with the supplied properties and requirements.
     *
     * @param properties named property schemas
     * @param required   required property names
     * @return closed object schema
     */
    public JsonNode objectSchema(Map<String, JsonNode> properties, java.util.List<String> required) {
        final ObjectNode schema = this.objectMapper.createObjectNode().put("type", "object");
        final ObjectNode propertyNodes = schema.putObject("properties");
        properties.forEach(propertyNodes::set);
        if (!required.isEmpty()) {
            final ArrayNode requiredNodes = schema.putArray("required");
            required.forEach(requiredNodes::add);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * Wraps a response body schema in the gateway's status-and-body result shape.
     *
     * @param responseSchema target response body schema, if available
     * @return output schema accepted by MCP
     */
    public JsonNode outputSchema(JsonNode responseSchema) {
        final ObjectNode schema = this.objectMapper.createObjectNode().put("type", "object");
        final ObjectNode properties = schema.putObject("properties");
        properties.putObject("statusCode").put("type", "integer");
        properties.set("body", responseSchema == null || responseSchema.isMissingNode() || responseSchema.isNull() // ls
                ? this.objectMapper.createObjectNode() : responseSchema);
        schema.putArray("required").add("statusCode");
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * Returns a deep copy of a schema or an empty object schema.
     *
     * @param node schema node to copy, if present
     * @return copied schema node or an empty object
     */
    public JsonNode copyOrEmpty(JsonNode node) {
        return node == null ? this.objectMapper.createObjectNode() : node.deepCopy();
    }
}
