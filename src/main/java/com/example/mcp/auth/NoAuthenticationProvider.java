package com.example.mcp.auth;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.mcp.model.AuthenticationConfiguration;

/**
 * Supplies empty credentials for APIs that do not require authentication.
 */
@Component
public class NoAuthenticationProvider implements AuthenticationProvider {

    /**
     * Returns empty request authentication values.
     *
     * @param configuration authentication settings for the target API
     * @return empty headers and query parameters
     */
    @Override
    public AuthenticationValues credentials(AuthenticationConfiguration configuration) {
        return new AuthenticationValues(Map.of(), Map.of());
    }
}
