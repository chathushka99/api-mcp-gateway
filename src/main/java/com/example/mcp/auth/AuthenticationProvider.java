package com.example.mcp.auth;

import com.example.mcp.model.AuthenticationConfiguration;

/**
 * Supplies authentication values for requests sent to a configured API.
 */
public interface AuthenticationProvider {

    /**
     * Resolves request credentials for the selected authentication configuration.
     *
     * @param configuration authentication settings for the target API
     * @return request headers and query parameters containing the credentials
     */
    AuthenticationValues credentials(AuthenticationConfiguration configuration);
}
