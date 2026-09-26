package com.example.mcp.openapi;

import com.example.mcp.config.GatewayProperties;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.example.mcp.model.AuthenticationConfiguration;
import com.example.mcp.model.GeneratedTool;
import com.example.mcp.model.ParameterDefinition;
import com.example.mcp.model.ParameterDefinition.Location;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates MCP tool descriptions and schemas from OpenAPI operations.
 */
@Component
public class OpenApiToolGenerator {

    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private static final Set<String> FORBIDDEN_HEADERS = Set.of("authorization", "proxy-authorization", "host", // ls
            "cookie", "set-cookie", "connection", "content-length", "transfer-encoding", // ls
            "proxy-connection", "keep-alive", "te", "trailer", "upgrade");

    private static final Map<String, String> METHODS = Map.of( // ls
            "GET", "get", "POST", "post", "PUT", "put", "PATCH", "patch", "DELETE", "delete", // ls
            "HEAD", "head", "OPTIONS", "options");

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemas;

    /**
     * Creates a generator using the mapper shared with JSON schema conversion.
     *
     * @param objectMapper mapper used to build and serialize schema nodes
     */
    public OpenApiToolGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemas = new JsonSchemaFactory(objectMapper);
    }

    /**
     * Generates tools for every operation in an OpenAPI document.
     *
     * @param apiName         configured API name
     * @param baseUrl         validated target API base URL
     * @param apiConfig       API-specific gateway configuration
     * @param openApi         parsed OpenAPI document
     * @param mutatingAllowed whether mutation tools may be enabled
     * @return generated tools in document order
     */
    public List<GeneratedTool> generate(String apiName, URI baseUrl, GatewayProperties.Api apiConfig, // ls
                                        io.swagger.v3.oas.models.OpenAPI openApi, boolean mutatingAllowed) {
        final AuthenticationConfiguration authentication = this.authentication(apiConfig.getAuth());
        final List<GeneratedTool> generated = new ArrayList<>();
        openApi.getPaths().forEach((path, pathItem) -> { // ls
            final Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap(); // ls
            if (operations == null) { // ls
                return; // ls
            } // ls
            operations.forEach((method, operation) -> generated.add( // ls
                    this.generateOperation(apiName, baseUrl, apiConfig, path, pathItem, method, operation, // ls
                            authentication, mutatingAllowed))); // ls
        });
        return List.copyOf(generated);
    }

    /**
     * Converts a single OpenAPI operation to its MCP representation.
     *
     * @param apiName         configured API name
     * @param baseUrl         validated target base URL
     * @param apiConfig       API-specific gateway configuration
     * @param path            OpenAPI operation path
     * @param pathItem        containing path-level parameters
     * @param method          operation HTTP method
     * @param operation       OpenAPI operation
     * @param authentication  resolved API authentication
     * @param mutatingAllowed whether mutation execution is enabled
     * @return generated tool definition and execution metadata
     */
    private GeneratedTool generateOperation(String apiName, URI baseUrl, GatewayProperties.Api apiConfig, // ls
                                            String path, PathItem pathItem, PathItem.HttpMethod method, Operation operation, // ls
                                            AuthenticationConfiguration authentication, boolean mutatingAllowed) {
        final String methodName = method.name();
        if (!METHODS.containsKey(methodName)) {
            throw new IllegalArgumentException("Unsupported OpenAPI HTTP method: " + methodName);
        }
        final Classification classification = method == PathItem.HttpMethod.GET || method == PathItem.HttpMethod.HEAD
                ? Classification.READ : Classification.MUTATING;
        final Map<String, Parameter> mergedParameters = new LinkedHashMap<>();
        if (pathItem.getParameters() != null) {
            pathItem.getParameters().forEach(parameter -> mergedParameters.put(parameterKey(parameter), parameter));
        }
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(parameter -> mergedParameters.put(parameterKey(parameter), parameter));
        }
        validateAuthenticationParameterConflict(authentication, mergedParameters.values());

        final List<ParameterDefinition> parameters = new ArrayList<>();
        final Map<String, JsonNode> properties = new LinkedHashMap<>();
        final List<String> required = new ArrayList<>();
        for (final Parameter parameter : mergedParameters.values()) {
            final Location location = switch (parameter.getIn()) {
                case "path" -> Location.PATH;
                case "query" -> Location.QUERY;
                case "header" -> Location.HEADER;
                default -> throw new IllegalArgumentException("Unsupported parameter location: " + parameter.getIn());
            };
            if (location == Location.HEADER) {
                validateHeaderAllowed(parameter.getName(), apiConfig.getAllowedHeaders());
            }
            final JsonNode parameterSchema = this.schemas.fromOpenApi(parameter.getSchema());
            if (parameter.getDescription() != null && !parameterSchema.has("description")) {
                ((ObjectNode) parameterSchema).put("description", parameter.getDescription());
            }
            final boolean parameterRequired = location == Location.PATH || Boolean.TRUE.equals(parameter.getRequired());
            this.addInputProperty(properties, required, parameter.getName(), parameterSchema, parameterRequired);
            parameters.add(new ParameterDefinition(parameter.getName(), location, parameterRequired, parameterSchema));
        }

        final RequestBody requestBody = operation.getRequestBody();
        JsonNode requestBodySchema = null;
        boolean requestBodyRequired = false;
        String requestContentType = "application/json";
        if (requestBody != null) {
            final MediaType requestMedia = selectMediaType(requestBody.getContent());
            if (requestMedia == null) {
                throw new IllegalArgumentException("OpenAPI request body must declare a content type");
            }
            requestBodySchema = this.schemas.fromOpenApi(requestMedia.getSchema());
            requestContentType = selectedMediaType(requestBody.getContent());
            if (!requestContentType.equals("application/json") && !requestContentType.endsWith("+json")) {
                throw new IllegalArgumentException("Only JSON request bodies are supported: " + requestContentType);
            }
            requestBodyRequired = Boolean.TRUE.equals(requestBody.getRequired());
            this.addInputProperty(properties, required, "body", requestBodySchema, requestBodyRequired);
        }

        final String toolName = toolName(operation.getOperationId(), methodName, path);
        final String description = description(operation, methodName, path);
        final JsonNode inputSchema = this.schemas.objectSchema(properties, required);
        final JsonNode responseSchema = this.responseSchema(operation.getResponses());
        final JsonNode outputSchema = this.schemas.outputSchema(responseSchema);
        final ApiOperationDefinition definition = new ApiOperationDefinition(apiName, operation.getOperationId(), // ls
                methodName, path, description, baseUrl, List.copyOf(parameters), requestBodySchema, // ls
                requestBodyRequired, requestContentType, responseSchema, authentication, apiConfig.getTimeout(), // ls
                classification, mutatingAllowed, normalizedAllowedHeaders(apiConfig.getAllowedHeaders()));
        return new GeneratedTool(toolName, description, this.schemas.serialize(inputSchema), this.schemas.serialize(outputSchema), // ls
                definition);
    }

    /**
     * Builds a case-insensitive key for merging path and operation parameters.
     *
     * @param parameter OpenAPI parameter
     * @return location-and-name key
     */
    private static String parameterKey(Parameter parameter) {
        return parameter.getIn() + ":" + parameter.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Rejects a documented parameter that collides with configured API-key credentials.
     *
     * @param authentication resolved API authentication
     * @param parameters     parameters documented for an operation
     */
    private static void validateAuthenticationParameterConflict(AuthenticationConfiguration authentication, // ls
                                                                Collection<Parameter> parameters) {
        if (authentication.type() != AuthenticationConfiguration.Type.API_KEY) {
            return;
        }
        final String expectedLocation = authentication.location() == AuthenticationConfiguration.Location.QUERY
                ? "query" : "header";
        final boolean conflict = parameters.stream().anyMatch(parameter -> // ls
                expectedLocation.equalsIgnoreCase(parameter.getIn()) && // ls
                        authentication.name().equalsIgnoreCase(parameter.getName()));
        if (conflict) {
            throw new IllegalArgumentException("API key authentication conflicts with a documented parameter");
        }
    }

    /**
     * Adds a named input property while rejecting ambiguous duplicate names.
     *
     * @param properties collected input property schemas
     * @param required   collected required property names
     * @param name       property name
     * @param schema     property schema
     * @param isRequired whether the property is mandatory
     */
    private void addInputProperty(Map<String, JsonNode> properties, List<String> required, String name, // ls
                                  JsonNode schema, boolean isRequired) {
        if (properties.putIfAbsent(name, schema) != null) {
            throw new IllegalArgumentException("OpenAPI parameters create an ambiguous MCP input property: " + name);
        }
        if (isRequired) {
            required.add(name);
        }
    }

    /**
     * Selects a successful response schema when one is documented.
     *
     * @param responses operation responses from the OpenAPI document
     * @return successful response body schema, or {@code null} if absent
     */
    private JsonNode responseSchema(ApiResponses responses) {
        if (responses == null || responses.isEmpty()) {
            return null;
        }
        ApiResponse success = responses.get("200");
        if (success == null) {
            success = responses.entrySet().stream() // ls
                    .filter(entry -> entry.getKey().matches("2\\d\\d|2XX")) // ls
                    .map(Map.Entry::getValue) // ls
                    .findFirst().orElse(null);
        }
        if (success == null) {
            return null;
        }
        final MediaType mediaType = selectMediaType(success.getContent());
        if (mediaType == null) {
            return null;
        }
        final String contentType = selectedMediaType(success.getContent()).toLowerCase(Locale.ROOT);
        if (!contentType.equals("application/json") && !contentType.endsWith("+json")) {
            return this.objectMapper.createObjectNode().put("type", "string");
        }
        return mediaType.getSchema() == null ? null : this.schemas.fromOpenApi(mediaType.getSchema());
    }

    /**
     * Chooses JSON, a JSON-compatible media type, or the first declared media type.
     *
     * @param content response or request content definitions
     * @return selected media type definition, or {@code null} when none exist
     */
    private static MediaType selectMediaType(Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        final MediaType json = content.get("application/json");
        if (json != null) {
            return json;
        }
        return content.entrySet().stream() // ls
                .filter(entry -> entry.getKey().endsWith("+json")) // ls
                .map(Map.Entry::getValue) // ls
                .findFirst() // ls
                .orElseGet(() -> content.values().iterator().next());
    }

    /**
     * Returns the media type selected for an OpenAPI content definition.
     *
     * @param content content definitions
     * @return selected media type name
     */
    private static String selectedMediaType(Content content) {
        if (content == null || content.isEmpty()) {
            return "application/json";
        }
        if (content.containsKey("application/json")) {
            return "application/json";
        }
        return content.keySet().stream().filter(type -> type.endsWith("+json")).findFirst() // ls
                .orElse(content.keySet().iterator().next());
    }

    /**
     * Resolves authentication settings and validates required credential metadata.
     *
     * @param config configured authentication options
     * @return normalized authentication configuration
     */
    private AuthenticationConfiguration authentication(GatewayProperties.Authentication config) {
        final String type = config.getType() == null ? "none" : config.getType().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "none" -> new AuthenticationConfiguration(AuthenticationConfiguration.Type.NONE, null, null, // ls
                    AuthenticationConfiguration.Location.HEADER);
            case "bearer" -> {
                requireCredential(config.getCredential(), "Bearer token");
                yield new AuthenticationConfiguration(AuthenticationConfiguration.Type.BEARER, "Authorization", // ls
                        config.getCredential(), AuthenticationConfiguration.Location.HEADER);
            }
            case "api-key", "apikey" -> {
                requireCredential(config.getCredential(), "API key");
                final boolean query = "query".equalsIgnoreCase(config.getLocation());
                final String name = query ? config.getParameterName() : config.getHeaderName();
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("API key authentication requires a parameter or header name");
                }
                if (query ? !name.matches("[A-Za-z0-9_.-]+") // ls
                        : !validHeaderName(name) || FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("API key authentication configuration is invalid");
                }
                yield new AuthenticationConfiguration(AuthenticationConfiguration.Type.API_KEY, name, // ls
                        config.getCredential(), query ? AuthenticationConfiguration.Location.QUERY // ls
                        : AuthenticationConfiguration.Location.HEADER);
            }
            default -> throw new IllegalArgumentException("Unsupported authentication type: " + type);
        };
    }

    /**
     * Ensures a configured authentication secret is present and safe for HTTP use.
     *
     * @param credential credential value to validate
     * @param type       human-readable credential type
     */
    private static void requireCredential(String credential, String type) {
        if (credential == null || credential.isBlank() || containsControlCharacters(credential)) {
            throw new IllegalArgumentException(type + " must be supplied through external configuration");
        }
    }

    /**
     * Derives a readable sentence for an operation.
     *
     * @param operation OpenAPI operation
     * @param method    HTTP method name
     * @param path      operation path
     * @return normalized operation description
     */
    private static String description(Operation operation, String method, String path) {
        String source = operation.getSummary() == null || operation.getSummary().isBlank()
                ? operation.getDescription() : operation.getSummary();
        if (source == null || source.isBlank()) {
            source = method + " " + path;
        }
        source = source.trim();
        return source.endsWith(".") || source.endsWith("!") || source.endsWith("?") ? source : source + ".";
    }

    /**
     * Converts an operation identifier or method/path pair to a safe tool name.
     *
     * @param operationId OpenAPI operation identifier, if present
     * @param method      HTTP method name
     * @param path        operation path
     * @return normalized MCP tool name
     */
    private static String toolName(String operationId, String method, String path) {
        String source = operationId;
        if (source == null || source.isBlank()) {
            final Matcher matcher = PATH_PARAMETER.matcher(path);
            String resource = null;
            if (matcher.find()) {
                resource = matcher.group(1).replaceFirst("(?i)(?:[-_]?id)$", "");
            } else {
                final String[] segments = path.split("/");
                for (int index = segments.length - 1; index >= 0; index--) {
                    if (!segments[index].isBlank()) {
                        resource = segments[index];
                        break;
                    }
                }
            }
            source = method.toLowerCase(Locale.ROOT) + "_" + (resource == null ? "root" : resource);
        }
        String normalized = CAMEL_BOUNDARY.matcher(source).replaceAll("$1_$2") // ls
                .replaceAll("[^A-Za-z0-9]+", "_") // ls
                .replaceAll("_+", "_") // ls
                .replaceAll("^_|_$", "") // ls
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("OpenAPI operation cannot be converted to a safe MCP tool name");
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64).replaceAll("_+$", "");
        }
        return normalized;
    }

    /**
     * Validates that a documented header is syntactically safe and allow-listed.
     *
     * @param headerName     documented header name
     * @param allowedHeaders configured permitted headers
     */
    private static void validateHeaderAllowed(String headerName, List<String> allowedHeaders) {
        final String normalized = headerName.toLowerCase(Locale.ROOT);
        if (!validHeaderName(headerName) || FORBIDDEN_HEADERS.contains(normalized) || allowedHeaders == null || // ls
                allowedHeaders.stream().noneMatch(name -> name.equalsIgnoreCase(headerName))) {
            throw new IllegalArgumentException("OpenAPI header parameter is not explicitly allow-listed: " + headerName);
        }
    }

    /**
     * Checks header token syntax.
     *
     * @param headerName header name to check
     * @return {@code true} if the name matches the HTTP token grammar
     */
    private static boolean validHeaderName(String headerName) {
        return headerName != null && HEADER_NAME.matcher(headerName).matches();
    }

    /**
     * Checks whether a value contains HTTP control characters.
     *
     * @param value string value to inspect
     * @return {@code true} when a control character is present
     */
    private static boolean containsControlCharacters(String value) {
        return value.chars().anyMatch(character -> character < 0x20 || character == 0x7f);
    }

    /**
     * Normalizes configured allowed header names for case-insensitive checks.
     *
     * @param allowedHeaders configured allowed headers
     * @return lowercase immutable header list
     */
    private static List<String> normalizedAllowedHeaders(List<String> allowedHeaders) {
        if (allowedHeaders == null) {
            return List.of();
        }
        final LinkedHashSet<String> normalized = new LinkedHashSet<>();
        allowedHeaders.forEach(header -> normalized.add(header.toLowerCase(Locale.ROOT)));
        return List.copyOf(normalized);
    }
}
