package com.example.mcp.auth;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.mcp.model.AuthenticationConfiguration;
import com.example.mcp.model.AuthenticationConfiguration.Location;

/**
 * Places a configured API key in either a request header or query parameter.
 */
@Component
public class StaticApiKeyAuthenticationProvider implements AuthenticationProvider {

    /**
     * Creates credentials at the configured request location.
     *
     * @param configuration API-key settings for the target API
     * @return credentials in the configured header or query parameter
     */
    @Override
    public AuthenticationValues credentials(AuthenticationConfiguration configuration) {
        if (configuration.location() == Location.QUERY) {
            return new AuthenticationValues(Map.of(), Map.of(configuration.name(), configuration.credential()));
        }
        return new AuthenticationValues(Map.of(configuration.name(), configuration.credential()), Map.of());
    }
}
