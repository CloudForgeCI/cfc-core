package com.cloudforge.core.interfaces;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code isObjectCacheEnabled}'s default-method logic — {@code getCacheBackend()} arriving null
 *  or blank (a {@code CmsSpec} implementation that hasn't configured a backend yet, including
 *  third-party plugins not reviewed alongside the built-in specs) must fail safe to "disabled,"
 *  not silently report caching as active. */
class CmsObjectCacheSpecTest {

    private static CmsObjectCacheSpec withBackend(String backend) {
        return new CmsObjectCacheSpec() {
            @Override public String getCacheBackend() { return backend; }
            @Override public String getCacheEndpoint() { return "localhost"; }
            @Override public int getCachePort() { return 6379; }
            @Override public Map<String, String> getCachePluginEnvironment() { return Map.of(); }
            @Override public String getCachePasswordSecretArn() { return null; }
        };
    }

    @Test
    void nullBackendIsTreatedAsDisabledNotEnabled() {
        assertFalse(withBackend(null).isObjectCacheEnabled());
    }

    @Test
    void blankBackendIsTreatedAsDisabled() {
        assertFalse(withBackend("").isObjectCacheEnabled());
        assertFalse(withBackend("   ").isObjectCacheEnabled());
    }

    @Test
    void explicitNoneIsDisabled() {
        assertFalse(withBackend("none").isObjectCacheEnabled());
    }

    @Test
    void redisOrMemcachedIsEnabled() {
        assertTrue(withBackend("redis").isObjectCacheEnabled());
        assertTrue(withBackend("memcached").isObjectCacheEnabled());
    }
}
