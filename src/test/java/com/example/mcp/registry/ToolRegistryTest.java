package com.example.mcp.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.example.mcp.config.GatewayProperties;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.openapi.OpenApiSpecificationLoader;
import com.example.mcp.openapi.OpenApiToolGenerator;
import com.example.mcp.security.TargetUrlPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies generated tool names and operation identifiers remain unique.
 */
class ToolRegistryTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Verifies that an existing tool name cannot be registered again.
	 */
	@Test
	@DisplayName("Rejects duplicate tool names")
	void rejectsDuplicateToolNames() {
		// prepare //
		final ToolRegistry registry = this.registry();
		final GeneratedTool existing = registry.list().getFirst();

		// act //
		// assert //
		assertThatThrownBy(() -> registry.registerAll(List.of(existing))) // ls
				.isInstanceOf(IllegalArgumentException.class) // ls
				.hasMessage("Duplicate MCP tool name: get_user");
	}

	/**
	 * Verifies conflicting operation identifiers across APIs are rejected.
	 */
	@Test
	@DisplayName("conflicting operation identifiers across APIs are rejected")
	void rejectsConflictingOperationIdsAcrossApis() {
		// prepare //
		final ToolRegistry registry = this.registry();
		final GeneratedTool existing = registry.list().getFirst();
		final ApiOperationDefinition operation = existing.operation();
		final ApiOperationDefinition conflict = new ApiOperationDefinition("another-api", operation.operationId(), // ls
				operation.httpMethod(), operation.path(), operation.description(), operation.baseUrl(), // ls
				operation.parameters(), operation.requestBodySchema(), operation.requestBodyRequired(), // ls
				operation.requestContentType(), operation.responseSchema(), operation.authentication(), // ls
				operation.timeout(), operation.classification(), operation.mutatingAllowed(), // ls
				operation.allowedHeaders());
		final GeneratedTool conflictTool = new GeneratedTool("different_tool", existing.description(), // ls
				existing.inputSchema(), existing.outputSchema(), conflict);

		// act //
		// assert //
		assertThatThrownBy(() -> registry.registerAll(List.of(conflictTool))) // ls
				.isInstanceOf(IllegalArgumentException.class) // ls
				.hasMessageContaining("Conflicting OpenAPI operationId 'getUser'");
	}

	/**
	 * Verifies enabled tools can be found, listed, and searched with optional queries.
	 */
	@Test
	@DisplayName("Finds and searches enabled tools")
	void findsAndSearchesEnabledTools() {
		// prepare //
		final ToolRegistry registry = this.registry();

		// act //
		final List<GeneratedTool> all = registry.search(null);
		final List<GeneratedTool> matches = registry.search("GET");

		// assert //
		assertThat(registry.find("get_user")).isPresent();
		assertThat(registry.find("missing")).isEmpty();
		assertThat(all).hasSize(1);
		assertThat(matches).extracting(GeneratedTool::name).containsExactly("get_user");
		assertThat(registry.search("not-present")).isEmpty();
		assertThat(registry.search("by id")).hasSize(1);
		assertThat(registry.search("example-service")).hasSize(1);
	}

	/**
	 * Rejects empty, incomplete, invalid-timeout, and duplicate API configuration.
	 */
	@Test
	@DisplayName("Rejects invalid and duplicate API configurations")
	void rejectsInvalidApiConfigurations() {
		// prepare //
		final GatewayProperties properties = this.validProperties();
		final GatewayProperties.Api api = properties.getApis().getFirst();

		// act //
		// assert //
		properties.setApis(List.of());
		assertThatThrownBy(() -> this.createRegistry(properties)) // ls
				.isInstanceOf(IllegalArgumentException.class) // ls
				.hasMessageContaining("At least one");
		properties.setApis(null);
		assertThatThrownBy(() -> this.createRegistry(properties)) // ls
				.isInstanceOf(IllegalArgumentException.class);

		properties.setApis(List.of(api));
		api.setName(null);
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setName(" ");
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setName("example-service");
		api.setOpenapi(null);
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setOpenapi("classpath:openapi/example-api.yaml");
		api.setBaseUrl(null);
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setBaseUrl("https://api.example.com");
		api.setTimeout(null);
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setTimeout(Duration.ZERO);
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setTimeout(Duration.ofSeconds(-1));
		assertThatThrownBy(() -> this.createRegistry(properties)).isInstanceOf(IllegalArgumentException.class);
		api.setTimeout(Duration.ofSeconds(10));
		properties.setApis(List.of(api, api));
		assertThatThrownBy(() -> this.createRegistry(properties)) // ls
				.isInstanceOf(IllegalArgumentException.class) // ls
				.hasMessageContaining("Duplicate API name");
	}

	/**
	 * Allows distinct tools when their OpenAPI operation identifiers are absent.
	 */
	@Test
	@DisplayName("Registers tools without operation identifiers")
	void registersToolsWithoutOperationIdentifiers() {
		// prepare //
		final ToolRegistry registry = this.registry();
		final ApiOperationDefinition source = registry.list().getFirst().operation();
		final ApiOperationDefinition noId = new ApiOperationDefinition( // ls
				source.apiName(), null, source.httpMethod(), source.path(), source.description(), source.baseUrl(), // ls
				source.parameters(), source.requestBodySchema(), source.requestBodyRequired(), // ls
				source.requestContentType(), source.responseSchema(), source.authentication(), // ls
				source.timeout(), source.classification(), source.mutatingAllowed(), source.allowedHeaders());
		final ApiOperationDefinition blankId = new ApiOperationDefinition( // ls
				source.apiName(), " ", source.httpMethod(), source.path(), source.description(), source.baseUrl(), // ls
				source.parameters(), source.requestBodySchema(), source.requestBodyRequired(), // ls
				source.requestContentType(), source.responseSchema(), source.authentication(), // ls
				source.timeout(), source.classification(), source.mutatingAllowed(), source.allowedHeaders());
		final GeneratedTool first = new GeneratedTool("first_tool", "First", "{}", "{}", noId);
		final GeneratedTool second = new GeneratedTool("second_tool", "Second", "{}", "{}", blankId);

		// act //
		registry.registerAll(List.of(first, second));

		// assert //
		assertThat(registry.find("first_tool")).contains(first);
		assertThat(registry.find("second_tool")).contains(second);
	}

	/**
	 * Rejects repeated operation identifiers within one batch before mutating the registry.
	 */
	@Test
	@DisplayName("Rejects duplicate operation identifiers in a registration batch")
	void rejectsDuplicateOperationIdentifiersInOneBatch() {
		// prepare //
		final ToolRegistry registry = this.registry();
		final ApiOperationDefinition source = registry.list().getFirst().operation();
		final ApiOperationDefinition operation = new ApiOperationDefinition( // ls
				source.apiName(), "newOperation", source.httpMethod(), source.path(), source.description(), source.baseUrl(), // ls
				source.parameters(), source.requestBodySchema(), source.requestBodyRequired(), // ls
				source.requestContentType(), source.responseSchema(), source.authentication(), // ls
				source.timeout(), source.classification(), source.mutatingAllowed(), source.allowedHeaders());
		final GeneratedTool first = new GeneratedTool("first_batch_tool", "First", "{}", "{}", operation);
		final GeneratedTool second = new GeneratedTool("second_batch_tool", "Second", "{}", "{}", operation);

		// act //
		// assert //
		assertThatThrownBy(() -> registry.registerAll(List.of(first, second)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Conflicting OpenAPI operationId 'newOperation'");
		assertThat(registry.find("first_batch_tool")).isEmpty();
	}

	/**
	 * Rejects duplicate names introduced within a single registration batch.
	 */
	@Test
	@DisplayName("Rejects duplicate names within a registration batch")
	void rejectsDuplicateNamesInOneBatch() {
		// prepare //
		final ToolRegistry registry = this.registry();
		final GeneratedTool source = registry.list().getFirst();
		final ApiOperationDefinition operation = source.operation();
		final ApiOperationDefinition withoutId = new ApiOperationDefinition( // ls
				operation.apiName(), null, operation.httpMethod(), operation.path(), operation.description(), // ls
				operation.baseUrl(), operation.parameters(), operation.requestBodySchema(), // ls
				operation.requestBodyRequired(), operation.requestContentType(), operation.responseSchema(), // ls
				operation.authentication(), operation.timeout(), operation.classification(), // ls
				operation.mutatingAllowed(), operation.allowedHeaders());
		final GeneratedTool first = new GeneratedTool(
				"batch_duplicate", source.description(), source.inputSchema(), source.outputSchema(), withoutId);
		final GeneratedTool duplicate = new GeneratedTool(
				"batch_duplicate", source.description(), source.inputSchema(), source.outputSchema(), withoutId);

		// act //
		// assert //
		assertThatThrownBy(() -> registry.registerAll(List.of(first, duplicate)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Duplicate MCP tool name: batch_duplicate");
	}

	/**
	 * Creates a registry backed by the example API definition.
	 *
	 * @return configured test registry
	 */
	private ToolRegistry registry() {
		return this.createRegistry(this.validProperties());
	}

	/**
	 * Builds a valid gateway configuration for registry tests.
	 *
	 * @return configured gateway properties
	 */
	private GatewayProperties validProperties() {
		final GatewayProperties properties = new GatewayProperties();
		properties.getSecurity().setAllowedHosts(List.of("api.example.com"));
		final GatewayProperties.Api api = new GatewayProperties.Api();
		api.setName("example-service");
		api.setBaseUrl("https://api.example.com");
		api.setOpenapi("classpath:openapi/example-api.yaml");
		properties.setApis(List.of(api));

		return properties;
	}

	/**
	 * Constructs a registry using the dependencies required by the application.
	 *
	 * @param properties gateway configuration under test
	 * @return configured tool registry
	 */
	private ToolRegistry createRegistry(GatewayProperties properties) {
		return new ToolRegistry(properties, // ls
				new OpenApiSpecificationLoader(new DefaultResourceLoader()), // ls
				new OpenApiToolGenerator(this.objectMapper), new TargetUrlPolicy(properties));
	}
}
