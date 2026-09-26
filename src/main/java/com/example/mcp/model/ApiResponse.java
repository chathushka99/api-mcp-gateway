package com.example.mcp.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contains the status and decoded body returned by a target API.
 *
 * @param statusCode  HTTP response status code
 * @param body        decoded response body, or {@code null} when empty
 * @param contentType response content type
 */
public record ApiResponse(int statusCode, JsonNode body, String contentType) {
}
