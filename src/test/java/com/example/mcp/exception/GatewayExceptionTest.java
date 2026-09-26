package com.example.mcp.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies gateway failures preserve their safe classification and target status.
 */
class GatewayExceptionTest {

    /**
     * Covers exception constructors with and without a cause or HTTP status.
     */
    @Test
    @DisplayName("Preserves error code, status, and cause")
    void preservesClassificationAndCause() {
        // prepare //
        final IllegalStateException cause = new IllegalStateException("fixture");

        // act //
        final GatewayException basic = new GatewayException(GatewayException.Code.UNKNOWN_TOOL, "unknown");
        final GatewayException caused = new GatewayException( // ls
                GatewayException.Code.EXECUTION_FAILURE, "failed", cause);
        final GatewayException targetFailure = new GatewayException( // ls
                GatewayException.Code.TARGET_CLIENT_ERROR, "target rejected request", 404);

        // assert //
        assertThat(basic.getCode()).isEqualTo(GatewayException.Code.UNKNOWN_TOOL);
        assertThat(basic.getStatusCode()).isNull();
        assertThat(caused.getCause()).isSameAs(cause);
        assertThat(caused.getStatusCode()).isNull();
        assertThat(targetFailure.getCode()).isEqualTo(GatewayException.Code.TARGET_CLIENT_ERROR);
        assertThat(targetFailure.getStatusCode()).isEqualTo(404);
    }
}
