package com.example.mcp.model;

/**
 * Describes how credentials are supplied to a configured API.
 *
 * @param type       authentication mechanism
 * @param name       header or query parameter name
 * @param credential configured secret value
 * @param location   request location for API-key authentication
 */
public record AuthenticationConfiguration(Type type, String name, String credential, Location location) {

    /**
     * Supported authentication mechanisms.
     */
    public enum Type {
        NONE, API_KEY, BEARER
    }

    /**
     * Request locations supported for API-key authentication.
     */
    public enum Location {
        HEADER, QUERY
    }
}
