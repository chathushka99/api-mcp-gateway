package com.example.mcp.openapi;

import com.example.mcp.config.GatewayProperties;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies OpenAPI operations are converted into safe MCP tool definitions.
 */
class OpenApiToolGeneratorTest {

    @TempDir
    private Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiSpecificationLoader loader = // ls
            new OpenApiSpecificationLoader(new DefaultResourceLoader());
    private final OpenApiToolGenerator generator = new OpenApiToolGenerator(this.objectMapper);

    /**
     * Verifies an OpenAPI operation produces the expected MCP schema.
     *
     * @throws Exception if the specification or generated schema cannot be parsed
     */
    @Test
    @DisplayName("Generates an MCP tool schema from the specification")
    void parsesSpecificationAndGeneratesMcpToolSchema() throws Exception {
        // prepare //
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setAllowedHeaders(java.util.List.of());

        // act //
        final LoadedOpenApi loaded = this.loader.load("classpath:openapi/example-api.yaml");
        final GeneratedTool tool = this.generator.generate("example-service", URI.create("https://api.example.com"), api, // ls
                loaded.openApi(), false).getFirst();

        // assert //
        assertThat(tool.name()).isEqualTo("get_user");
        assertThat(tool.description()).isEqualTo("Get a user by ID.");
        final JsonNode schema = this.objectMapper.readTree(tool.inputSchema());
        assertThat(schema.path("properties").path("userId").path("type").asText()).isEqualTo("string");
        assertThat(schema.path("required").toString()).contains("userId");
        assertThat(this.objectMapper.readTree(tool.outputSchema()).path("properties").has("body")).isTrue();
    }

    /**
     * Verifies a safe tool name is derived when operationId is absent.
     *
     * @throws Exception if the temporary specification cannot be written or parsed
     */
    @Test
    @DisplayName("Derives a safe name when operation ID is missing")
    void createsSafeNameWhenOperationIdIsMissing() throws Exception {
        // prepare //
        final String document = """
                openapi: 3.0.3
                info:
                  title: Example
                  version: 1
                paths:
                  /accounts/{accountId}:
                    get:
                      parameters:
                        - name: accountId
                          in: path
                          required: true
                          schema:
                            type: string
                      responses:
                        "200":
                          description: OK
                """;
        final java.nio.file.Path spec = java.nio.file.Files.createTempFile("openapi", ".yaml");
        try {
            java.nio.file.Files.writeString(spec, document);
            final GatewayProperties.Api api = new GatewayProperties.Api();
            api.setAllowedHeaders(java.util.List.of());

            // act //
            final LoadedOpenApi loaded = this.loader.load("file:" + spec.toAbsolutePath());
            final GeneratedTool tool = this.generator.generate("account-service", URI.create("https://api.example.com"), api, // ls
                    loaded.openApi(), false).getFirst();

            // assert //
            assertThat(tool.name()).isEqualTo("get_account");
        } finally {
            java.nio.file.Files.deleteIfExists(spec);
        }
    }

    /**
     * Exercises operation parameters, auth configuration, request bodies, and response media types.
     *
     * @throws Exception if the specification fixture cannot be written or parsed
     */
    @Test
    @DisplayName("Generates tools for path, query, header, body, and response definitions")
    void generatesToolsForSupportedOperationShapes() throws Exception {
        // prepare //
        final String document = """
                openapi: 3.0.3
                info:
                  title: Widget API
                  version: 1.0
                paths:
                  /widgets/{widgetId}:
                    parameters:
                      - name: widgetId
                        in: path
                        required: true
                        schema:
                          type: string
                    get:
                      operationId: getWidget
                      summary: Fetch a widget
                      parameters:
                        - name: filter
                          in: query
                          schema:
                            type: string
                        - name: X-Tenant
                          in: header
                          schema:
                            type: string
                      responses:
                        "200":
                          description: Widget
                          content:
                            application/json:
                              schema:
                                type: object
                    post:
                      operationId: updateWidget
                      requestBody:
                        required: true
                        content:
                          application/problem+json:
                            schema:
                              type: object
                      responses:
                        "201":
                          description: Updated
                          content:
                            text/plain:
                              schema:
                                type: string
                  /widgets:
                    delete:
                      responses:
                        "204":
                          description: Removed
                  /long-name:
                    get:
                      operationId: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                      responses:
                        "200":
                          description: Long name
                """;
        final Path specification = this.temporaryDirectory.resolve("widgets.yaml");
        Files.writeString(specification, document);
        final LoadedOpenApi loaded = this.loader.load("file:" + specification.toAbsolutePath());
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setAllowedHeaders(List.of("X-Tenant"));
        api.getAuth().setType("api-key");
        api.getAuth().setCredential("fixture-key");
        api.getAuth().setLocation("query");
        api.getAuth().setParameterName("token");

        // act //
        final List<GeneratedTool> tools = this.generator.generate( // ls
                "widget-service", URI.create("https://api.example.com"), api, loaded.openApi(), true);

        // assert //
        assertThat(tools).extracting(GeneratedTool::name) // ls
                .containsExactly("get_widget", "update_widget", "delete_widgets", // ls
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(tools.get(0).operation().parameters()).hasSize(3);
        assertThat(tools.get(0).operation().allowedHeaders()).containsExactly("x-tenant");
        assertThat(tools.get(0).operation().authentication().name()).isEqualTo("token");
        assertThat(tools.get(1).operation().requestBodyRequired()).isTrue();
        assertThat(tools.get(1).operation().requestContentType()).isEqualTo("application/problem+json");
        assertThat(tools.get(1).operation().responseSchema().path("type").asText()).isEqualTo("string");
        assertThat(tools.get(2).operation().classification()).isEqualTo(Classification.MUTATING);
        assertThat(tools.get(2).description()).isEqualTo("DELETE /widgets.");

        api.setAllowedHeaders(List.of());
        assertThatThrownBy(() -> this.generator.generate( // ls
                "widget-service", URI.create("https://api.example.com"), api, loaded.openApi(), true)) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessageContaining("header parameter is not explicitly allow-listed");
        api.setAllowedHeaders(List.of("X-Tenant"));
        api.getAuth().setLocation("header");
        api.getAuth().setHeaderName("X-Tenant");
        assertThatThrownBy(() -> this.generator.generate( // ls
                "widget-service", URI.create("https://api.example.com"), api, loaded.openApi(), true)) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessage("API key authentication conflicts with a documented parameter");
    }

    /**
     * Rejects conflicting credentials and unsafe or incomplete API-key settings.
     *
     * @throws Exception if the example specification cannot be loaded
     */
    @Test
    @DisplayName("Rejects unsafe and conflicting authentication configurations")
    void rejectsInvalidAuthenticationSettings() throws Exception {
        // prepare //
        final LoadedOpenApi loaded = this.loader.load("classpath:openapi/example-api.yaml");
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setAllowedHeaders(List.of());
        api.getAuth().setType("bearer");
        api.getAuth().setCredential("bad\ncredential");

        // act //
        // assert //
        assertThatThrownBy(() -> this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class);

        api.getAuth().setType("api-key");
        api.getAuth().setCredential("fixture-key");
        api.getAuth().setLocation("query");
        api.getAuth().setParameterName(" ");
        assertThatThrownBy(() -> this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class);

        api.getAuth().setLocation("header");
        api.getAuth().setHeaderName("Authorization");
        assertThatThrownBy(() -> this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class);

        api.getAuth().setHeaderName("X-Key");
        api.getAuth().setCredential("bad\ncredential");
        assertThatThrownBy(() -> this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class);

        api.getAuth().setType("custom");
        assertThatThrownBy(() -> this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class);

        api.getAuth().setType("bearer");
        api.getAuth().setCredential("fixture-token");
        assertThat(this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .hasSize(1);
        api.getAuth().setType("api-key");
        api.getAuth().setLocation("header");
        api.getAuth().setHeaderName("X-Key");
        api.getAuth().setCredential("fixture-key");
        assertThat(this.generator.generate( // ls
                "example-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .hasSize(1);
    }

    /**
     * Rejects request bodies whose declared media type cannot be sent as JSON.
     *
     * @throws Exception if the OpenAPI fixture cannot be written or parsed
     */
    @Test
    @DisplayName("Rejects unsupported request-body media types")
    void rejectsUnsupportedRequestBodyMediaTypes() throws Exception {
        // prepare //
        final Path specification = this.temporaryDirectory.resolve("unsupported-body.yaml");
        Files.writeString(specification, """
                openapi: 3.0.3
                info:
                  title: Unsupported body
                  version: 1
                paths:
                  /files:
                    post:
                      requestBody:
                        content:
                          text/plain:
                            schema:
                              type: string
                      responses:
                        "201":
                          description: Uploaded
                """);
        final LoadedOpenApi loaded = this.loader.load("file:" + specification.toAbsolutePath());
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setAllowedHeaders(List.of());

        // act //
        // assert //
        assertThatThrownBy(() -> this.generator.generate( // ls
                "file-service", URI.create("https://api.example.com"), api, loaded.openApi(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JSON request bodies are supported");
    }

    /**
     * Exercises fallback descriptions, punctuation, and invalid normalized tool names.
     *
     * @throws Exception if the OpenAPI fixture cannot be written or parsed
     */
    @Test
    @DisplayName("Preserves descriptions and rejects unsafe generated names")
    void preservesDescriptionsAndRejectsUnsafeNames() throws Exception {
        // prepare //
        final Path specification = this.temporaryDirectory.resolve("descriptions.yaml");
        Files.writeString(specification, """
                openapi: 3.0.3
                info:
                  title: Description cases
                  version: 1
                paths:
                  /bang:
                    get:
                      operationId: bang
                      summary: Pay!
                      responses:
                        "200":
                          description: OK
                  /question:
                    get:
                      operationId: question
                      summary: Are you ready?
                      responses:
                        "200":
                          description: OK
                  /blank:
                    get:
                      responses:
                        "200":
                          description: OK
                """);
        final LoadedOpenApi loaded = this.loader.load("file:" + specification.toAbsolutePath());
        loaded.openApi().getPaths().get("/blank").setParameters(null);
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setAllowedHeaders(null);
        api.getAuth().setType(null);

        // act //
        final List<GeneratedTool> tools = this.generator.generate( // ls
                "description-service", URI.create("https://api.example.com"), api, loaded.openApi(), false);

        // assert //
        assertThat(tools).extracting(GeneratedTool::description) // ls
                .containsExactly("Pay!", "Are you ready?", "GET /blank.");
        final List<io.swagger.v3.oas.models.Operation> operations = loaded.openApi().getPaths().values().stream() // ls
                .map(pathItem -> pathItem.readOperations().getFirst()) // ls
                .toList();
        operations.getFirst().setOperationId("!!!");
        assertThatThrownBy(() -> this.generator.generate( // ls
                "description-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAPI operation cannot be converted to a safe MCP tool name");
    }

    /**
     * Rejects parameter locations that cannot be exposed as MCP tool inputs.
     *
     * @throws Exception if the OpenAPI fixture cannot be written or parsed
     */
    @Test
    @DisplayName("Rejects unsupported parameter locations")
    void rejectsUnsupportedParameterLocations() throws Exception {
        // prepare //
        final Path specification = this.temporaryDirectory.resolve("cookie-parameter.yaml");
        Files.writeString(specification, """
                openapi: 3.0.3
                info:
                  title: Cookie parameter
                  version: 1
                paths:
                  /session:
                    get:
                      operationId: getSession
                      parameters:
                        - name: session
                          in: cookie
                          schema:
                            type: string
                      responses:
                        "200":
                          description: OK
                """);
        final LoadedOpenApi loaded = this.loader.load("file:" + specification.toAbsolutePath());
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setAllowedHeaders(List.of());

        // act //
        // assert //
        assertThatThrownBy(() -> this.generator.generate( // ls
                "session-service", URI.create("https://api.example.com"), api, loaded.openApi(), false)) // ls
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported parameter location: cookie");
    }
}
