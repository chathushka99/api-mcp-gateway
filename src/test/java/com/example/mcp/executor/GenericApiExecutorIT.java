package com.example.mcp.executor;

import com.example.mcp.auth.AuthenticationProviderRegistry;
import com.example.mcp.auth.BearerTokenAuthenticationProvider;
import com.example.mcp.auth.NoAuthenticationProvider;
import com.example.mcp.auth.StaticApiKeyAuthenticationProvider;
import com.example.mcp.config.GatewayProperties;
import com.example.mcp.exception.GatewayException;
import com.example.mcp.mcp.McpToolHandler;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.model.ParameterDefinition;
import com.example.mcp.model.ParameterDefinition.Location;
import com.example.mcp.openapi.OpenApiSpecificationLoader;
import com.example.mcp.openapi.OpenApiToolGenerator;
import com.example.mcp.registry.ToolRegistry;
import com.example.mcp.security.TargetUrlPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies API execution, validation, error handling, and request limits end to end.
 */
class GenericApiExecutorIT {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockWebServer server = new MockWebServer();

    private GatewayProperties properties;
    private ToolRegistry registry;
    private GenericApiExecutor executor;
    private McpToolHandler handler;

    /**
     * Starts the mock target and initializes the gateway test fixture.
     *
     * @throws Exception if the mock server cannot start
     */
    @BeforeEach
    void setUp() throws Exception {
        // prepare //
        this.server.start();
        this.properties = new GatewayProperties();
        this.properties.getSecurity().setAllowedHosts(List.of("localhost"));
        this.properties.getSecurity().setAllowPrivateTargets(true);
        this.properties.getLimits().setConnectTimeout(Duration.ofSeconds(2));
        this.properties.getLimits().setMaxResponseBodyBytes(1024 * 1024);
        final GatewayProperties.Api api = new GatewayProperties.Api();
        api.setName("integration-api");
        api.setBaseUrl(this.server.url("/").toString().replaceFirst("/$", ""));
        api.setOpenapi("classpath:openapi/integration-api.yaml");
        api.setTimeout(Duration.ofSeconds(3));
        api.setAllowedHeaders(List.of("X-Tenant", "X-Tags"));
        this.properties.setApis(List.of(api));

        final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        final OpenApiSpecificationLoader loader = new OpenApiSpecificationLoader(resourceLoader);
        final OpenApiToolGenerator generator = new OpenApiToolGenerator(this.objectMapper);
        final TargetUrlPolicy targetPolicy = new TargetUrlPolicy(this.properties);
        this.registry = new ToolRegistry(this.properties, loader, generator, targetPolicy);
        final AuthenticationProviderRegistry authProviders = new AuthenticationProviderRegistry(new NoAuthenticationProvider(), // ls
                new StaticApiKeyAuthenticationProvider(), new BearerTokenAuthenticationProvider());
        final WebClient webClient = WebClient.builder().build();
        this.executor = new GenericApiExecutor(webClient, this.objectMapper, authProviders, targetPolicy, // ls
                new JsonInputValidator(), this.properties);
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());
    }

    /**
     * Stops the mock target after each test.
     */
    @AfterEach
    void tearDown() {
        // prepare //
        this.server.close();
    }

    /**
     * Verifies an MCP tool request reaches the target with its path and query values.
     *
     * @throws Exception if the mock request cannot be retrieved
     */
    @Test
    @DisplayName("Forwards path and query parameters to the configured API")
    void generatedMcpToolCallsTheConfiguredApiWithPathAndQueryParameters() throws Exception {
        // prepare //
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "application/json") // ls
                .body("{\"id\":\"123\",\"name\":\"Ada\"}").build());

        // act //
        final GeneratedTool tool = this.registry.find("get_user").orElseThrow();
        final McpSchema.CallToolResult result = this.handler.handle(tool.name(), Map.of("userId", "123", "expand", "profile")).block();

        // verify //
        final mockwebserver3.RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);

        // assert //
        assertThat(tool.description()).isEqualTo("Get user details.");
        assertThat(result).isNotNull();
        assertThat(result.structuredContent()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result.structuredContent()).get("statusCode")).isEqualTo(200);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getUrl().encodedPath()).isEqualTo("/users/123");
        assertThat(request.getUrl().queryParameter("expand")).isEqualTo("profile");
    }

    /**
     * Verifies API-key query authentication, array query values, allow-listed headers, and text responses.
     *
     * @throws Exception if the mock request cannot be retrieved
     */
    @Test
    @DisplayName("Forwards array queries, allow-listed headers, and query authentication")
    void forwardsArrayQueriesHeadersAndQueryCredentials() throws Exception {
        // prepare //
        final GatewayProperties.Api api = this.properties.getApis().getFirst();
        api.getAuth().setType("api-key");
        api.getAuth().setLocation("query");
        api.getAuth().setParameterName("token");
        api.getAuth().setCredential("fixture-key");
        this.registry = new ToolRegistry(this.properties, new OpenApiSpecificationLoader(new DefaultResourceLoader()), // ls
                new OpenApiToolGenerator(this.objectMapper), new TargetUrlPolicy(this.properties));
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "text/plain") // ls
                .body("accepted").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle( // ls
                "get_user", Map.of( // ls
                        "userId", "123", "labels", List.of("one", "two"), // ls
                        "X-Tenant", "tenant-1", "X-Tags", List.of("red", "blue"))).block();

        // verify //
        final mockwebserver3.RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(request).isNotNull();
        assertThat(request.getHeaders().get("X-Tenant")).isEqualTo("tenant-1");
        assertThat(request.getHeaders().get("X-Tags")).isEqualTo("red,blue");
        assertThat(request.getUrl().queryParameterValues("labels")).containsExactly("one", "two");
        assertThat(request.getUrl().queryParameter("token")).isEqualTo("fixture-key");
        assertThat(result.structuredContent().toString()).contains("accepted");
    }

    /**
     * Preserves successful empty bodies and base64-encodes non-textual target content.
     */
    @Test
    @DisplayName("Handles empty and binary successful responses")
    void handlesEmptyAndBinaryResponses() {
        // prepare //
        this.server.enqueue(new MockResponse.Builder().code(204).build());
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "application/octet-stream") // ls
                .body("binary").build());

        // act //
        final McpSchema.CallToolResult empty = this.handler.handle("get_user", Map.of("userId", "empty")).block();
        final McpSchema.CallToolResult binary = this.handler.handle("get_user", Map.of("userId", "binary")).block();

        // assert //
        assertThat(empty).isNotNull();
        assertThat(empty.isError()).isFalse();
        assertThat(empty.structuredContent().toString()).contains("204").doesNotContain("body");
        assertThat(binary).isNotNull();
        assertThat(binary.isError()).isFalse();
        assertThat(binary.structuredContent().toString()).contains("YmluYXJ5");
    }

    /**
     * Verifies target server failures are classified without forwarding their response bodies.
     */
    @Test
    @DisplayName("Hides target server error bodies")
    void hidesTargetServerErrorBodies() {
        // prepare //
        this.server.enqueue(new MockResponse.Builder().code(503) // ls
                .addHeader("Content-Type", "text/plain") // ls
                .body("private server detail").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("get_user", Map.of("userId", "503")).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("HTTP 503").doesNotContain("private server detail");
    }

    /**
     * Verifies connection failures map to a stable safe error category.
     *
     * @throws Exception if the mock server cannot be closed
     */
    @Test
    @DisplayName("Maps connection failures to a safe error")
    void mapsConnectionFailures() throws Exception {
        // prepare //
        this.server.close();

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("get_user", Map.of("userId", "123")).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("Unable to connect to target API");
    }

    /**
     * Rejects header input that is absent from the operation's explicit allow-list.
     *
     * @throws Exception if the input fixture cannot be parsed
     */
    @Test
    @DisplayName("Rejects non-allow-listed header arguments")
    void rejectsNonAllowListedHeaders() throws Exception {
        // prepare //
        final ApiOperationDefinition source = this.registry.find("get_user").orElseThrow().operation();
        final ParameterDefinition forbiddenHeader = new ParameterDefinition( // ls
                "X-Forbidden", Location.HEADER, false, this.objectMapper.readTree("{\"type\":\"string\"}"));
        final List<ParameterDefinition> parameters = new ArrayList<>(source.parameters());
        parameters.add(forbiddenHeader);
        final ApiOperationDefinition operation = new ApiOperationDefinition( // ls
                source.apiName(), source.operationId(), source.httpMethod(), source.path(), source.description(), // ls
                source.baseUrl(), parameters, source.requestBodySchema(), source.requestBodyRequired(), // ls
                source.requestContentType(), source.responseSchema(), source.authentication(), source.timeout(), // ls
                Classification.READ, false, List.of());

        // act //
        // assert //
        assertThatThrownBy(() -> this.executor.execute( // ls
                operation, this.objectMapper.readTree("{\"userId\":\"123\",\"X-Forbidden\":\"no\"}")).block())
                .isInstanceOf(GatewayException.class)
                .hasMessage("Header parameter is not allow-listed: X-Forbidden");
        assertThat(this.server.getRequestCount()).isZero();
    }

    /**
     * Verifies disabled mutating operations never reach the target API.
     */
    @Test
    @DisplayName("Rejects mutating operations when disabled")
    void rejectsMutatingOperationsWhenDisabled() {
        // prepare //
        final GeneratedTool createUser = new OpenApiToolGenerator(this.objectMapper).generate("integration-api", // ls
                        URI.create(this.properties.getApis().getFirst().getBaseUrl()), this.properties.getApis().getFirst(), // ls
                        new OpenApiSpecificationLoader(new DefaultResourceLoader()) // ls
                                .load("classpath:openapi/integration-api.yaml").openApi(), false) // ls
                .stream().filter(tool -> tool.name().equals("create_user")).findFirst().orElseThrow();

        // act //
        // assert //
        assertThat(this.registry.find("create_user")).isEmpty();
        assertThatThrownBy(() -> this.executor.execute(createUser.operation(), // ls
                this.objectMapper.valueToTree(Map.of("body", Map.of("name", "Ada")))).block()) // ls
                .isInstanceOf(GatewayException.class) // ls
                .hasMessage("Mutating API operations are disabled");
        assertThat(this.server.getRequestCount()).isZero();
    }

    /**
     * Verifies unsafe path segments are rejected before an outbound request.
     */
    @Test
    @DisplayName("Rejects invalid path segments before calling the target")
    void rejectsInvalidPathSegmentsBeforeCallingTheTarget() {
        // prepare //

        // act //
        final List<String> pathValues = List.of("../admin", ".", "..", "bad\\path", "bad%path", "invalid\nsegment");
        final List<McpSchema.CallToolResult> results = pathValues.stream() // ls
                .map(pathValue -> this.handler.handle("get_user", Map.of("userId", pathValue)).block()) // ls
                .toList();

        // assert //
        assertThat(results).allSatisfy(result -> assertThat(result).isNotNull().extracting(
                McpSchema.CallToolResult::isError).isEqualTo(true));
        assertThat(this.server.getRequestCount()).isZero();
    }

    /**
     * Rejects null and non-object arguments before any request is built.
     */
    @Test
    @DisplayName("Rejects null executor arguments")
    void rejectsNullExecutorArguments() {
        // prepare //
        final ApiOperationDefinition operation = this.registry.find("get_user").orElseThrow().operation();

        // act //
        // assert //
        assertThatThrownBy(() -> this.executor.execute(operation, null).block())
                .isInstanceOf(GatewayException.class)
                .hasMessage("Tool arguments must be a JSON object");
        assertThat(this.server.getRequestCount()).isZero();
    }

    /**
     * Verifies enabled mutations send the declared JSON request body.
     *
     * @throws Exception if the mock request cannot be retrieved
     */
    @Test
    @DisplayName("Sends JSON request bodies when mutations are enabled")
    void serializesJsonBodiesWhenMutationsAreEnabled() throws Exception {
        // prepare //
        this.properties.getOperations().setAllowMutating(true);
        this.registry = new ToolRegistry(this.properties, new OpenApiSpecificationLoader(new DefaultResourceLoader()), // ls
                new OpenApiToolGenerator(this.objectMapper), new TargetUrlPolicy(this.properties));
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());
        this.server.enqueue(new MockResponse.Builder().code(201) // ls
                .addHeader("Content-Type", "application/json") // ls
                .body("{\"id\":\"new-user\"}").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("create_user", Map.of("body", Map.of("name", "Ada"))).block();

        // verify //
        final mockwebserver3.RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getUrl().encodedPath()).isEqualTo("/users");
        assertThat(request.getBody().utf8()).isEqualTo("{\"name\":\"Ada\"}");
    }

    /**
     * Verifies target error bodies are omitted from MCP error results.
     */
    @Test
    @DisplayName("Hides target error bodies from MCP clients")
    void hidesTargetErrorBodiesFromMcpClients() {
        // prepare //
        this.server.enqueue(new MockResponse.Builder().code(404) // ls
                .addHeader("Content-Type", "application/json") // ls
                .body("{\"secret\":\"target-internal-detail\"}").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("get_user", Map.of("userId", "missing")).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("HTTP 404") // ls
                .doesNotContain("target-internal-detail");
    }

    /**
     * Verifies unknown tools and missing inputs produce safe errors.
     */
    @Test
    @DisplayName("Rejects unknown tools and missing arguments")
    void rejectsUnknownAndMissingToolArguments() {
        // prepare //

        // act //
        final McpSchema.CallToolResult unknown = this.handler.handle("unregistered", Map.of()).block();
        final McpSchema.CallToolResult missing = this.handler.handle("get_user", Map.of()).block();

        // assert //
        assertThat(unknown).isNotNull();
        assertThat(unknown.isError()).isTrue();
        assertThat(missing).isNotNull();
        assertThat(missing.isError()).isTrue();
        assertThat(this.server.getRequestCount()).isZero();
    }

    /**
     * Verifies malformed target JSON is mapped to a safe MCP error.
     */
    @Test
    @DisplayName("Maps malformed JSON responses to safe MCP errors")
    void mapsMalformedJsonResponsesToSafeMcpErrors() {
        // prepare //
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "application/json") // ls
                .body("{not-json").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("get_user", Map.of("userId", "123")).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("malformed JSON").doesNotContain("{not-json");
    }

    /**
     * Verifies bearer credentials are sent without becoming tool arguments.
     *
     * @throws Exception if the mock request cannot be retrieved
     */
    @Test
    @DisplayName("Applies bearer credentials without exposing them to tool arguments")
    void appliesTheConfiguredBearerTokenWithoutExposingItToToolArguments() throws Exception {
        // prepare //
        final GatewayProperties.Api api = this.properties.getApis().getFirst();
        api.getAuth().setType("bearer");
        api.getAuth().setCredential("unit-test-token");
        this.registry = new ToolRegistry(this.properties, new OpenApiSpecificationLoader(new DefaultResourceLoader()), // ls
                new OpenApiToolGenerator(this.objectMapper), new TargetUrlPolicy(this.properties));
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "application/json") // ls
                .body("{\"id\":\"123\"}").build());

        // act //
        this.handler.handle("get_user", Map.of("userId", "123")).block();

        // verify //
        final mockwebserver3.RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);

        // assert //
        assertThat(request).isNotNull();
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer unit-test-token");
    }

    /**
     * Verifies delayed target responses are classified as timeouts.
     */
    @Test
    @DisplayName("Classifies delayed target responses as timeouts")
    void classifiesDelayedTargetResponsesAsTimeouts() {
        // prepare //
        this.properties.getApis().getFirst().setTimeout(Duration.ofMillis(80));
        this.registry = new ToolRegistry(this.properties, new OpenApiSpecificationLoader(new DefaultResourceLoader()), // ls
                new OpenApiToolGenerator(this.objectMapper), new TargetUrlPolicy(this.properties));
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "application/json") // ls
                .bodyDelay(500, TimeUnit.MILLISECONDS) // ls
                .body("{\"id\":\"123\"}").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("get_user", Map.of("userId", "123")).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("timed out");
    }

    /**
     * Verifies the configured response body size limit is enforced.
     */
    @Test
    @DisplayName("Rejects oversized responses")
    void rejectsOversizedResponses() {
        // prepare //
        this.properties.getLimits().setMaxResponseBodyBytes(16);
        this.executor = new GenericApiExecutor(WebClient.builder().build(), this.objectMapper, // ls
                new AuthenticationProviderRegistry(new NoAuthenticationProvider(), // ls
                        new StaticApiKeyAuthenticationProvider(), new BearerTokenAuthenticationProvider()), // ls
                new TargetUrlPolicy(this.properties), new JsonInputValidator(), this.properties);
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());
        this.server.enqueue(new MockResponse.Builder().code(200) // ls
                .addHeader("Content-Type", "application/json") // ls
                .body("{\"message\":\"this response is too large\"}").build());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("get_user", Map.of("userId", "123")).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("exceeds the configured size limit");
    }

    /**
     * Verifies oversized request bodies are rejected before they are sent.
     */
    @Test
    @DisplayName("Rejects oversized request bodies before sending them")
    void rejectsOversizedRequestBodiesBeforeSendingThem() {
        // prepare //
        this.properties.getOperations().setAllowMutating(true);
        this.properties.getLimits().setMaxRequestBodyBytes(4);
        this.registry = new ToolRegistry(this.properties, new OpenApiSpecificationLoader(new DefaultResourceLoader()), // ls
                new OpenApiToolGenerator(this.objectMapper), new TargetUrlPolicy(this.properties));
        this.executor = new GenericApiExecutor(WebClient.builder().build(), this.objectMapper, // ls
                new AuthenticationProviderRegistry(new NoAuthenticationProvider(), // ls
                        new StaticApiKeyAuthenticationProvider(), new BearerTokenAuthenticationProvider()), // ls
                new TargetUrlPolicy(this.properties), new JsonInputValidator(), this.properties);
        this.handler = new McpToolHandler(this.registry, this.executor, this.objectMapper, new SimpleMeterRegistry());

        // act //
        final McpSchema.CallToolResult result = this.handler.handle("create_user", Map.of("body", Map.of("name", "Ada"))).block();

        // assert //
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.toString()).contains("exceeds the configured size limit");
        assertThat(this.server.getRequestCount()).isZero();
    }
}
