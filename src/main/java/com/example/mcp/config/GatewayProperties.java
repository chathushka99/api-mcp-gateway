package com.example.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds API definitions, execution options, security, and resource limits.
 */
@ConfigurationProperties("gateway")
public class GatewayProperties {

    private List<Api> apis = new ArrayList<>();

    private final Operations operations = new Operations();

    private final Security security = new Security();

    private final Limits limits = new Limits();

    /**
     * Returns configured API definitions.
     *
     * @return mutable API configuration list
     */
    public List<Api> getApis() {
        return this.apis;
    }

    /**
     * Replaces the configured API definitions.
     *
     * @param apis configured APIs
     */
    public void setApis(List<Api> apis) {
        this.apis = apis;
    }

    /**
     * Returns gateway operation options.
     *
     * @return operation settings
     */
    public Operations getOperations() {
        return this.operations;
    }

    /**
     * Returns outbound security settings.
     *
     * @return security settings
     */
    public Security getSecurity() {
        return this.security;
    }

    /**
     * Returns gateway resource limits.
     *
     * @return request and response limits
     */
    public Limits getLimits() {
        return this.limits;
    }

    /**
     * Holds the configuration for one target API.
     */
    public static class Api {
        private String name;
        private String baseUrl;
        private String openapi;

        private Duration timeout = Duration.ofSeconds(10);

        private List<String> allowedHeaders = new ArrayList<>();

        private final Authentication auth = new Authentication();

        /**
         * Returns the configured API name.
         *
         * @return API name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Sets the configured API name.
         *
         * @param name API name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Returns the target API base URL.
         *
         * @return base URL
         */
        public String getBaseUrl() {
            return this.baseUrl;
        }

        /**
         * Sets the target API base URL.
         *
         * @param baseUrl target base URL
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Returns the OpenAPI document location.
         *
         * @return document location
         */
        public String getOpenapi() {
            return this.openapi;
        }

        /**
         * Sets the OpenAPI document location.
         *
         * @param openapi document location
         */
        public void setOpenapi(String openapi) {
            this.openapi = openapi;
        }

        /**
         * Returns the target request timeout.
         *
         * @return request timeout
         */
        public Duration getTimeout() {
            return this.timeout;
        }

        /**
         * Sets the target request timeout.
         *
         * @param timeout request timeout
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /**
         * Returns headers explicitly allowed for this API.
         *
         * @return allow-listed headers
         */
        public List<String> getAllowedHeaders() {
            return this.allowedHeaders;
        }

        /**
         * Sets headers explicitly allowed for this API.
         *
         * @param allowedHeaders allow-listed headers
         */
        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        /**
         * Returns authentication settings for this API.
         *
         * @return authentication settings
         */
        public Authentication getAuth() {
            return this.auth;
        }
    }

    /**
     * Holds authentication options for an API.
     */
    public static class Authentication {
        private String type = "none";


        private String credential;
        private String headerName = "X-API-Key";
        private String location = "header";
        private String parameterName;

        /**
         * Returns the authentication mechanism name.
         *
         * @return mechanism name
         */
        public String getType() {
            return this.type;
        }

        /**
         * Sets the authentication mechanism name.
         *
         * @param type mechanism name
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Returns the API-key header name.
         *
         * @return header name
         */
        public String getHeaderName() {
            return this.headerName;
        }

        /**
         * Sets the API-key header name.
         *
         * @param headerName header name
         */
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        /**
         * Returns the configured authentication secret.
         *
         * @return credential value
         */
        public String getCredential() {
            return this.credential;
        }

        /**
         * Sets the configured authentication secret.
         *
         * @param credential credential value
         */
        public void setCredential(String credential) {
            this.credential = credential;
        }

        /**
         * Returns the API-key location.
         *
         * @return location name
         */
        public String getLocation() {
            return this.location;
        }

        /**
         * Sets the API-key location.
         *
         * @param location location name
         */
        public void setLocation(String location) {
            this.location = location;
        }

        /**
         * Returns the API-key query parameter name.
         *
         * @return parameter name
         */
        public String getParameterName() {
            return this.parameterName;
        }

        /**
         * Sets the API-key query parameter name.
         *
         * @param parameterName parameter name
         */
        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }
    }

    /**
     * Holds whether mutating API operations may execute.
     */
    public static class Operations {
        private boolean allowMutating;

        /**
         * Indicates whether mutating operations are enabled.
         *
         * @return {@code true} when mutating operations are enabled
         */
        public boolean isAllowMutating() {
            return this.allowMutating;
        }

        /**
         * Enables or disables mutating operations.
         *
         * @param allowMutating whether mutations are enabled
         */
        public void setAllowMutating(boolean allowMutating) {
            this.allowMutating = allowMutating;
        }
    }

    /**
     * Holds outbound host allow-list and private-target options.
     */
    public static class Security {
        private List<String> allowedHosts = new ArrayList<>();

        private boolean allowPrivateTargets;

        /**
         * Returns permitted outbound host names.
         *
         * @return allowed hosts
         */
        public List<String> getAllowedHosts() {
            return this.allowedHosts;
        }

        /**
         * Sets permitted outbound host names.
         *
         * @param allowedHosts allowed hosts
         */
        public void setAllowedHosts(List<String> allowedHosts) {
            this.allowedHosts = allowedHosts;
        }

        /**
         * Indicates whether private network targets are permitted.
         *
         * @return {@code true} when private targets are allowed
         */
        public boolean isAllowPrivateTargets() {
            return this.allowPrivateTargets;
        }

        /**
         * Enables or disables private network targets.
         *
         * @param allowPrivateTargets whether private targets are allowed
         */
        public void setAllowPrivateTargets(boolean allowPrivateTargets) {
            this.allowPrivateTargets = allowPrivateTargets;
        }
    }

    /**
     * Holds outbound request, response, and connection limits.
     */
    public static class Limits {
        private int maxRequestBodyBytes = 1_048_576;
        private int maxResponseBodyBytes = 2_097_152;

        private Duration connectTimeout = Duration.ofSeconds(3);

        /**
         * Returns the maximum serialized request body size.
         *
         * @return request body limit in bytes
         */
        public int getMaxRequestBodyBytes() {
            return this.maxRequestBodyBytes;
        }

        /**
         * Sets the maximum serialized request body size.
         *
         * @param maxRequestBodyBytes request body limit in bytes
         */
        public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
            this.maxRequestBodyBytes = maxRequestBodyBytes;
        }

        /**
         * Returns the maximum buffered response body size.
         *
         * @return response body limit in bytes
         */
        public int getMaxResponseBodyBytes() {
            return this.maxResponseBodyBytes;
        }

        /**
         * Sets the maximum buffered response body size.
         *
         * @param maxResponseBodyBytes response body limit in bytes
         */
        public void setMaxResponseBodyBytes(int maxResponseBodyBytes) {
            this.maxResponseBodyBytes = maxResponseBodyBytes;
        }

        /**
         * Returns the outbound connection timeout.
         *
         * @return connection timeout
         */
        public Duration getConnectTimeout() {
            return this.connectTimeout;
        }

        /**
         * Sets the outbound connection timeout.
         *
         * @param connectTimeout connection timeout
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }
    }
}
