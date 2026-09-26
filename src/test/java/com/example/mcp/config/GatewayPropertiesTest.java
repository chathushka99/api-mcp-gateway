package com.example.mcp.config;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies configuration properties expose their configured values.
 */
class GatewayPropertiesTest {

    /**
     * Exercises accessors for API, authentication, policy, and resource-limit settings.
     */
    @Test
    @DisplayName("Exposes configured API and gateway settings")
    void exposesConfiguredSettings() {
        // prepare //
        final GatewayProperties properties = new GatewayProperties();
        final GatewayProperties.Api api = new GatewayProperties.Api();
        final GatewayProperties.Authentication authentication = api.getAuth();
        final GatewayProperties.Operations operations = properties.getOperations();
        final GatewayProperties.Security security = properties.getSecurity();
        final GatewayProperties.Limits limits = properties.getLimits();
        final Duration timeout = Duration.ofSeconds(12);

        // act //
        api.setName("example");
        api.setBaseUrl("https://api.example.test");
        api.setOpenapi("classpath:openapi/example.yaml");
        api.setTimeout(timeout);
        api.setAllowedHeaders(List.of("X-Tenant"));
        authentication.setType("api-key");
        authentication.setHeaderName("X-API-Key");
        authentication.setCredential("fixture-key");
        authentication.setLocation("header");
        authentication.setParameterName("api_key");
        operations.setAllowMutating(true);
        security.setAllowedHosts(List.of("api.example.test"));
        security.setAllowPrivateTargets(true);
        limits.setMaxRequestBodyBytes(128);
        limits.setMaxResponseBodyBytes(256);
        limits.setConnectTimeout(timeout);
        properties.setApis(List.of(api));

        // assert //
        assertThat(properties.getApis()).containsExactly(api);
        assertThat(api.getName()).isEqualTo("example");
        assertThat(api.getBaseUrl()).isEqualTo("https://api.example.test");
        assertThat(api.getOpenapi()).isEqualTo("classpath:openapi/example.yaml");
        assertThat(api.getTimeout()).isEqualTo(timeout);
        assertThat(api.getAllowedHeaders()).containsExactly("X-Tenant");
        assertThat(authentication.getType()).isEqualTo("api-key");
        assertThat(authentication.getHeaderName()).isEqualTo("X-API-Key");
        assertThat(authentication.getCredential()).isEqualTo("fixture-key");
        assertThat(authentication.getLocation()).isEqualTo("header");
        assertThat(authentication.getParameterName()).isEqualTo("api_key");
        assertThat(operations.isAllowMutating()).isTrue();
        assertThat(security.getAllowedHosts()).containsExactly("api.example.test");
        assertThat(security.isAllowPrivateTargets()).isTrue();
        assertThat(limits.getMaxRequestBodyBytes()).isEqualTo(128);
        assertThat(limits.getMaxResponseBodyBytes()).isEqualTo(256);
        assertThat(limits.getConnectTimeout()).isEqualTo(timeout);
    }
}
