package com.example.mcp.auth;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.mcp.model.AuthenticationConfiguration;

/**
 * Builds the Authorization header for bearer-token authentication.
 */
@Component
public class BearerTokenAuthenticationProvider implements AuthenticationProvider {

    /**
     * Creates the bearer authorization header for the target request.
     *
     * @param configuration authentication settings containing the token
     * @return credentials with an Authorization header
     */
    @Override
    public AuthenticationValues credentials(AuthenticationConfiguration configuration) {
        return new AuthenticationValues( // ls
                Map.of("Authorization", "Bearer " + configuration.credential()), Map.of());
    }
}
