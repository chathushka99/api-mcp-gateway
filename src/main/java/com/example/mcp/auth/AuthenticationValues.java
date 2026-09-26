package com.example.mcp.auth;

import java.util.Map;

/**
 * Holds authentication values that are applied to an outbound request.
 *
 * @param headers         HTTP headers carrying credentials
 * @param queryParameters query parameters carrying credentials
 */
public record AuthenticationValues(Map<String, String> headers, Map<String, String> queryParameters) {
}
