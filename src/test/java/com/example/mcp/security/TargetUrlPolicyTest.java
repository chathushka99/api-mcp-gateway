package com.example.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.mcp.config.GatewayProperties;
import com.example.mcp.exception.GatewayException;

/**
 * Verifies outbound targets obey host and network-address policy.
 */
class TargetUrlPolicyTest {

	/**
	 * Verifies configured targets must be allow-listed and public by default.
	 */
	@Test
	@DisplayName("Requires allow-listed public targets by default")
	void requiresConfiguredHostsToBeAllowListedAndPublicByDefault() {
		// prepare //
		final GatewayProperties properties = new GatewayProperties();
		properties.getSecurity().setAllowedHosts(List.of("api.example.test"));
		final TargetUrlPolicy policy = new TargetUrlPolicy(properties);

		// act //
		// assert //
		assertThat(policy.validateConfiguredBaseUrl("https://api.example.test/v1")) // ls
				.isEqualTo(URI.create("https://api.example.test/v1"));
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://other.example.test")) // ls
				.isInstanceOf(GatewayException.class) // ls
				.hasMessage("Target URL is not permitted by gateway security policy");
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("http://api.example.test")) // ls
				.isInstanceOf(GatewayException.class);
	}

	/**
	 * Verifies loopback targets are blocked unless private targets are explicitly enabled.
	 */
	@Test
	@DisplayName("Blocks loopback targets unless explicitly allowed")
	void blocksLoopbackTargetsUnlessExplicitlyAllowed() {
		// prepare //
		final GatewayProperties properties = new GatewayProperties();
		properties.getSecurity().setAllowedHosts(List.of("127.0.0.1"));

		// act //
		// assert //
		assertThatThrownBy(() -> new TargetUrlPolicy(properties) // ls
				.validateConfiguredBaseUrl("http://127.0.0.1:8080")) // ls
				.isInstanceOf(GatewayException.class);
	}

	/**
	 * Verifies scheme, URL components, host resolution, and explicit private-target exceptions.
	 */
	@Test
	@DisplayName("Rejects unsafe URL forms and applies private-target exceptions")
	void validatesResolvedTargetsAndConfiguredUrlForms() {
		// prepare //
		final GatewayProperties properties = new GatewayProperties();
		final URI publicIpv6 = URI.create("https://[2001:4860:4860::8888]");
		final URI loopbackIpv6 = URI.create("https://[::1]");
		properties.getSecurity().setAllowedHosts(List.of( // ls
				"api.example.test", "127.0.0.1", "localhost", "8.8.8.8", "0.0.0.0", // ls
				"169.254.1.1", "10.0.0.1", "224.0.0.1", "999.999.999.999", // ls
				publicIpv6.getHost(), loopbackIpv6.getHost()));
		final TargetUrlPolicy policy = new TargetUrlPolicy(properties);

		// act //
		// assert //
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("ftp://api.example.test")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://user@api.example.test")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://api.example.test?query=1")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://api.example.test#fragment")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://localhost"))) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://api.example.test"))) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://other.example.test"))) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://0.0.0.0")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://169.254.1.1")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://10.0.0.1")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://224.0.0.1")) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl("https://999.999.999.999")) // ls
				.isInstanceOf(GatewayException.class);
		assertThat(policy.validateConfiguredBaseUrl("https://8.8.8.8")).isEqualTo(URI.create("https://8.8.8.8"));
		policy.verifyResolvedTarget(URI.create("https://8.8.8.8"));
		assertThat(policy.validateConfiguredBaseUrl(publicIpv6.toString())).isEqualTo(publicIpv6);
		policy.verifyResolvedTarget(publicIpv6);
		assertThatThrownBy(() -> policy.validateConfiguredBaseUrl(loopbackIpv6.toString())) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(loopbackIpv6)) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://0.0.0.0"))) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://169.254.1.1"))) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://10.0.0.1"))) // ls
				.isInstanceOf(GatewayException.class);
		assertThatThrownBy(() -> policy.verifyResolvedTarget(URI.create("https://224.0.0.1"))) // ls
				.isInstanceOf(GatewayException.class);

		final GatewayProperties privateProperties = new GatewayProperties();
		privateProperties.getSecurity().setAllowedHosts(List.of("127.0.0.1"));
		privateProperties.getSecurity().setAllowPrivateTargets(true);
		final TargetUrlPolicy privatePolicy = new TargetUrlPolicy(privateProperties);
		assertThat(privatePolicy.validateConfiguredBaseUrl("http://127.0.0.1")).isEqualTo(URI.create("http://127.0.0.1"));
		privatePolicy.verifyResolvedTarget(URI.create("http://127.0.0.1"));
	}
}
