package com.cloudforge.core.interfaces;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ApplicationSpec and OidcIntegration interface default methods.
 */
class ApplicationInterfacesTest {

    // Minimal ApplicationSpec implementation
    static class MinimalApp implements ApplicationSpec {
        @Override
        public String applicationId() {
            return "test-app";
        }

        @Override
        public String defaultContainerImage() {
            return "test/image:latest";
        }

        @Override
        public int applicationPort() {
            return 8080;
        }

        @Override
        public String containerDataPath() {
            return "/var/data";
        }

        @Override
        public String efsDataPath() {
            return "/app-data";
        }

        @Override
        public String volumeName() {
            return "appData";
        }

        @Override
        public String containerUser() {
            return "1000:1000";
        }

        @Override
        public String efsPermissions() {
            return "750";
        }

        @Override
        public String ebsDeviceName() {
            return "/dev/xvdh";
        }

        @Override
        public String ec2DataPath() {
            return "/var/lib/app";
        }

        @Override
        public List<String> ec2LogPaths() {
            return List.of("/var/log/app/app.log");
        }

        @Override
        public void configureUserData(UserDataBuilder builder, Ec2Context context) {
            // Minimal implementation
        }
    }

    // ApplicationSpec with custom defaults
    static class CustomApp extends MinimalApp {
        @Override
        public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
            return Map.of("APP_URL", fqdn != null ? fqdn : "localhost");
        }

        @Override
        public String healthCheckPath() {
            return "/api/health";
        }

        @Override
        public boolean supportsOidcIntegration() {
            return true;
        }

        @Override
        public OidcIntegration getOidcIntegration() {
            return new TestOidcIntegration();
        }
    }

    // Minimal OidcIntegration implementation
    static class TestOidcIntegration implements OidcIntegration {
        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public String getIntegrationMethod() {
            return "Test OIDC Plugin";
        }

        @Override
        public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
            return Map.of("OIDC_ENABLED", "true");
        }

        @Override
        public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
            return List.of("echo 'OIDC configured'");
        }
    }

    // OidcIntegration with custom defaults
    static class CustomOidcIntegration extends TestOidcIntegration {
        @Override
        public String getConfigurationFile(OidcConfiguration config) {
            return "oidc.enabled=true\n";
        }

        @Override
        public String getConfigurationFilePath() {
            return "/etc/app/oidc.conf";
        }

        @Override
        public String getPostDeploymentInstructions() {
            return "Please restart the application after deployment.";
        }

        @Override
        public String getContainerStartupCommand() {
            return "/usr/bin/start-app.sh";
        }
    }

    // ========== ApplicationSpec Tests ==========

    @Test
    void testApplicationSpecRequiredMethods() {
        MinimalApp app = new MinimalApp();
        assertEquals("test-app", app.applicationId());
        assertEquals("test/image:latest", app.defaultContainerImage());
        assertEquals(8080, app.applicationPort());
        assertEquals("/var/data", app.containerDataPath());
        assertEquals("/app-data", app.efsDataPath());
        assertEquals("appData", app.volumeName());
        assertEquals("1000:1000", app.containerUser());
        assertEquals("750", app.efsPermissions());
        assertEquals("/dev/xvdh", app.ebsDeviceName());
        assertEquals("/var/lib/app", app.ec2DataPath());
        assertEquals(List.of("/var/log/app/app.log"), app.ec2LogPaths());
    }

    @Test
    void testApplicationSpecDefaultEnvironmentVariables() {
        MinimalApp app = new MinimalApp();
        Map<String, String> envVars = app.containerEnvironmentVariables("example.com", true, "alb-oidc");
        assertTrue(envVars.isEmpty());
    }

    @Test
    void testApplicationSpecCustomEnvironmentVariables() {
        CustomApp app = new CustomApp();
        Map<String, String> envVars = app.containerEnvironmentVariables("app.example.com", true, "none");
        assertEquals("app.example.com", envVars.get("APP_URL"));
    }

    @Test
    void testApplicationSpecCustomEnvironmentVariablesNullFqdn() {
        CustomApp app = new CustomApp();
        Map<String, String> envVars = app.containerEnvironmentVariables(null, false, null);
        assertEquals("localhost", envVars.get("APP_URL"));
    }

    @Test
    void testApplicationSpecDefaultHealthCheckPath() {
        MinimalApp app = new MinimalApp();
        assertEquals("/", app.healthCheckPath());
    }

    @Test
    void testApplicationSpecCustomHealthCheckPath() {
        CustomApp app = new CustomApp();
        assertEquals("/api/health", app.healthCheckPath());
    }

    @Test
    void testApplicationSpecDefaultOidcSupport() {
        MinimalApp app = new MinimalApp();
        assertFalse(app.supportsOidcIntegration());
        assertNull(app.getOidcIntegration());
    }

    @Test
    void testApplicationSpecCustomOidcSupport() {
        CustomApp app = new CustomApp();
        assertTrue(app.supportsOidcIntegration());
        assertNotNull(app.getOidcIntegration());
    }

    // ========== OidcIntegration Tests ==========

    @Test
    void testOidcIntegrationRequiredMethods() {
        TestOidcIntegration oidc = new TestOidcIntegration();
        assertTrue(oidc.isSupported());
        assertEquals("Test OIDC Plugin", oidc.getIntegrationMethod());
        assertEquals(Map.of("OIDC_ENABLED", "true"), oidc.getEnvironmentVariables(null));
        assertEquals(List.of("echo 'OIDC configured'"), oidc.getUserDataCommands(null, null));
    }

    @Test
    void testOidcIntegrationDefaultConfigurationFile() {
        TestOidcIntegration oidc = new TestOidcIntegration();
        assertNull(oidc.getConfigurationFile(null));
        assertNull(oidc.getConfigurationFilePath());
    }

    @Test
    void testOidcIntegrationCustomConfigurationFile() {
        CustomOidcIntegration oidc = new CustomOidcIntegration();
        assertEquals("oidc.enabled=true\n", oidc.getConfigurationFile(null));
        assertEquals("/etc/app/oidc.conf", oidc.getConfigurationFilePath());
    }

    @Test
    void testOidcIntegrationDefaultPostDeployment() {
        TestOidcIntegration oidc = new TestOidcIntegration();
        assertNull(oidc.getPostDeploymentInstructions());
    }

    @Test
    void testOidcIntegrationCustomPostDeployment() {
        CustomOidcIntegration oidc = new CustomOidcIntegration();
        assertEquals("Please restart the application after deployment.", oidc.getPostDeploymentInstructions());
    }

    @Test
    void testOidcIntegrationDefaultStartupCommand() {
        TestOidcIntegration oidc = new TestOidcIntegration();
        assertEquals("/usr/local/bin/start.sh", oidc.getContainerStartupCommand());
    }

    @Test
    void testOidcIntegrationCustomStartupCommand() {
        CustomOidcIntegration oidc = new CustomOidcIntegration();
        assertEquals("/usr/bin/start-app.sh", oidc.getContainerStartupCommand());
    }

    // ========== Integration Tests ==========

    @Test
    void testApplicationWithOidcIntegration() {
        CustomApp app = new CustomApp();
        assertTrue(app.supportsOidcIntegration());

        OidcIntegration oidc = app.getOidcIntegration();
        assertNotNull(oidc);
        assertTrue(oidc.isSupported());
        assertEquals("Test OIDC Plugin", oidc.getIntegrationMethod());
    }

    @Test
    void testMultipleApplicationInstances() {
        MinimalApp app1 = new MinimalApp();
        CustomApp app2 = new CustomApp();

        // Verify they are independent
        assertNotEquals(app1.healthCheckPath(), app2.healthCheckPath());
        assertNotEquals(app1.supportsOidcIntegration(), app2.supportsOidcIntegration());
    }

    @Test
    void testOidcConfigurationWithNullValues() {
        TestOidcIntegration oidc = new TestOidcIntegration();

        // Should handle null config gracefully
        assertDoesNotThrow(() -> oidc.getEnvironmentVariables(null));
        assertDoesNotThrow(() -> oidc.getUserDataCommands(null, null));
        assertDoesNotThrow(() -> oidc.getConfigurationFile(null));
    }
}
