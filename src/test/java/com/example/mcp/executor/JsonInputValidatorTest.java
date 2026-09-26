package com.example.mcp.executor;

import com.example.mcp.exception.GatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies generated JSON schemas reject invalid tool arguments.
 */
class JsonInputValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonInputValidator validator = new JsonInputValidator();

    /**
     * Checks that missing required and unexpected properties are rejected.
     *
     * @throws Exception if the test schema cannot be parsed
     */
    @Test
    @DisplayName("Rejects missing required and unexpected properties")
    void rejectsMissingRequiredAndUnexpectedProperties() throws Exception {
        // prepare //
        final JsonNode schema = this.objectMapper.readTree("""
                {"type":"object","properties":{"id":{"type":"string"}},"required":["id"],"additionalProperties":false}
                """);

        // act //
        // assert //
        assertThatThrownBy(() -> this.validator.validate(schema, this.objectMapper.createObjectNode())) // ls
                .isInstanceOf(GatewayException.class).hasMessage("Missing required argument: id");
        assertThatThrownBy(() -> this.validator.validate(schema, this.objectMapper.readTree("""
                {"id":"1","unexpected":true}
                """))).isInstanceOf(GatewayException.class).hasMessage("Unknown argument: unexpected");
    }

    /**
     * Checks that nested value types and enum constraints are enforced.
     *
     * @throws Exception if the test schema cannot be parsed
     */
    @Test
    @DisplayName("Validates nested request body types and enums")
    void validatesNestedRequestBodyTypesAndEnums() throws Exception {
        // prepare //
        final JsonNode schema = this.objectMapper.readTree("""
                {"type":"object","properties":{"mode":{"type":"string","enum":["safe","full"]}}}
                """);

        // act //
        // assert //
        assertThatThrownBy(() -> this.validator.validate(schema, this.objectMapper.readTree("""
                {"mode":4}
                """))).isInstanceOf(GatewayException.class).hasMessage("mode is not an allowed value");
    }

    /**
     * Exercises supported JSON types and string, number, array, and nested-object constraints.
     *
     * @throws Exception if a fixture cannot be parsed
     */
    @Test
    @DisplayName("Accepts supported JSON values and enforces schema constraints")
    void validatesSupportedTypesAndConstraints() throws Exception {
        // prepare //
        final JsonNode objectSchema = this.objectMapper.readTree("""
                {"type":"object","properties":{"items":{"type":"array","items":{"type":"integer"}}}}
                """);
        final JsonNode stringSchema = this.objectMapper.readTree("""
                {"type":"string","minLength":2,"maxLength":5,"pattern":"[a-z]+"}
                """);
        final JsonNode numberSchema = this.objectMapper.readTree("""
                {"type":"number","minimum":1,"maximum":3}
                """);
        final JsonNode enumSchema = this.objectMapper.readTree("""
                {"enum":["ready","done"]}
                """);

        // act //
        this.validator.validate(objectSchema, this.objectMapper.readTree("{\"items\":[1,2]}"));
        this.validator.validate(this.objectMapper.readTree("{\"type\":\"integer\"}"), this.objectMapper.readTree("2"));
        this.validator.validate(this.objectMapper.readTree("{\"type\":\"boolean\"}"), this.objectMapper.readTree("true"));
        this.validator.validate(this.objectMapper.readTree("{\"type\":\"null\"}"), this.objectMapper.readTree("null"));
        this.validator.validate(this.objectMapper.readTree("{}"), this.objectMapper.readTree("\"any\""));
        this.validator.validate(enumSchema, this.objectMapper.readTree("\"ready\""));
        this.validator.validate(stringSchema, this.objectMapper.readTree("\"abcd\""));
        this.validator.validate(numberSchema, this.objectMapper.readTree("2"));
        this.validator.validate(this.objectMapper.readTree("{}"), null);

        // assert //
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"unknown\"}"), this.objectMapper.readTree("1"))) // ls
                .isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"string\",\"minLength\":3}"), // ls
                this.objectMapper.readTree("\"ab\""))).hasMessage("arguments is shorter than allowed");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"string\",\"maxLength\":1}"), // ls
                this.objectMapper.readTree("\"ab\""))).hasMessage("arguments is longer than allowed");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"string\",\"pattern\":\"[0-9]+\"}"), // ls
                this.objectMapper.readTree("\"abc\""))).hasMessage("arguments has an invalid format");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"number\",\"minimum\":2}"), // ls
                this.objectMapper.readTree("1"))).hasMessage("arguments is below the allowed minimum");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"number\",\"maximum\":2}"), // ls
                this.objectMapper.readTree("3"))).hasMessage("arguments is above the allowed maximum");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}"), // ls
                this.objectMapper.readTree("[1]"))).hasMessage("arguments[0] must be of type string");
        assertThatCode(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"object\",\"properties\":{\"known\":{\"type\":\"string\"}}}"), // ls
                this.objectMapper.readTree("{\"known\":\"value\"}"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"object\"}"), this.objectMapper.readTree("[]"))) // ls
                .hasMessage("arguments must be of type object");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"array\"}"), this.objectMapper.readTree("{}"))) // ls
                .hasMessage("arguments must be of type array");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"string\"}"), this.objectMapper.readTree("1"))) // ls
                .hasMessage("arguments must be of type string");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"integer\"}"), this.objectMapper.readTree("1.5"))) // ls
                .hasMessage("arguments must be of type integer");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"number\"}"), this.objectMapper.readTree("\"1\""))) // ls
                .hasMessage("arguments must be of type number");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"boolean\"}"), this.objectMapper.readTree("1"))) // ls
                .hasMessage("arguments must be of type boolean");
        assertThatThrownBy(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"null\"}"), this.objectMapper.readTree("\"not-null\""))) // ls
                .hasMessage("arguments must be of type null");
        assertThatCode(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"enum\":[]}"), this.objectMapper.readTree("\"anything\""))) // ls
                .doesNotThrowAnyException();
        assertThatCode(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"object\",\"additionalProperties\":true}"), // ls
                this.objectMapper.readTree("{\"unknown\":true}"))).doesNotThrowAnyException();
        assertThatCode(() -> this.validator.validate( // ls
                this.objectMapper.readTree( // ls
                        "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"), // ls
                this.objectMapper.readTree("{\"dynamic\":\"value\"}"))).doesNotThrowAnyException();
        assertThatCode(() -> this.validator.validate( // ls
                this.objectMapper.readTree("{\"type\":\"array\"}"), this.objectMapper.readTree("[]"))) // ls
                .doesNotThrowAnyException();
        assertThatCode(() -> this.validator.validate(null, this.objectMapper.readTree("{}"))) // ls
                .doesNotThrowAnyException();
        assertThatCode(() -> this.validator.validate( // ls
                MissingNode.getInstance(), this.objectMapper.readTree("{}"))).doesNotThrowAnyException();
        assertThatCode(() -> this.validator.validate( // ls
                NullNode.getInstance(), this.objectMapper.readTree("{}"))).doesNotThrowAnyException();
        for (final String type : List.of("object", "array", "string", "integer", "number", "boolean")) {
            assertThatThrownBy(() -> this.validator.validate( // ls
                    this.objectMapper.createObjectNode().put("type", type), null))
                    .hasMessage("arguments must be of type " + type);
        }
        assertThatCode(() -> this.validator.validate( // ls
                this.objectMapper.createObjectNode().put("type", "null"), null)).doesNotThrowAnyException();
    }
}
