package com.example.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the enterprise API MCP gateway application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EnterpriseApiMcpGatewayApplication {

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(EnterpriseApiMcpGatewayApplication.class, args);
    }
}
