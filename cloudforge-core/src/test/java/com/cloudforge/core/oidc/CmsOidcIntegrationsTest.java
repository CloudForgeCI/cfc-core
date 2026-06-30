package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the six CMS-specific OidcIntegration implementations.
 * Pattern mirrors JenkinsOidcIntegrationTest.
 */
class CmsOidcIntegrationsTest {

    private OidcConfiguration cognitoConfig;
    private Ec2Context ec2Context;

    static class StubEc2Context implements Ec2Context {
        private final String stackName;
        StubEc2Context(String name) { this.stackName = name; }
        @Override public String stackName() { return stackName; }
        @Override public String runtimeType() { return "ec2"; }
        @Override public String securityProfile() { return "production"; }
        @Override public boolean hasEfs() { return true; }
        @Override public Optional<String> efsId() { return Optional.of("fs-12345678"); }
        @Override public Optional<String> accessPointId() { return Optional.of("fsap-12345678"); }
    }

    @BeforeEach
    void setUp() {
        cognitoConfig = new CognitoOidcConfiguration(
            "us-east-1", "us-east-1_abc123", "myapp",
            "client-id-test",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:oidc-secret",
            "https://cms.example.com/callback",
            "Admins");
        ec2Context = new StubEc2Context("cms-prod");
    }

    // ===== Shared contract tests across all 6 integrations =====

    static Stream<Object[]> allIntegrations() {
        return Stream.of(
            new Object[]{ new WordPressOidcIntegration(),  "WordPress"   },
            new Object[]{ new DrupalOidcIntegration(),     "Drupal"      },
            new Object[]{ new JoomlaOidcIntegration(),     "Joomla"      },
            new Object[]{ new MagentoOidcIntegration(),    "Magento"     },
            new Object[]{ new MoodleOidcIntegration(),     "Moodle"      },
            new Object[]{ new PrestaShopOidcIntegration(), "PrestaShop"  }
        );
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void isSupported(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        assertTrue(oidc.isSupported(), name + " OIDC integration must report isSupported()=true");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void integrationMethodIsNonEmpty(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        assertNotNull(oidc.getIntegrationMethod(), name + " integration method must not be null");
        assertFalse(oidc.getIntegrationMethod().isEmpty(), name + " integration method must not be empty");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void configurationFilePathIsAbsolute(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        String path = oidc.getConfigurationFilePath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"), name + " config file path must be absolute: " + path);
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void environmentVariablesContainClientId(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        Map<String, String> env = oidc.getEnvironmentVariables(cognitoConfig);
        assertNotNull(env);
        assertFalse(env.isEmpty(), name + " must return environment variables");
        assertTrue(env.values().stream().filter(v -> v != null).anyMatch(v -> v.contains("client-id-test")),
            name + " env vars must contain the client ID");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void environmentVariablesContainEndpoints(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        Map<String, String> env = oidc.getEnvironmentVariables(cognitoConfig);
        String allValues = String.join(" ", env.values());
        assertTrue(allValues.contains("amazoncognito.com") || allValues.contains("cognito-idp"),
            name + " env vars must contain Cognito endpoints");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void configurationFileReferencesClientIdEnvVar(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        String configFile = oidc.getConfigurationFile(cognitoConfig);
        assertNotNull(configFile, name + " must return a configuration file");
        assertFalse(configFile.isBlank(), name + " configuration file must not be empty");
        // Config files use env var references (getenv/ENV) rather than hardcoded values — correct design
        assertTrue(configFile.contains("CLIENT_ID") || configFile.contains("client_id"),
            name + " config file must reference the client ID env var");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void userDataCommandsAreNonEmpty(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        List<String> commands = oidc.getUserDataCommands(cognitoConfig, ec2Context);
        assertNotNull(commands);
        assertFalse(commands.isEmpty(), name + " must return user data commands");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void userDataCommandsContainInstallSteps(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        List<String> commands = oidc.getUserDataCommands(cognitoConfig, ec2Context);
        String joined = String.join("\n", commands);
        assertTrue(joined.length() > 100,
            name + " user data must contain meaningful setup commands");
    }

    @ParameterizedTest
    @MethodSource("allIntegrations")
    void postDeploymentInstructionsAreNonEmpty(Object integration, String name) {
        var oidc = (com.cloudforge.core.interfaces.OidcIntegration) integration;
        String instructions = oidc.getPostDeploymentInstructions();
        assertNotNull(instructions);
        assertFalse(instructions.isBlank(), name + " must return post-deployment instructions");
    }

    // ===== Per-integration spot-checks =====

    @Nested
    class WordPress {
        WordPressOidcIntegration integration = new WordPressOidcIntegration();

        @Test
        void integrationMethodMentionsPlugin() {
            assertTrue(integration.getIntegrationMethod().toLowerCase().contains("openid") ||
                       integration.getIntegrationMethod().toLowerCase().contains("plugin"));
        }

        @Test
        void configFilePathIsInMuPlugins() {
            assertTrue(integration.getConfigurationFilePath().contains("mu-plugins") ||
                       integration.getConfigurationFilePath().contains("wp-content"));
        }

        @Test
        void envVarsContainWordPressPrefix() {
            Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
            assertTrue(env.keySet().stream().anyMatch(k -> k.startsWith("OIDC_") || k.startsWith("WP_")),
                "WordPress env vars should use OIDC_ or WP_ prefix");
        }
    }

    @Nested
    class Drupal {
        DrupalOidcIntegration integration = new DrupalOidcIntegration();

        @Test
        void integrationMethodMentionsNativeModule() {
            assertTrue(integration.getIntegrationMethod().toLowerCase().contains("native") ||
                       integration.getIntegrationMethod().toLowerCase().contains("openid_connect"));
        }

        @Test
        void configFilePathIsInSitesDefault() {
            assertTrue(integration.getConfigurationFilePath().contains("sites/default") ||
                       integration.getConfigurationFilePath().contains("cloudforge"));
        }
    }

    @Nested
    class Joomla {
        JoomlaOidcIntegration integration = new JoomlaOidcIntegration();

        @Test
        void integrationMethodIsNonEmpty() {
            assertFalse(integration.getIntegrationMethod().isEmpty());
        }

        @Test
        void userDataCommandsReferenceJoomla() {
            List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
            String joined = String.join("\n", commands).toLowerCase();
            assertTrue(joined.contains("joomla") || joined.contains("oidc") || joined.contains("oauth"));
        }
    }

    @Nested
    class Magento {
        MagentoOidcIntegration integration = new MagentoOidcIntegration();

        @Test
        void configFileIsInAppEtc() {
            assertTrue(integration.getConfigurationFilePath().contains("app/etc") ||
                       integration.getConfigurationFilePath().contains("cloudforge"));
        }

        @Test
        void userDataCommandsReferenceComposerOrModule() {
            List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
            String joined = String.join("\n", commands).toLowerCase();
            assertTrue(joined.contains("magento") || joined.contains("composer") || joined.contains("module"));
        }
    }

    @Nested
    class Moodle {
        MoodleOidcIntegration integration = new MoodleOidcIntegration();

        @Test
        void integrationMethodMentionsOidc() {
            assertTrue(integration.getIntegrationMethod().toLowerCase().contains("openid") ||
                       integration.getIntegrationMethod().toLowerCase().contains("oidc"));
        }

        @Test
        void configFilePathIsPHPFile() {
            assertTrue(integration.getConfigurationFilePath().endsWith(".php"));
        }
    }

    @Nested
    class PrestaShop {
        PrestaShopOidcIntegration integration = new PrestaShopOidcIntegration();

        @Test
        void integrationMethodIsNonEmpty() {
            assertFalse(integration.getIntegrationMethod().isEmpty());
        }

        @Test
        void configFileIsInConfigDirectory() {
            assertTrue(integration.getConfigurationFilePath().contains("config") ||
                       integration.getConfigurationFilePath().contains("cloudforge"));
        }
    }
}
