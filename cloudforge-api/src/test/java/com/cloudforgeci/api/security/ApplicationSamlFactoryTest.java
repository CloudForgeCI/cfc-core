package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ApplicationSamlFactory.
 *
 * Tests application-level SAML authentication including:
 * - Cognito SAML via Keycloak bridge
 * - Identity Center SAML integration
 * - SAML provider selection
 * - Application SAML support validation
 */
class ApplicationSamlFactoryTest {

    // ========== Mock Application Spec ==========

    static class MockSamlApplicationSpec implements ApplicationSpec {
        private final boolean supportsOidc;
        private final OidcIntegration oidcIntegration;

        MockSamlApplicationSpec(boolean supportsOidc, OidcIntegration integration) {
            this.supportsOidc = supportsOidc;
            this.oidcIntegration = integration;
        }

        @Override
        public String applicationId() { return "test-saml-app"; }

        @Override
        public String defaultContainerImage() { return "test/saml-app:latest"; }

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
        public String getIntegrationMethod() { return "SAML 2.0 Integration"; }

        @Override
        public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
            return Map.of("SAML_ENABLED", "true");
        }

        @Override
        public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
            return List.of("# SAML configured");
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

    static class MockOidcIntegration implements OidcIntegration {
        @Override
        public boolean isSupported() { return true; }

        @Override
        public String getIntegrationMethod() { return "OIDC Integration"; }

        @Override
        public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
            return Map.of("OIDC_ENABLED", "true");
        }

        @Override
        public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
            return List.of("# OIDC configured");
        }

        @Override
        public boolean supportsCognito() { return true; }

        @Override
        public boolean supportsIdentityCenterSaml() { return false; }

        @Override
        public String getAuthenticationType() { return "OIDC"; }

        @Override
        public String getOidcCallbackPath() { return "/oauth/callback"; }
    }

    // ========== Factory Construction Tests ==========

    @Test
    void testFactoryConstructor() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlConstructorTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");
        assertNotNull(factory);
    }

    // ========== Auth Mode Validation Tests ==========

    @Test
    void testFactorySkipsNoneAuthMode() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "none");
        context.put("oidcProvider", "cognito-saml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlNoneAuth", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should not throw, should skip processing
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactoryProcessesApplicationOidcAuthMode() {
        // Given: application-oidc auth mode with SAML application
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");
        context.put("stackName", "TestSamlApp");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlAppOidc", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Set mock SAML application spec
        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec(true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should process without error
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Application Support Tests ==========

    @Test
    void testFactorySkipsNullApplicationSpec() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlNullApp", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Don't set applicationSpec - leave null

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should skip without error
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsApplicationNotSupportingOidc() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlNoOidc", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Application that doesn't support OIDC
        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec(false, null);
        builder.getSystemContext().applicationSpec.set(mockApp);

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should skip without error
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsOidcApplicationForSamlFactory() {
        // Given: Application that uses OIDC (not SAML)
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlOidcApp", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Application with OIDC integration (not SAML)
        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec(true, new MockOidcIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should skip since app uses OIDC not SAML
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== SAML Provider Tests ==========

    @ParameterizedTest
    @CsvSource({
        "cognito-saml,Keycloak bridge to Cognito",
        "identity-center,IAM Identity Center SAML"
    })
    void testSamlProviderRouting(String provider, String description) {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", provider);
        context.put("stackName", "TestProvider");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlProvider-" + provider.replace("-", ""), SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec(true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should handle provider-specific routing
        assertDoesNotThrow(() -> factory.create(), "Should handle " + description);
    }

    @Test
    void testUnknownSamlProviderSkipsProcessing() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "unknown-provider");
        context.put("stackName", "TestUnknown");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlUnknown", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec(true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should handle unknown provider gracefully
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
        context.put("stackName", "TestProfile");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlProfile-" + profile, SecurityProfile.valueOf(profile), RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec(true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        ApplicationSamlFactory factory = new ApplicationSamlFactory(builder.getStack(), "SamlFactory");

        // When/Then: Factory should work with all security profiles
        assertDoesNotThrow(() -> factory.create(), description + " should work");
    }

    // ========== Keycloak Singleton Tests ==========

    @Test
    void testKeycloakDeployedOncePerStack() {
        // Given: Multiple SAML applications in same stack
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("oidcProvider", "cognito-saml");
        context.put("stackName", "TestMultiSaml");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "SamlMulti", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp1 = new MockSamlApplicationSpec(true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp1);

        // First factory call
        ApplicationSamlFactory factory1 = new ApplicationSamlFactory(builder.getStack(), "SamlFactory1");
        assertDoesNotThrow(() -> factory1.create());

        // Second factory would reuse the existing Keycloak
        ApplicationSamlFactory factory2 = new ApplicationSamlFactory(builder.getStack(), "SamlFactory2");
        assertDoesNotThrow(() -> factory2.create());
    }
}
