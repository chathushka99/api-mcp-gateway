package com.example.mcp.security;

import com.example.mcp.config.GatewayProperties;
import com.example.mcp.exception.GatewayException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Restricts outbound API destinations to explicitly permitted safe targets.
 */
@Component
public class TargetUrlPolicy {

    private final Set<String> allowedHosts;
    private final boolean allowPrivateTargets;

    /**
     * Creates a target policy from the configured host and private-address rules.
     *
     * @param properties gateway security configuration
     */
    public TargetUrlPolicy(GatewayProperties properties) {
        this.allowedHosts = properties.getSecurity().getAllowedHosts().stream() // ls
                .map(host -> host.toLowerCase(Locale.ROOT)) // ls
                .collect(Collectors.toUnmodifiableSet());
        this.allowPrivateTargets = properties.getSecurity().isAllowPrivateTargets();
    }

    /**
     * Validates and parses a configured HTTP(S) base URL.
     *
     * @param configuredUrl configured target URL
     * @return validated target URI
     * @throws GatewayException if the URL is not permitted
     */
    public URI validateConfiguredBaseUrl(String configuredUrl) {
        try {
            final URI uri = URI.create(configuredUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || // ls
                    uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || // ls
                    uri.getFragment() != null || // ls
                    ("http".equalsIgnoreCase(uri.getScheme()) && !this.allowPrivateTargets) || // ls
                    !this.allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw unsafeTarget();
            }
            this.checkLiteralAddress(uri.getHost());
            return uri;
        } catch (IllegalArgumentException exception) {
            throw unsafeTarget();
        }
    }

    /**
     * Rechecks the resolved target immediately before outbound execution.
     *
     * @param target resolved target URI
     * @throws GatewayException if the target is not permitted or resolvable
     */
    public void verifyResolvedTarget(URI target) {
        if (!this.allowedHosts.contains(target.getHost().toLowerCase(Locale.ROOT))) {
            throw unsafeTarget();
        }
        if (this.allowPrivateTargets) {
            return;
        }
        try {
            for (final InetAddress address : InetAddress.getAllByName(target.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || // ls
                        address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw unsafeTarget();
                }
            }
        } catch (UnknownHostException exception) {
            throw new GatewayException(GatewayException.Code.UNSAFE_TARGET, "Target host could not be resolved");
        }
    }

    /**
     * Rejects literal private or malformed IP addresses when private targets are disabled.
     *
     * @param host host name or address to inspect
     * @throws GatewayException if the literal address is unsafe
     */
    private void checkLiteralAddress(String host) {
        if (this.allowPrivateTargets || !isIpAddress(host)) {
            return;
        }
        try {
            final InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || // ls
                    address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw unsafeTarget();
            }
        } catch (UnknownHostException exception) {
            throw unsafeTarget();
        }
    }

    /**
     * Identifies IPv4- or IPv6-shaped host strings.
     *
     * @param host host string to inspect
     * @return {@code true} if the host has an IP literal shape
     */
    private static boolean isIpAddress(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    /**
     * Creates the standard exception used for a disallowed target.
     *
     * @return classified unsafe-target exception
     */
    private static GatewayException unsafeTarget() {
        return new GatewayException(GatewayException.Code.UNSAFE_TARGET, // ls
                "Target URL is not permitted by gateway security policy");
    }
}
