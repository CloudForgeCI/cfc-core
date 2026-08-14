package com.cloudforge.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationPropertyLoaderTest {

    @BeforeEach
    void isolateFromShellEnv() {
        // Developer shells often export CFC_MANAGER_URL; env wins over -D in resolve().
        ApplicationPropertyLoader.overrideEnvironmentForTests(name -> null);
        ApplicationPropertyLoader.clearCache();
    }

    @AfterEach
    void clear() {
        ApplicationPropertyLoader.resetForTests();
        System.clearProperty("cfc.manager.url");
        System.clearProperty("CFC_MANAGER_URL");
    }

    @Test
    void toEnvNameMapsDottedKey() {
        assertEquals("CFC_MANAGER_URL", ApplicationPropertyLoader.toEnvName("cfc.manager.url"));
        assertEquals("CFC_MANAGER_HISTORY_TOKEN",
            ApplicationPropertyLoader.toEnvName("cfc.manager.history-token"));
    }

    @Test
    void systemPropertyOverridesFile() {
        System.setProperty("cfc.manager.url", "http://override:1958");
        assertEquals("http://override:1958", ApplicationPropertyLoader.resolve("cfc.manager.url"));
    }

    @Test
    void envOverridesSystemProperty() {
        ApplicationPropertyLoader.overrideEnvironmentForTests(name ->
            "CFC_MANAGER_URL".equals(name) ? "http://from-env:1958" : null);
        System.setProperty("cfc.manager.url", "http://from-sys:1958");
        assertEquals("http://from-env:1958", ApplicationPropertyLoader.resolve("cfc.manager.url"));
    }

    @Test
    void applyPropertyDefaultsFillsBlankManagerUrl() {
        System.setProperty("cfc.manager.url", "http://from-sys:1958");
        DeploymentConfig config = new DeploymentConfig();
        assertNull(config.managerUrl);
        ApplicationPropertyLoader.applyPropertyDefaults(config);
        assertEquals("http://from-sys:1958", config.managerUrl);
    }
}
