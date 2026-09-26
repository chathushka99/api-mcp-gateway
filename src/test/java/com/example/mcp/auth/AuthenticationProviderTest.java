package com.example.mcp.auth;

import com.example.mcp.model.AuthenticationConfiguration;
import com.example.mcp.model.AuthenticationConfiguration.Location;
import com.example.mcp.model.AuthenticationConfiguration.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks credentials supplied by each authentication provider.
 */
class AuthenticationProviderTest {

    /**
     * Checks that bearer, header API-key, query API-key, and unauthenticated providers return the expected credentials.
     */
    @Test
    @DisplayName("bearer, header API-key, query API-key, and unauthenticated providers return the expected credentials")
    void supportsBearerAndStaticApiKeyCredentials() {
        // prepare //
        final AuthenticationValues bearer = new BearerTokenAuthenticationProvider().credentials( // ls
                new AuthenticationConfiguration(Type.BEARER, "Authorization", "token-value", Location.HEADER));
        final AuthenticationValues headerKey = new StaticApiKeyAuthenticationProvider().credentials( // ls
                new AuthenticationConfiguration(Type.API_KEY, "X-API-Key", "key-value", Location.HEADER));
        final AuthenticationValues queryKey = new StaticApiKeyAuthenticationProvider().credentials( // ls
                new AuthenticationConfiguration(Type.API_KEY, "api_key", "key-value", Location.QUERY));

        // act //
        // assert //
        assertThat(bearer.headers()).containsEntry("Authorization", "Bearer token-value");
        assertThat(headerKey.headers()).containsEntry("X-API-Key", "key-value");
        assertThat(queryKey.queryParameters()).containsEntry("api_key", "key-value");
        assertThat(new NoAuthenticationProvider().credentials(new AuthenticationConfiguration(Type.NONE, null, null, Location.HEADER))).isEqualTo(new AuthenticationValues(Map.of(), Map.of()));
    }

    /**
     * Verifies the provider registry dispatches every configured authentication mechanism.
     */
    @Test
    @DisplayName("Selects providers for no-auth, API-key, and bearer configurations")
    void selectsConfiguredAuthenticationProviders() {
        // prepare //
        final AuthenticationProviderRegistry registry = new AuthenticationProviderRegistry( // ls
                new NoAuthenticationProvider(), new StaticApiKeyAuthenticationProvider(), // ls
                new BearerTokenAuthenticationProvider());

        // act //
        final AuthenticationValues noAuth = registry.credentials( // ls
                new AuthenticationConfiguration(Type.NONE, null, null, Location.HEADER));
        final AuthenticationValues apiKey = registry.credentials( // ls
                new AuthenticationConfiguration(Type.API_KEY, "key", "fixture", Location.QUERY));
        final AuthenticationValues bearer = registry.credentials( // ls
                new AuthenticationConfiguration(Type.BEARER, "Authorization", "fixture", Location.HEADER));

        // assert //
        assertThat(noAuth.headers()).isEmpty();
        assertThat(apiKey.queryParameters()).containsEntry("key", "fixture");
        assertThat(bearer.headers()).containsEntry("Authorization", "Bearer fixture");
    }
}
