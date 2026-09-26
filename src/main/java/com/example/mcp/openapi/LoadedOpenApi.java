package com.example.mcp.openapi;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Pairs an OpenAPI model with the location from which it was loaded.
 *
 * @param source  OpenAPI document location
 * @param openApi parsed OpenAPI definition
 */
public record LoadedOpenApi(String source, OpenAPI openApi) {
}
