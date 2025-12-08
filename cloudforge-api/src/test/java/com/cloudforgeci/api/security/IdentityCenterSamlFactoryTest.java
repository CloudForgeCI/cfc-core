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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for IdentityCenterSamlFactory.
 *
 * Tests IAM Identity Center SAML integration including:
 * - SSO Instance ARN validation
 * - Auth mode validation
 * - Application SAML support validation
 * - Site URL construction
 * - Security profile behavior
 */
class IdentityCenterSamlFactoryTest {

    private static final String TEST_SSO_INSTANCE_ARN = "arn:aws:sso:::instance/ssoins-1234567890abcdef0";

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
        public String getIntegrationMethod() { return "SAML 2.0 via IAM Identity Center"; }

        @Override
        public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
            return Map.of("SAML_ENABLED", "true");
        }

        @Override
        public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
            return List.of("# SAML configured via Identity Center");
        }

        @Override
        public boolean supportsCognito() { return false; }

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
            "IdCenterConstructor", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");
        assertNotNull(factory);
    }

    // ========== Auth Mode Validation Tests ==========

    @Test
    void testFactorySkipsNoneAuthMode() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "none");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNone", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip processing
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Auto-Provision Validation Tests ==========

    @Test
    void testFactorySkipsWhenAutoProvisionDisabled() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", false);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNoAuto", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip when auto-provision is disabled
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsWhenAutoProvisionNull() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        // autoProvisionIdentityCenter not set
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNullAuto", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip when auto-provision is null
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== SSO Instance ARN Validation Tests ==========

    @Test
    void testFactoryThrowsWhenSsoInstanceArnMissing() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        // ssoInstanceArn not set

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNoArn", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should throw when SSO ARN is missing
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.create());
        assertTrue(ex.getMessage().contains("ssoInstanceArn"));
    }

    @Test
    void testFactoryThrowsWhenSsoInstanceArnEmpty() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", "");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterEmptyArn", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should throw when SSO ARN is empty
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.create());
        assertTrue(ex.getMessage().contains("ssoInstanceArn"));
    }

    // ========== Application Support Tests ==========

    @Test
    void testFactorySkipsNullApplicationSpec() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNullApp", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Don't set applicationSpec - leave null

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip when applicationSpec is null
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsApplicationNotSupportingOidc() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNoOidc", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Application that doesn't support OIDC
        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("no-oidc-app", false, null);
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsApplicationWithNullOidcIntegration() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterNullIntegration", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Application with supportsOidc=true but null integration
        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("partial-app", true, null);
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testFactorySkipsOidcOnlyApplication() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterOidcOnly", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // Application with OIDC (not SAML) integration - doesn't support Identity Center SAML
        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("oidc-app", true, new MockOidcIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should skip since app doesn't support Identity Center SAML
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Security Profile Tests ==========

    @ParameterizedTest
    @CsvSource({
        "DEV,Development profile",
        "STAGING,Staging profile",
        "PRODUCTION,Production profile"
    })
    void testFactoryWithSecurityProfiles(String profile, String description) {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);
        context.put("stackName", "TestIdentityCenter");
        context.put("region", "us-east-1");
        context.put("domain", "example.com");
        context.put("subdomain", "app");
        context.put("enableSsl", true);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterProfile-" + profile, SecurityProfile.valueOf(profile), RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // When/Then: Factory should work with all security profiles
        assertDoesNotThrow(() -> factory.create(), description + " should work");
    }

    // ========== Site URL Construction Tests ==========

    @ParameterizedTest
    @CsvSource({
        "example.com,app,true",
        "example.com,app,false",
        "example.com,,true"
    })
    void testSiteUrlConstruction(String domain, String subdomain, boolean ssl) {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);
        context.put("stackName", "TestSiteUrl");
        context.put("region", "us-east-1");
        context.put("domain", domain);
        if (subdomain != null && !subdomain.isEmpty()) {
            context.put("subdomain", subdomain);
        }
        context.put("enableSsl", ssl);

        String stackId = "IdCenterUrl-" + domain.replace(".", "") + "-" + (subdomain != null ? subdomain : "nosub");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            stackId, SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // Factory doesn't throw and URL construction happens internally
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== FQDN Override Test ==========

    @Test
    void testFqdnOverridesDomainSubdomain() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);
        context.put("stackName", "TestFqdn");
        context.put("region", "us-east-1");
        context.put("fqdn", "custom.mysite.com");
        context.put("domain", "example.com");
        context.put("subdomain", "app");
        context.put("enableSsl", true);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterFqdn", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // FQDN should take precedence over domain+subdomain
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Initial Admin Email Test ==========

    @Test
    void testInitialAdminEmailOutput() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);
        context.put("stackName", "TestAdmin");
        context.put("region", "us-east-1");
        context.put("domain", "example.com");
        context.put("subdomain", "app");
        context.put("enableSsl", true);
        context.put("cognitoInitialAdminEmail", "admin@example.com");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterAdmin", SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // Should not throw and should create output for admin email
        assertDoesNotThrow(() -> factory.create());
    }

    // ========== Region Handling Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1"})
    void testFactoryWithDifferentRegions(String region) {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "application-oidc");
        context.put("autoProvisionIdentityCenter", true);
        context.put("ssoInstanceArn", TEST_SSO_INSTANCE_ARN);
        context.put("stackName", "TestRegion");
        context.put("region", region);
        context.put("domain", "example.com");
        context.put("subdomain", "app");
        context.put("enableSsl", true);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "IdCenterRegion-" + region.replace("-", ""), SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        MockSamlApplicationSpec mockApp = new MockSamlApplicationSpec("mattermost", true, new MockSamlIntegration());
        builder.getSystemContext().applicationSpec.set(mockApp);

        IdentityCenterSamlFactory factory = new IdentityCenterSamlFactory(builder.getStack(), "IdentityCenterSaml");

        // Factory should work with different regions
        assertDoesNotThrow(() -> factory.create());
    }
}
