package com.example.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

/**
 * Verifies the application entry point delegates launch arguments to Spring Boot.
 */
class EnterpriseApiMcpGatewayApplicationTest {

    /**
     * Launches the application entry point without starting a server.
     */
    @Test
    @DisplayName("Delegates application startup to Spring Boot")
    void delegatesStartupToSpringApplication() {
        // prepare //
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            // act //
            EnterpriseApiMcpGatewayApplication.main(new String[]{"--fixture"});

            // verify //
            springApplication.verify(() -> SpringApplication.run( // ls
                    EnterpriseApiMcpGatewayApplication.class, "--fixture"));
        }
    }
}
