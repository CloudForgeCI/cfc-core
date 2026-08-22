package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Canonical coverage for {@link PreferredUrlResolver}. Mirrors
 * {@code cloudforge-manager}'s {@code CloudFormationInventoryPreferredUrlTest}, which now
 * delegates to this class — kept in sync since both apps share this precedence.
 */
class PreferredUrlResolverTest {

    @Test
    void prefersEdgeHostnameForLocalStackJenkins() {
        String url = PreferredUrlResolver.preferredUrl(Map.of(
            "CloudForgeApplicationId", "jenkins",
            "LocalStackApplicationUrl", "http://localhost:8080/",
            "LocalStackLocalUrl", "https://localhost.localstack.cloud:4566/_aws/elb/cfc-xnp5kj/"));
        assertEquals("http://jenkins.cloudforge.localhost/", url);
    }

    @Test
    void prefersEdgeHostnameForManager() {
        String url = PreferredUrlResolver.preferredUrl(Map.of(
            "CloudForgeApplicationId", "cloudforge-manager",
            "LocalStackApplicationUrl", "http://localhost:1958/"));
        assertEquals("http://manager.cloudforge.localhost/", url);
    }

    @Test
    void fallsBackToAwsStyleOutputsWithoutLocalEmulatorKeys() {
        String url = PreferredUrlResolver.preferredUrl(Map.of(
            "CloudForgeApplicationId", "jenkins",
            "ApplicationUrl", "https://ci.example.com/"));
        assertEquals("https://ci.example.com/", url);
    }

    @Test
    void unknownLocalAppFallsBackToHostPortUrl() {
        String url = PreferredUrlResolver.preferredUrl(Map.of(
            "CloudForgeApplicationId", "custom-app",
            "LocalStackApplicationUrl", "http://localhost:9999/"));
        assertEquals("http://localhost:9999/", url);
    }

    @Test
    void emptyOutputsYieldNull() {
        assertNull(PreferredUrlResolver.preferredUrl(Map.of()));
        assertNull(PreferredUrlResolver.preferredUrl(null));
    }
}
