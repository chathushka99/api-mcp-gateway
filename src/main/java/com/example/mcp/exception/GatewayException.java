package com.example.mcp.exception;

/**
 * Represents a safe, classified failure while processing a gateway request.
 */
public class GatewayException extends RuntimeException {

    private final Code code;
    private final Integer statusCode;

    /**
     * Creates an exception without a target HTTP status or underlying cause.
     *
     * @param code    gateway error classification
     * @param message safe error message
     */
    public GatewayException(Code code, String message) {
        super(message);
        this.code = code;
        this.statusCode = null;
    }

    /**
     * Creates an exception with an underlying cause.
     *
     * @param code    gateway error classification
     * @param message safe error message
     * @param cause   underlying failure
     */
    public GatewayException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = null;
    }

    /**
     * Creates an exception associated with a target HTTP status.
     *
     * @param code       gateway error classification
     * @param message    safe error message
     * @param statusCode target HTTP response status
     */
    public GatewayException(Code code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    /**
     * Returns the gateway error classification.
     *
     * @return the error code
     */
    public Code getCode() {
        return this.code;
    }

    /**
     * Returns the target HTTP status when the failure came from a response.
     *
     * @return target status code, or {@code null} when unavailable
     */
    public Integer getStatusCode() {
        return this.statusCode;
    }

    /**
     * Enumerates failures that can be safely reported to MCP clients.
     */
    public enum Code {
        INVALID_ARGUMENTS, // ls
        UNKNOWN_TOOL, // ls
        MUTATING_OPERATION_DISABLED, // ls
        UNSAFE_TARGET, // ls
        TIMEOUT, // ls
        CONNECTION_FAILURE, // ls
        TARGET_CLIENT_ERROR, // ls
        TARGET_SERVER_ERROR, // ls
        MALFORMED_RESPONSE, // ls
        RESPONSE_TOO_LARGE, // ls
        REQUEST_TOO_LARGE, // ls
        EXECUTION_FAILURE
    }
}
