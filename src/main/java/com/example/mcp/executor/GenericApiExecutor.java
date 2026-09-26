package com.example.mcp.executor;

import com.example.mcp.auth.AuthenticationProviderRegistry;
import com.example.mcp.auth.AuthenticationValues;
import com.example.mcp.config.GatewayProperties;
import com.example.mcp.exception.GatewayException;
import com.example.mcp.model.ApiOperationDefinition;
import com.example.mcp.model.ApiOperationDefinition.Classification;
import com.example.mcp.model.ApiResponse;
import com.example.mcp.model.ParameterDefinition;
import com.example.mcp.model.ParameterDefinition.Location;
import com.example.mcp.security.TargetUrlPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and executes API operations over the configured outbound HTTP client.
 */
@Component
public class GenericApiExecutor {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AuthenticationProviderRegistry authenticationProviders;
    private final TargetUrlPolicy targetUrlPolicy;
    private final JsonInputValidator inputValidator;

    private final int maxRequestBodyBytes;
    private final int maxResponseBodyBytes;

    /**
     * Creates an executor with its outbound dependencies and configured size limits.
     *
     * @param webClient               HTTP client used to reach target APIs
     * @param objectMapper            mapper used for request and response JSON
     * @param authenticationProviders provider registry for outbound credentials
     * @param targetUrlPolicy         policy guarding configured and resolved destinations
     * @param inputValidator          validator for generated tool arguments
     * @param properties              gateway settings containing body limits
     */
    public GenericApiExecutor(WebClient webClient, ObjectMapper objectMapper, // ls
                              AuthenticationProviderRegistry authenticationProviders, TargetUrlPolicy targetUrlPolicy, // ls
                              JsonInputValidator inputValidator, GatewayProperties properties) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.authenticationProviders = authenticationProviders;
        this.targetUrlPolicy = targetUrlPolicy;
        this.inputValidator = inputValidator;

        this.maxRequestBodyBytes = properties.getLimits().getMaxRequestBodyBytes();
        this.maxResponseBodyBytes = properties.getLimits().getMaxResponseBodyBytes();
    }

    /**
     * Executes one generated operation after validating its arguments and destination.
     *
     * @param operation API operation to invoke
     * @param arguments MCP tool arguments
     * @return asynchronous target API result
     */
    public Mono<ApiResponse> execute(ApiOperationDefinition operation, JsonNode arguments) {
        return Mono.defer(() -> { // ls
            if (operation.classification() == Classification.MUTATING && !operation.mutatingAllowed()) { // ls
                return Mono.error(new GatewayException(GatewayException.Code.MUTATING_OPERATION_DISABLED, // ls
                        "Mutating API operations are disabled")); // ls
            } // ls

            this.validate(operation, arguments); // ls

            final byte[] requestBody = this.serializeBody(operation, arguments); // ls

            return Mono.fromCallable(() -> this.buildUri(operation, arguments)) // ls
                    .subscribeOn(Schedulers.boundedElastic()) // ls
                    .flatMap(uri -> { // ls
                        this.targetUrlPolicy.verifyResolvedTarget(uri); // ls
                        return this.send(operation, arguments, uri, requestBody); // ls
                    }) // ls
                    .timeout(operation.timeout()) // ls
                    .onErrorMap(this::isTimeoutFailure, // ls
                            error -> new GatewayException(GatewayException.Code.TIMEOUT, // ls
                                    "Target API request timed out", error)) // ls
                    .onErrorMap(error -> !(error instanceof GatewayException), // ls
                            this::classifyExecutionFailure); // ls
        });
    }

    /**
     * Validates the complete tool argument object against operation parameters.
     *
     * @param operation operation metadata and parameter schemas
     * @param arguments supplied tool arguments
     */
    private void validate(ApiOperationDefinition operation, JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                    "Tool arguments must be a JSON object");
        }

        final Map<String, JsonNode> inputProperties = new LinkedHashMap<>();
        final List<String> required = new ArrayList<>();

        for (final ParameterDefinition parameter : operation.parameters()) {
            inputProperties.put(parameter.name(), parameter.schema());
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }

        if (operation.requestBodySchema() != null) {
            inputProperties.put("body", operation.requestBodySchema());
            if (operation.requestBodyRequired()) {
                required.add("body");
            }
        }

        final ObjectNode rootSchema = this.objectMapper.createObjectNode() // ls
                .put("type", "object") // ls
                .put("additionalProperties", false);
        final ObjectNode propertySchemas = rootSchema.putObject("properties");
        inputProperties.forEach(propertySchemas::set);

        final ArrayNode requiredSchemas = rootSchema.putArray("required");
        required.forEach(requiredSchemas::add);

        this.inputValidator.validate(rootSchema, arguments);
    }

    /**
     * Serializes a provided request body and enforces its configured size limit.
     *
     * @param operation operation metadata containing the request schema
     * @param arguments supplied tool arguments
     * @return serialized request body, or {@code null} when no body is used
     */
    private byte[] serializeBody(ApiOperationDefinition operation, JsonNode arguments) {
        if (operation.requestBodySchema() == null || !arguments.has("body")) {
            return null;
        }

        try {
            final byte[] bytes = this.objectMapper.writeValueAsBytes(arguments.get("body"));

            if (bytes.length > this.maxRequestBodyBytes) {
                throw new GatewayException(GatewayException.Code.REQUEST_TOO_LARGE, // ls
                        "Request body exceeds the configured size limit");
            }
            return bytes;
        } catch (IOException exception) {
            throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                    "Request body is not valid JSON", exception);
        }
    }

    /**
     * Builds the encoded target URI and applies query authentication credentials.
     *
     * @param operation operation metadata and request parameters
     * @param arguments supplied tool arguments
     * @return complete target request URI
     */
    private URI buildUri(ApiOperationDefinition operation, JsonNode arguments) {
        final Map<String, Object> pathVariables = new LinkedHashMap<>();
        final UriComponentsBuilder builder = UriComponentsBuilder.fromUri(operation.baseUrl()).path(operation.path());

        for (final ParameterDefinition parameter : operation.parameters()) {
            final JsonNode value = arguments.get(parameter.name());
            if (value == null) {
                continue;
            }
            switch (parameter.location()) {
                case PATH -> {
                    final String pathValue = scalar(value, parameter.name());

                    if (pathValue.equals(".") || pathValue.equals("..") || pathValue.contains("/") || // ls
                            pathValue.contains("\\") || pathValue.contains("%") || containsControlCharacters(pathValue)) {
                        throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                                "Invalid path parameter: " + parameter.name());
                    }
                    pathVariables.put(parameter.name(), pathValue);
                }
                case QUERY -> addQueryParameter(builder, parameter.name(), value);
                case HEADER -> {
                    if (!operation.allowedHeaders().contains(parameter.name().toLowerCase(java.util.Locale.ROOT))) {
                        throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                                "Header parameter is not allow-listed: " + parameter.name());
                    }
                    headerValue(value, parameter.name());
                }
            }
        }

        final AuthenticationValues credentials = this.authenticationProviders.credentials(operation.authentication());
        credentials.queryParameters().forEach(builder::replaceQueryParam);

        return builder.buildAndExpand(pathVariables).encode().toUri();
    }

    /**
     * Adds a scalar or array-valued parameter to the target query string.
     *
     * @param builder URI builder receiving the parameter
     * @param name    query parameter name
     * @param value   parameter value
     */
    private static void addQueryParameter(UriComponentsBuilder builder, String name, JsonNode value) {
        if (value.isArray()) {
            for (final JsonNode item : value) {
                builder.queryParam(name, scalar(item, name));
            }
        } else {
            builder.queryParam(name, scalar(value, name));
        }
    }

    /**
     * Sends the target request and maps its response to the gateway result model.
     *
     * @param operation operation metadata for the request
     * @param arguments supplied tool arguments
     * @param uri       resolved target URI
     * @param body      serialized request body, if any
     * @return asynchronous target response
     */
    private Mono<ApiResponse> send(ApiOperationDefinition operation, JsonNode arguments, URI uri, byte[] body) {
        final HttpMethod method = HttpMethod.valueOf(operation.httpMethod());
        final AuthenticationValues credentials = this.authenticationProviders.credentials(operation.authentication());

        final WebClient.RequestBodySpec request = this.webClient.method(method).uri(uri) // ls
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN);

        for (final ParameterDefinition parameter : operation.parameters()) {
            if (parameter.location() != Location.HEADER || !arguments.has(parameter.name())) {
                continue;
            }
            if (!operation.allowedHeaders().contains(parameter.name().toLowerCase(java.util.Locale.ROOT))) {
                return Mono.error(new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                        "Header parameter is not allow-listed: " + parameter.name()));
            }
            final String value = headerValue(arguments.get(parameter.name()), parameter.name());
            request.header(parameter.name(), value);
        }

        credentials.headers().forEach(request::header);

        if (body != null) {
            request.contentType(MediaType.parseMediaType(operation.requestContentType()));
            request.body(BodyInserters.fromValue(body));
        }

        return request.exchangeToMono(response -> { // ls
            final int status = response.statusCode().value(); // ls
            final String contentType = response.headers().contentType().map(MediaType::toString).orElse(""); // ls

            return this.readResponseBytes(response) // ls
                    .flatMap(bytes -> { // ls
                        if (status < 200 || status >= 300) { // ls
                            final GatewayException.Code code = status < 500 // ls
                                    ? GatewayException.Code.TARGET_CLIENT_ERROR // ls
                                    : GatewayException.Code.TARGET_SERVER_ERROR; // ls
                            return Mono.error(new GatewayException(code, // ls
                                    "Target API returned HTTP " + status, status)); // ls
                        } // ls

                        try { // ls
                            final JsonNode parsedBody = this.parseResponse(bytes, contentType); // ls
                            return Mono.just(new ApiResponse(status, parsedBody, contentType)); // ls
                        } catch (GatewayException exception) { // ls
                            return Mono.error(exception); // ls
                        } // ls
                    }); // ls
        });
    }

    /**
     * Joins the response stream while honoring the configured body-size limit.
     *
     * @param response client response whose body is read
     * @return response bytes
     */
    private Mono<byte[]> readResponseBytes(org.springframework.web.reactive.function.client.ClientResponse response) {
        return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class), this.maxResponseBodyBytes) // ls
                .map(buffer -> { // ls
                    try { // ls
                        byte[] bytes = new byte[buffer.readableByteCount()]; // ls
                        buffer.read(bytes); // ls
                        return bytes; // ls
                    } finally { // ls
                        DataBufferUtils.release(buffer); // ls
                    } // ls
                }) // ls
                .defaultIfEmpty(new byte[0]);
    }

    /**
     * Decodes JSON and text responses, encoding other media types as Base64.
     *
     * @param bytes       response payload
     * @param contentType response media type
     * @return decoded JSON value or textual payload node
     */
    private JsonNode parseResponse(byte[] bytes, String contentType) {
        if (bytes.length == 0) {
            return null;
        }

        try {
            if (contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
                return this.objectMapper.readTree(bytes);
            }

            final String normalizedType = contentType.toLowerCase(java.util.Locale.ROOT);
            final boolean textual = normalizedType.startsWith("text/") || normalizedType.contains("xml");

            final String body = textual ? new String(bytes, StandardCharsets.UTF_8)
                    : Base64.getEncoder().encodeToString(bytes);

            return this.objectMapper.getNodeFactory().textNode(body);
        } catch (java.io.IOException exception) {
            throw new GatewayException(GatewayException.Code.MALFORMED_RESPONSE, // ls
                    "Target API returned malformed JSON", exception);
        }
    }

    /**
     * Converts transport and buffering failures into stable gateway error codes.
     *
     * @param error unclassified execution failure
     * @return safe, classified gateway exception
     */
    private RuntimeException classifyExecutionFailure(Throwable error) {
        if (error instanceof DataBufferLimitException) {
            return new GatewayException(GatewayException.Code.RESPONSE_TOO_LARGE, // ls
                    "Target API response exceeds the configured size limit", error);
        }

        if (error instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
            return new GatewayException(GatewayException.Code.CONNECTION_FAILURE, // ls
                    "Unable to connect to target API", error);
        }

        return new GatewayException(GatewayException.Code.EXECUTION_FAILURE, // ls
                "Target API request could not be completed", error);
    }

    /**
     * Detects timeout failures, including wrapped connection-timeout causes.
     *
     * @param error execution failure to inspect
     * @return {@code true} if any cause represents a timeout
     */
    private boolean isTimeoutFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.TimeoutException || // ls
                    cause instanceof io.netty.channel.ConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a JSON value into a safe scalar request parameter.
     *
     * @param value JSON parameter value
     * @param name  parameter name used in validation errors
     * @return scalar string representation
     */
    private static String scalar(JsonNode value, String name) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                    "Parameter must be a scalar value: " + name);
        }

        final String result = value.isTextual() ? value.asText() : value.toString();

        if (containsControlCharacters(result)) {
            throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                    "Parameter contains invalid characters: " + name);
        }
        return result;
    }

    /**
     * Converts a header value or array of values to a safe string.
     *
     * @param value JSON header value
     * @param name  header name used in validation errors
     * @return normalized header value
     */
    private static String headerValue(JsonNode value, String name) {
        String result;
        if (value.isArray()) {
            final List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(scalar(item, name)));
            result = String.join(",", values);
        } else {
            result = scalar(value, name);
        }

        if (containsControlCharacters(result)) {
            throw new GatewayException(GatewayException.Code.INVALID_ARGUMENTS, // ls
                    "Header value contains invalid characters: " + name);
        }
        return result;
    }

    /**
     * Checks whether a string contains HTTP control characters.
     *
     * @param value value to inspect
     * @return {@code true} when a control character is present
     */
    private static boolean containsControlCharacters(String value) {
        return value.chars().anyMatch(character -> character < 0x20 || character == 0x7f);
    }
}
