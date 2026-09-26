package com.example.mcp.openapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Verifies OpenAPI document loading, caching, reloading, and reference restrictions.
 */
class OpenApiSpecificationLoaderTest {

    @TempDir
    java.nio.file.Path temporaryDirectory;

    /**
     * Verifies JSON documents are loaded, cached, and refreshed on demand.
     *
     * @throws Exception if the fixture cannot be written or loaded
     */
    @Test
    @DisplayName("Loads and caches JSON specifications")
    void loadsAndCachesJsonSpecifications() throws Exception {
        // prepare //
        final String json = """
                {"openapi":"3.0.3","info":{"title":"JSON API","version":"1.0"},"paths":{"/health":{"get":{"responses":{"200":{"description":"OK"}}}}}}
                """;
        final java.nio.file.Path source = this.temporaryDirectory.resolve("spec.json");
        Files.writeString(source, json);
        final OpenApiSpecificationLoader loader = new OpenApiSpecificationLoader(new DefaultResourceLoader());

        // act //
        final LoadedOpenApi first = loader.load(source.toUri().toString().replace("file:///", "file:/"));

        // assert //
        assertThat(first.openApi().getOpenapi()).isEqualTo("3.0.3");
        final String location = source.toUri().toString().replace("file:///", "file:/");
        assertThat(loader.load(location)).isSameAs(first);
        Files.writeString(source, json.replace("JSON API", "Reloaded API"));

        // verify //
        assertThat(loader.reload(location).openApi().getInfo().getTitle()).isEqualTo("Reloaded API");
    }

    /**
     * Verifies external references are rejected.
     *
     * @throws Exception if the fixture cannot be written
     */
    @Test
    @DisplayName("Rejects external references")
    void rejectsExternalReferences() throws Exception {
        // prepare //
        final String yaml = """
                openapi: 3.0.3
                info:
                  title: External reference
                  version: 1.0
                paths:
                  /health:
                    get:
                      responses:
                        "200":
                          description: OK
                components:
                  schemas:
                    External:
                      $ref: https://example.invalid/schema.yaml
                """;
        final java.nio.file.Path source = this.temporaryDirectory.resolve("external.yaml");
        Files.writeString(source, yaml);
        final OpenApiSpecificationLoader loader = new OpenApiSpecificationLoader(new DefaultResourceLoader());

        // act //
        // assert //
        assertThatThrownBy(() -> loader.load(source.toUri().toString().replace("file:///", "file:/"))) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessage("External OpenAPI references are not allowed");
    }

    /**
     * Rejects unsupported resource schemes, missing documents, and malformed content.
     *
     * @throws Exception if a test document cannot be written
     */
    @Test
    @DisplayName("Rejects invalid specification locations and documents")
    void rejectsInvalidLocationsAndDocuments() throws Exception {
        // prepare //
        final OpenApiSpecificationLoader loader = new OpenApiSpecificationLoader(new DefaultResourceLoader());
        final java.nio.file.Path malformed = this.temporaryDirectory.resolve("malformed.yaml");
        Files.writeString(malformed, "not an OpenAPI document");
        final java.nio.file.Path emptyPaths = this.temporaryDirectory.resolve("empty-paths.yaml");
        Files.writeString(emptyPaths, """
                openapi: 3.0.3
                info:
                  title: Empty
                  version: 1
                paths: {}
                """);
        final java.nio.file.Path invalidOperation = this.temporaryDirectory.resolve("invalid-operation.yaml");
        Files.writeString(invalidOperation, """
                openapi: 3.0.3
                info:
                  title: Invalid path
                  version: 1
                paths:
                  /users/{userId}:
                    get:
                      responses:
                        "200":
                          description: Missing path parameter
                """);

        // act //
        // assert //
        assertThatThrownBy(() -> loader.load("https://example.invalid/openapi.yaml")) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessage("OpenAPI specifications must use classpath: or file: locations");
        assertThatThrownBy(() -> loader.load(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loader.load("file:" + this.temporaryDirectory.resolve("missing.yaml"))) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessageContaining("was not found");
        assertThatThrownBy(() -> loader.load("file:" + malformed.toAbsolutePath())) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessageContaining("OpenAPI specification is invalid");
        assertThatThrownBy(() -> loader.load("file:" + emptyPaths.toAbsolutePath())) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessage("OpenAPI specification does not contain any paths");
        assertThatThrownBy(() -> loader.load("file:" + invalidOperation.toAbsolutePath())) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessageContaining("OpenAPI specification is invalid");
    }

    /**
     * Rejects specifications larger than the fixed parser input limit.
     *
     * @throws Exception if the oversized fixture cannot be written
     */
    @Test
    @DisplayName("Rejects oversized specification files")
    void rejectsOversizedSpecificationFiles() throws Exception {
        // prepare //
        final OpenApiSpecificationLoader loader = new OpenApiSpecificationLoader(new DefaultResourceLoader());
        final java.nio.file.Path source = this.temporaryDirectory.resolve("oversized.yaml");
        Files.writeString(source, " ".repeat(5 * 1024 * 1024 + 1));

        // act //
        // assert //
        assertThatThrownBy(() -> loader.load("file:" + source.toAbsolutePath())) // ls
                .isInstanceOf(IllegalArgumentException.class) // ls
                .hasMessage("OpenAPI specification exceeds the configured size limit");
    }

    /**
     * Converts local-resource read failures into the loader's stable configuration error.
     *
     * @throws Exception if the mocked resource fails during input setup
     */
    @Test
    @DisplayName("Reports resource read failures safely")
    void reportsResourceReadFailures() throws Exception {
        // prepare //
        final ResourceLoader resourceLoader = mock(ResourceLoader.class);
        final Resource resource = mock(Resource.class);
        final OpenApiSpecificationLoader loader = new OpenApiSpecificationLoader(resourceLoader);
        when(resourceLoader.getResource("file:fixture.yaml")).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenThrow(new IOException("fixture read failure"));

        // act //
        // assert //
        assertThatThrownBy(() -> loader.load("file:fixture.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to read OpenAPI specification: file:fixture.yaml");
    }
}
