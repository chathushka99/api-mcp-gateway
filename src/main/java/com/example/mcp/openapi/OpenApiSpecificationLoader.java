package com.example.mcp.openapi;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Loads, validates, and caches local OpenAPI documents.
 */
@Component
public class OpenApiSpecificationLoader {

    private static final int MAX_SPECIFICATION_BYTES = 5 * 1024 * 1024;
    private static final Pattern EXTERNAL_REFERENCE = Pattern.compile( // ls
            "(?im)[\"']?\\$ref[\"']?\\s*:\\s*(?:\"(?!#)[^\"]*\"|'(?!#)[^']*'|(?![#\"'\\s])\\S+)");

    private final ResourceLoader resourceLoader;

    private final ConcurrentMap<String, LoadedOpenApi> cache = new ConcurrentHashMap<>();

    /**
     * Creates a loader using the Spring resource abstraction.
     *
     * @param resourceLoader resource loader for classpath and file locations
     */
    public OpenApiSpecificationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads a validated OpenAPI document, reusing a cached result when available.
     *
     * @param location classpath or file resource location
     * @return parsed and cached OpenAPI document
     */
    public LoadedOpenApi load(String location) {
        validateLocation(location);
        return this.cache.computeIfAbsent(location, this::loadUncached);
    }

    /**
     * Removes a cached document and loads its current contents.
     *
     * @param location classpath or file resource location
     * @return freshly parsed OpenAPI document
     */
    public LoadedOpenApi reload(String location) {
        validateLocation(location);
        this.cache.remove(location);
        return this.load(location);
    }

    /**
     * Restricts specification sources to local classpath and file resources.
     *
     * @param location source location to validate
     * @throws IllegalArgumentException if the location uses an unsupported scheme
     */
    private static void validateLocation(String location) {
        if (location == null || !(location.startsWith("classpath:") || location.startsWith("file:"))) {
            throw new IllegalArgumentException("OpenAPI specifications must use classpath: or file: locations");
        }
    }

    /**
     * Reads and parses a specification after applying size and reference checks.
     *
     * @param location local resource location
     * @return parsed OpenAPI document
     */
    private LoadedOpenApi loadUncached(String location) {
        try {
            final Resource resource = this.resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new IllegalArgumentException("OpenAPI specification was not found: " + location);
            }
            final String contents;
            try (InputStream input = resource.getInputStream()) {
                final byte[] bytes = input.readNBytes(MAX_SPECIFICATION_BYTES + 1);
                if (bytes.length > MAX_SPECIFICATION_BYTES) {
                    throw new IllegalArgumentException("OpenAPI specification exceeds the configured size limit");
                }
                contents = new String(bytes, StandardCharsets.UTF_8);
            }
            if (EXTERNAL_REFERENCE.matcher(contents).find()) {
                throw new IllegalArgumentException("External OpenAPI references are not allowed");
            }

            final ParseOptions options = new ParseOptions();
            options.setResolve(true);
            final SwaggerParseResult result = new OpenAPIV3Parser().readContents(contents, null, options);
            final List<String> errors = result.getMessages();
            if (result.getOpenAPI() == null || errors != null && !errors.isEmpty()) {
                throw new IllegalArgumentException("OpenAPI specification is invalid: " + // ls
                        (errors == null ? "unable to parse document" : String.join("; ", errors)));
            }
            final String version = result.getOpenAPI().getOpenapi();
            if (version == null || !version.startsWith("3.")) {
                throw new IllegalArgumentException("Only OpenAPI 3.x specifications are supported");
            }
            if (result.getOpenAPI().getPaths() == null || result.getOpenAPI().getPaths().isEmpty()) {
                throw new IllegalArgumentException("OpenAPI specification does not contain any paths");
            }
            return new LoadedOpenApi(location, result.getOpenAPI());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read OpenAPI specification: " + location, exception);
        }
    }
}
