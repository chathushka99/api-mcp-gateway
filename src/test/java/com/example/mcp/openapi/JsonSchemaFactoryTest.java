package com.example.mcp.openapi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;

import io.swagger.v3.oas.models.media.Schema;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies supported OpenAPI constraints map into MCP-compatible JSON schemas.
 */
class JsonSchemaFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory factory = new JsonSchemaFactory(this.objectMapper);

    /**
     * Maps scalar, collection, object, and additional-property constraints.
     */
    @Test
    @DisplayName("Converts OpenAPI schema constraints and object properties")
    void convertsSchemaConstraints() {
        // prepare //
        final Schema<Object> stringSchema = new Schema<>();
        stringSchema.setType("string");
        stringSchema.setFormat("email");
        stringSchema.setDescription("Contact address");
        stringSchema.setEnum(List.of("one", "two"));
        stringSchema.setMinimum(new BigDecimal("1"));
        stringSchema.setMaximum(new BigDecimal("10"));
        stringSchema.setMinLength(2);
        stringSchema.setMaxLength(20);
        stringSchema.setPattern(".+@.+");

        final Schema<Object> objectSchema = new Schema<>();
        objectSchema.setType("object");
        objectSchema.setProperties(Map.of("email", stringSchema));
        objectSchema.setRequired(List.of("email"));
        objectSchema.setAdditionalProperties(true);

        // act //
        final JsonNode converted = this.factory.fromOpenApi(objectSchema);

        // assert //
        assertThat(converted.path("type").asText()).isEqualTo("object");
        assertThat(converted.path("properties").path("email").path("format").asText()).isEqualTo("email");
        assertThat(converted.path("properties").path("email").path("enum").size()).isEqualTo(2);
        assertThat(converted.path("properties").path("email").path("minimum").decimalValue()) // ls
                .isEqualByComparingTo("1");
        assertThat(converted.path("properties").path("email").path("maximum").decimalValue()) // ls
                .isEqualByComparingTo("10");
        assertThat(converted.path("properties").path("email").path("minLength").asInt()).isEqualTo(2);
        assertThat(converted.path("properties").path("email").path("maxLength").asInt()).isEqualTo(20);
        assertThat(converted.path("properties").path("email").path("pattern").asText()).isEqualTo(".+@.+");
        assertThat(converted.path("required")).containsExactly(this.objectMapper.getNodeFactory().textNode("email"));
        assertThat(converted.path("additionalProperties").asBoolean()).isTrue();
    }

    /**
     * Handles absent schemas and schema-valued additional properties.
     */
    @Test
    @DisplayName("Handles empty schemas and schema-valued additional properties")
    void handlesEmptyAndAdditionalPropertySchemas() {
        // prepare //
        final Schema<Object> objectSchema = new Schema<>();
        objectSchema.setAdditionalProperties(new Schema<>().type("integer"));
        final Schema<Object> closedSchema = new Schema<>();
        closedSchema.setAdditionalProperties(false);
        final Schema<Object> arraySchema = new Schema<>();
        arraySchema.setType("array");
        arraySchema.setItems(new Schema<>().type("string"));
        final Schema<Object> optionalObject = new Schema<>();
        optionalObject.setProperties(Map.of("optional", new Schema<>().type("string")));
        final Schema<Object> emptyProperties = new Schema<>();
        emptyProperties.setProperties(Map.of());
        final Schema<Object> emptyRequiredObject = new Schema<>();
        emptyRequiredObject.setProperties(Map.of("optional", new Schema<>().type("string")));
        emptyRequiredObject.setRequired(List.of());
        final Schema<Object> emptyEnum = new Schema<>();
        emptyEnum.setEnum(List.of());

        // act //
        final JsonNode empty = this.factory.fromOpenApi(null);
        final JsonNode converted = this.factory.fromOpenApi(objectSchema);
        final JsonNode closed = this.factory.fromOpenApi(closedSchema);
        final JsonNode array = this.factory.fromOpenApi(arraySchema);
        final JsonNode optional = this.factory.fromOpenApi(optionalObject);
        final JsonNode emptyPropertiesNode = this.factory.fromOpenApi(emptyProperties);
        final JsonNode emptyRequiredNode = this.factory.fromOpenApi(emptyRequiredObject);
        final JsonNode emptyEnumNode = this.factory.fromOpenApi(emptyEnum);

        // assert //
        assertThat(empty.isObject()).isTrue();
        assertThat(converted.path("additionalProperties").path("type").asText()).isEqualTo("integer");
        assertThat(closed.path("additionalProperties").asBoolean()).isFalse();
        assertThat(array.path("items").path("type").asText()).isEqualTo("string");
        assertThat(optional.path("required").isMissingNode()).isTrue();
        assertThat(emptyPropertiesNode.path("properties").isMissingNode()).isTrue();
        assertThat(emptyRequiredNode.path("required").isMissingNode()).isTrue();
        assertThat(emptyEnumNode.path("enum").isMissingNode()).isTrue();
    }

    /**
     * Covers schema serialization, object wrapping, response wrapping, and defensive copying.
     *
     * @throws Exception if a fixture cannot be serialized
     */
    @Test
    @DisplayName("Serializes, wraps, and copies schema nodes")
    void serializesAndWrapsSchemas() throws Exception {
        // prepare //
        final JsonNode fieldSchema = this.objectMapper.readTree("{\"type\":\"string\"}");
        final JsonNode source = this.objectMapper.readTree("{\"type\":\"object\"}");

        // act //
        final JsonNode objectSchema = this.factory.objectSchema(Map.of("name", fieldSchema), List.of("name"));
        final JsonNode emptyRequiredSchema = this.factory.objectSchema(Map.of(), List.of());
        final JsonNode withBody = this.factory.outputSchema(fieldSchema);
        final JsonNode withoutBody = this.factory.outputSchema(null);
        final JsonNode missingBody = this.factory.outputSchema(MissingNode.getInstance());
        final JsonNode nullBody = this.factory.outputSchema(NullNode.getInstance());
        final JsonNode copied = this.factory.copyOrEmpty(source);
        final JsonNode emptyCopy = this.factory.copyOrEmpty(null);

        // assert //
        assertThat(this.factory.serialize(objectSchema)).contains("\"name\"");
        assertThat(objectSchema.path("required").get(0).asText()).isEqualTo("name");
        assertThat(emptyRequiredSchema.path("required").isMissingNode()).isTrue();
        assertThat(withBody.path("properties").path("body").path("type").asText()).isEqualTo("string");
        assertThat(withoutBody.path("required").get(0).asText()).isEqualTo("statusCode");
        assertThat(missingBody.path("properties").path("body").isObject()).isTrue();
        assertThat(nullBody.path("properties").path("body").isObject()).isTrue();
        assertThat(copied).isNotSameAs(source);
        assertThat(emptyCopy.isObject()).isTrue();
    }
}
