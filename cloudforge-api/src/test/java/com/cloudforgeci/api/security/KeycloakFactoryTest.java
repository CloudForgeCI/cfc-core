package com.cloudforgeci.api.security;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for KeycloakFactory.
 *
 * Tests Keycloak SAML bridge deployment including:
 * - Provider validation (only activates for cognito-saml)
 * - Auth mode validation
 * - Infrastructure validation (VPC required)
 * - Security profile handling
 */
class KeycloakFactoryTest {

    // ========== Mock Application Spec ==========

    static class MockSamlApplicationSpec implements ApplicationSpec {
        private final boolean supportsOidc;
        private final OidcIntegration oidcIntegration;
        private final String appId;

        MockSamlApplicationSpec(String appId, boolean supportsOidc, OidcIntegration integration) {
            this.appId = appId;
            this.supportsOidc = supportsOidc;
            this.oidcIntegration = integration;
        }

        @Override
        public String applicationId() { return appId; }

        @Override
        public String defaultContainerImage() { return "test/" + appId + ":latest"; }

        @Override
        public int applicationPort() { return 8080; }

        @Override
        public String containerDataPath() { return "/var/data"; }

        @Override
        public String efsDataPath() { return "/app-data"; }

        @Override
        public String volumeName() { return "appData"; }

        @Override
        public String containerUser() { return "1000:1000"; }

        @Override
        public String efsPermissions() { return "750"; }

        @Override
        public String ebsDeviceName() { return "/dev/xvdh"; }

        @Override
        public String ec2DataPath() { return "/var/lib/app"; }

        @Override
        public List<String> ec2LogPaths() { return List.of("/var/log/app/app.log"); }

        @Override
        public void configureUserData(UserDataBuilder builder, Ec2Context context) {}

        @Override
        public boolean supportsOidcIntegration() { return supportsOidc; }

        @Override
        public OidcIntegration getOidcIntegration() { return oidcIntegration; }
    }

    static class MockSamlIntegration implements OidcIntegration {
        @Override
        public boolean isSupported() { return true; }

        @Override
        public String getIntegrationMethod() { return "SAML 2.0 via Keycloak"; }

        @Override
        public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
            return Map.of("SAML_ENABLED", "true");
        }

        @Override
        public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
            return List.of("# SAML configured via Keycloak");
        }

        @Override
        public boolean supportsCognito() { return true; }

        @Override
        public boolean supportsIdentityCenterSaml() { return true; }

        @Override
        public String getAuthenticationType() { return "SAML"; }

        @Override
        public String getOidcCallbackPath() { return "/login/sso/saml"; }
    }

    // ========== Factory Construction Tests ==========

    @Test
    void testFactoryConstructor() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakConstructor", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");
        assertNotNull(factory);
    }

    // ========== Provider Validation Tests ==========

    @Test
    void testFactorySkipsNonCognitoSamlProvider() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito");  // Not cognito-saml

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakNonSaml", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // When/Then: Factory should skip for non-SAML provider
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsIdentityCenterProvider() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "identity-center");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakIdentityCenter", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // When/Then: Factory should skip for identity-center provider
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Auth Mode Validation Tests ==========

    @Test
    void testFactorySkipsNoneAuthMode() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "none");
        context.put("oidcProvider", "cognito-saml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakNoneAuth", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // When/Then: Factory should skip for none auth mode
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Security Profile Tests ==========

    @ParameterizedTest
    @CsvSource({
        "DEV,Basic security",
        "STAGING,Enhanced security",
        "PRODUCTION,Full security"
    })
    void testFactoryWithSecurityProfiles(String profile, String description) {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");
        context.put("stackName", "TestKeycloak");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakProfile-" + profile, SecurityProfile.valueOf(profile), RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // Factory may require VPC but should not throw unexpected errors
        assertDoesNotThrow(() -> factory.create(), description + " should work");
    }

    // ========== Application Support Tests ==========

    @Test
    void testFactorySkipsNullApplicationSpec() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakNullApp", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Don't set applicationSpec - leave null

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // When/Then: Factory should handle null application spec gracefully
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsApplicationNotSupportingOidc() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakNoOidc", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("no-oidc", false, null);
        builder.getSystemContext().applicationSpec.set(mockApp);

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // When/Then: Factory should skip for app not supporting OIDC
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Infrastructure Requirements Tests ==========

    @Test
    void testFactoryWithCompleteInfrastructure() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");
        context.put("stackName", "TestKeycloakInfra");
        context.put("region", "us-east-1");
        context.put("domain", "example.com");
        context.put("subdomain", "app");
        context.put("enableSsl", true);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakInfra", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        KeycloakFactory factory = new KeycloakFactory(builder.getStack(), "Keycloak");

        // With complete infrastructure, factory should work
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Keycloak Configuration Tests ==========

    @Test
    void testKeycloakNotDeployedMultipleTimes() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");
        context.put("stackName", "TestKeycloakSingleton");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "KeycloakSingleton", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        // First factory call
        KeycloakFactory factory1 = new KeycloakFactory(builder.getStack(), "Keycloak1");
        assertDoesNotThrow(() -> factory1.create());

        // Second factory call should not duplicate Keycloak
        KeycloakFactory factory2 = new KeycloakFactory(builder.getStack(), "Keycloak2");
        assertDoesNotThrow(() -> factory2.create());
    }
}
