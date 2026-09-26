package com.example.mcp.auth;

import org.springframework.stereotype.Component;

import com.example.mcp.model.AuthenticationConfiguration;
import com.example.mcp.model.AuthenticationConfiguration.Type;

/**
 * Selects the authentication strategy for each target API.
 */
@Component
public class AuthenticationProviderRegistry {

    private final NoAuthenticationProvider noAuthenticationProvider;
    private final StaticApiKeyAuthenticationProvider apiKeyAuthenticationProvider;
    private final BearerTokenAuthenticationProvider bearerTokenAuthenticationProvider;

    /**
     * Creates a registry of the supported authentication providers.
     *
     * @param noAuthenticationProvider          provider for unauthenticated APIs
     * @param apiKeyAuthenticationProvider      provider for static API keys
     * @param bearerTokenAuthenticationProvider provider for bearer tokens
     */
    public AuthenticationProviderRegistry(NoAuthenticationProvider noAuthenticationProvider, // ls
                                          StaticApiKeyAuthenticationProvider apiKeyAuthenticationProvider, // ls
                                          BearerTokenAuthenticationProvider bearerTokenAuthenticationProvider) {
        this.noAuthenticationProvider = noAuthenticationProvider;
        this.apiKeyAuthenticationProvider = apiKeyAuthenticationProvider;
        this.bearerTokenAuthenticationProvider = bearerTokenAuthenticationProvider;
    }

    /**
     * Resolves credentials using the provider matching the configured type.
     *
     * @param configuration authentication settings for the target API
     * @return request authentication values
     */
    public AuthenticationValues credentials(AuthenticationConfiguration configuration) {
        return switch (configuration.type()) {
            case NONE -> this.noAuthenticationProvider.credentials(configuration);
            case API_KEY -> this.apiKeyAuthenticationProvider.credentials(configuration);
            case BEARER -> this.bearerTokenAuthenticationProvider.credentials(configuration);
        };
    }
}
