package com.cloudforge.core.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerRuntimeBridgeTest {

    private static final String[] ALL_KEYS = {
        ManagerEnvKeys.TARGET, ManagerEnvKeys.AUTH_MODE, ManagerEnvKeys.PUBLIC_URL,
        ManagerEnvKeys.BIND, ManagerEnvKeys.PORT, ManagerEnvKeys.DB_MODE,
        ManagerEnvKeys.LOCALSTACK_ENDPOINT, ManagerEnvKeys.AWS_ENDPOINT_URL,
        ManagerEnvKeys.AWS_DEFAULT_REGION, ManagerEnvKeys.MINISTACK_ENDPOINT,
        ManagerEnvKeys.OIDC_CLIENT_ID, ManagerEnvKeys.OIDC_REDIRECT_URL
    };

    @AfterEach
    void clearSystemProperties() {
        for (String key : ALL_KEYS) {
            System.clearProperty(key);
        }
    }

    @Test
    void applyRequiresNonNullLookup() {
        assertThrows(NullPointerException.class, () -> ManagerRuntimeBridge.apply(null));
    }

    @Test
    void applySetsSystemPropertiesFromLookup() {
        Map<String, String> values = new HashMap<>();
        values.put(ManagerEnvKeys.PROP_TARGET, "ministack");
        values.put(ManagerEnvKeys.PROP_AUTH_MODE, "none");
        values.put(ManagerEnvKeys.PROP_PUBLIC_URL, "http://cloudforge.localhost");
        values.put("server.address", "0.0.0.0");
        values.put("server.port", "1958");
        values.put(ManagerEnvKeys.PROP_DB_MODE, "embedded");

        ManagerRuntimeBridge.apply(values::get);

        assertEquals("ministack", System.getProperty(ManagerEnvKeys.TARGET));
        assertEquals("none", System.getProperty(ManagerEnvKeys.AUTH_MODE));
        assertEquals("http://cloudforge.localhost", System.getProperty(ManagerEnvKeys.PUBLIC_URL));
        assertEquals("0.0.0.0", System.getProperty(ManagerEnvKeys.BIND));
        assertEquals("1958", System.getProperty(ManagerEnvKeys.PORT));
        assertEquals("embedded", System.getProperty(ManagerEnvKeys.DB_MODE));
    }

    @Test
    void localstackFallsBackToAwsEndpointWhenAbsent() {
        Map<String, String> values = new HashMap<>();
        values.put(ManagerEnvKeys.PROP_AWS_ENDPOINT_URL, "http://aws.localhost:4566");

        ManagerRuntimeBridge.apply(values::get);

        assertEquals("http://aws.localhost:4566", System.getProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT));
        assertEquals("http://aws.localhost:4566", System.getProperty(ManagerEnvKeys.AWS_ENDPOINT_URL));
        // ministack also falls back to the same awsEndpoint when neither ministack nor
        // localstack were explicitly supplied.
        assertEquals("http://aws.localhost:4566", System.getProperty(ManagerEnvKeys.MINISTACK_ENDPOINT));
    }

    @Test
    void ministackFallsBackToLocalstackWhenAwsEndpointAbsent() {
        Map<String, String> values = new HashMap<>();
        values.put(ManagerEnvKeys.PROP_LOCALSTACK_ENDPOINT, "http://localstack.localhost:4566");

        ManagerRuntimeBridge.apply(values::get);

        assertEquals("http://localstack.localhost:4566", System.getProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT));
        assertEquals("http://localstack.localhost:4566", System.getProperty(ManagerEnvKeys.MINISTACK_ENDPOINT));
    }

    @Test
    void awsDefaultRegionDefaultsToUsEast1WhenUnset() {
        Map<String, String> values = new HashMap<>();

        ManagerRuntimeBridge.apply(values::get);

        assertEquals("us-east-1", System.getProperty(ManagerEnvKeys.AWS_DEFAULT_REGION));
    }

    @Test
    void awsDefaultRegionHonorsExplicitLookupValue() {
        Map<String, String> values = new HashMap<>();
        values.put(ManagerEnvKeys.PROP_AWS_REGION, "eu-west-1");

        ManagerRuntimeBridge.apply(values::get);

        assertEquals("eu-west-1", System.getProperty(ManagerEnvKeys.AWS_DEFAULT_REGION));
    }

    @Test
    void applySetsSuppliedOidcKeysAndSkipsBlankOnes() {
        Map<String, String> values = new HashMap<>();
        values.put(ManagerEnvKeys.OIDC_CLIENT_ID, "abc123");
        values.put(ManagerEnvKeys.OIDC_REDIRECT_URL, "  ");

        ManagerRuntimeBridge.apply(values::get);

        assertEquals("abc123", System.getProperty(ManagerEnvKeys.OIDC_CLIENT_ID));
        assertNull(System.getProperty(ManagerEnvKeys.OIDC_REDIRECT_URL));
    }

    @Test
    void oidcKeyCatalogMapsEachKeyToItself() {
        Map<String, String> catalog = ManagerRuntimeBridge.oidcKeyCatalog();

        assertEquals(14, catalog.size());
        assertTrue(catalog.containsKey(ManagerEnvKeys.OIDC_ISSUER));
        assertEquals(ManagerEnvKeys.OIDC_ISSUER, catalog.get(ManagerEnvKeys.OIDC_ISSUER));
        assertEquals(ManagerEnvKeys.OIDC_MANAGER_GROUP, catalog.get(ManagerEnvKeys.OIDC_MANAGER_GROUP));
    }
}
