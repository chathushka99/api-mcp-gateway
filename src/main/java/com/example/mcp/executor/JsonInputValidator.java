package com.example.mcp.executor;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.mcp.exception.GatewayException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Applies the supported JSON Schema constraints to tool arguments.
 */
@Component
public class JsonInputValidator {

    /**
     * Validates a tool argument value against its generated schema.
     *
     * @param schema JSON schema describing the allowed arguments
     * @param value  supplied argument value
     */
    public void validate(JsonNode schema, JsonNode value) {
        this.validateValue(schema, value, "arguments");
    }

    /**
     * Recursively validates a value and reports the failing property path.
     *
     * @param schema   schema for the current value
     * @param value    current JSON value
     * @param location human-readable argument path
     */
    private void validateValue(JsonNode schema, JsonNode value, String location) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }
        final JsonNode enumValues = schema.get("enum");
        if (enumValues != null && !enumValues.isEmpty()) {
            boolean matches = false;
            for (final JsonNode allowed : enumValues) {
                if (allowed.equals(value)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                throw invalid(location + " is not an allowed value");
            }
        }
        final String type = schema.path("type").asText("");
        final boolean typeMatches = switch (type) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber();
            case "boolean" -> value != null && value.isBoolean();
            case "null" -> value == null || value.isNull();
            case "" -> true;
            default -> false;
        };
        if (!typeMatches) {
            throw invalid(location + " must be of type " + type);
        }
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isObject()) {
            final JsonNode properties = schema.path("properties");
            final JsonNode required = schema.path("required");
            if (required.isArray()) {
                for (final JsonNode property : required) {
                    if (!value.has(property.asText())) {
                        throw invalid("Missing required argument: " + property.asText());
                    }
                }
            }
            final Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                final JsonNode propertySchema = properties.get(field.getKey());
                if (propertySchema == null && schema.path("additionalProperties").isBoolean() && // ls
                        !schema.path("additionalProperties").asBoolean()) {
                    throw invalid("Unknown argument: " + field.getKey());
                }
                if (propertySchema != null) {
                    this.validateValue(propertySchema, field.getValue(), field.getKey());
                }
            }
        }
        if (value.isArray() && schema.has("items")) {
            for (int index = 0; index < value.size(); index++) {
                this.validateValue(schema.get("items"), value.get(index), location + "[" + index + "]");
            }
        }
        if (value.isTextual()) {
            final String text = value.asText();
            if (schema.has("minLength") && text.length() < schema.path("minLength").asInt()) {
                throw invalid(location + " is shorter than allowed");
            }
            if (schema.has("maxLength") && text.length() > schema.path("maxLength").asInt()) {
                throw invalid(location + " is longer than allowed");
            }
            if (schema.has("pattern") && !Pattern.matches(schema.path("pattern").asText(), text)) {
                throw invalid(location + " has an invalid format");
            }
        }
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0) {
                throw invalid(location + " is below the allowed minimum");
            }
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.path("maximum").decimalValue()) > 0) {
                throw invalid(location + " is above the allowed maximum");
            }
        }
    }

    /**
     * Creates an invalid-arguments exception for a validation failure.
     *
     * @param message explanation of the rejected value
     * @return classified validation exception
     */
    private static GatewayException invalid(String message) {
        return new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, message);
    }
}
