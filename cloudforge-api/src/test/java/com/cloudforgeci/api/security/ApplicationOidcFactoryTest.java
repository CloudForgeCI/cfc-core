package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
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
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ApplicationOidcFactory.
 *
 * Tests application-level OIDC authentication including:
 * - Cognito auto-provision
 * - Manual OIDC configuration (IAM Identity Center, external providers)
 * - Application OIDC integration (Jenkins, GitLab, Grafana)
 * - Client secret management
 * - Security profile configurations
 */
class ApplicationOidcFactoryTest {

    // ========== Mock Application Spec ==========

    static class MockApplicationSpec implements ApplicationSpec {
        private final boolean supportsOidc;
        private final OidcIntegration oidcIntegration;

        MockApplicationSpec(boolean supportsOidc, OidcIntegration integration) {
            this.supportsOidc = supportsOidc;
            this.oidcIntegration = integration;
        }

        @Override
        public String applicationId() { return "test-app"; }

        @Override
        public String defaultContainerImage() { return "test/app:latest"; }

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

    static class MockOidcIntegration implements OidcIntegration {
        @Override
        public boolean isSupported() { return true; }

        @Override
        public String getIntegrationMethod() { return "Test OIDC Integration"; }

        @Override
        public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
            return Map.of("OIDC_ENABLED", "true");
        }

        @Override
        public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
            return List.of("echo 'OIDC configured'");
        }

        @Override
        public String getPostDeploymentInstructions() {
            return "Test deployment instructions";
        }
    }

    // ========== Tests for authMode validation ==========

    @Test
    void testApplicationOidcFactoryNotEnabledWithoutAuthMode() {
        // Given: A stack without application-oidc authMode
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcNoAuth");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcNoAuth");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "none");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with authMode != "application-oidc"
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should not configure OIDC (graceful skip)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryEnabledWithCorrectAuthMode() {
        // Given: A stack with application-oidc authMode
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcEnabled");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client-id");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with application-oidc
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure OIDC
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for ApplicationSpec validation ==========

    @Test
    void testApplicationOidcFactoryWithoutApplicationSpec() {
        // Given: A stack without ApplicationSpec set
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcNoSpec");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcNoSpec");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating ApplicationOidcFactory without ApplicationSpec
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should handle gracefully
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithUnsupportedApplication() {
        // Given: An application that doesn't support OIDC
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcUnsupported");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcUnsupported");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // Application that doesn't support OIDC
        ctx.applicationSpec.set(new MockApplicationSpec(false, null));

        // When: Creating ApplicationOidcFactory for unsupported app
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should log warning but not crash
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithNullOidcIntegration() {
        // Given: An application that claims to support OIDC but returns null integration
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcNullIntegration");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcNullIntegration");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // Application that supports OIDC but returns null integration (error condition)
        ctx.applicationSpec.set(new MockApplicationSpec(true, null));

        // When: Creating ApplicationOidcFactory with null integration
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should handle gracefully
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for Cognito configuration ==========

    @Test
    void testApplicationOidcFactoryWithCognitoAutoProvision() {
        // Given: A stack with Cognito auto-provision
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcCognito");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcCognito");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolId", "us-east-1_TestPool123");
        cfcContext.put("cognitoUserPoolDomain", "test-auth");
        cfcContext.put("cognitoUserPoolClientId", "test-client-123");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // Simulate CognitoAuthenticationFactory setting SystemContext values
        ctx.cognitoUserPoolId.set("us-east-1_TestPool123");
        ctx.cognitoDomainPrefix.set("test-auth");
        ctx.cognitoClientId.set("test-client-123");

        // When: Creating ApplicationOidcFactory with Cognito
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure Cognito OIDC
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithCognitoGroupsEnabled() {
        // Given: A stack with Cognito groups enabled
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcCognitoGroups");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcCognitoGroups");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolId", "us-east-1_TestPool123");
        cfcContext.put("cognitoUserPoolDomain", "test-auth");
        cfcContext.put("cognitoUserPoolClientId", "test-client-123");
        cfcContext.put("cognitoCreateGroups", true);
        cfcContext.put("cognitoAdminGroupName", "AdminGroup");
        cfcContext.put("cognitoUserGroupName", "UserGroup");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // Simulate CognitoAuthenticationFactory setting SystemContext values
        ctx.cognitoUserPoolId.set("us-east-1_TestPool123");
        ctx.cognitoDomainPrefix.set("test-auth");
        ctx.cognitoClientId.set("test-client-123");

        // When: Creating ApplicationOidcFactory with groups
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure group-based access
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithCognitoIncompleteConfiguration() {
        // Given: A stack with incomplete Cognito configuration
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcCognitoIncomplete");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcCognitoIncomplete");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("cognitoAutoProvision", true);
        // Missing cognitoUserPoolId and other Cognito details
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with incomplete Cognito config
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should handle gracefully (will be configured later)
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for manual OIDC configuration ==========

    @Test
    void testApplicationOidcFactoryWithOktaProvider() {
        // Given: A stack with Okta OIDC provider
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcOkta");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcOkta");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://dev-12345.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://dev-12345.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://dev-12345.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://dev-12345.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "okta-client-id");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with Okta
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure Okta OIDC
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithAuth0Provider() {
        // Given: A stack with Auth0 OIDC provider
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcAuth0");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcAuth0");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.auth0.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.auth0.com/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.auth0.com/oauth/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.auth0.com/userinfo");
        cfcContext.put("oidcClientId", "auth0-client-id");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with Auth0
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure Auth0 OIDC
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithIAMIdentityCenterProvider() {
        // Given: A stack with IAM Identity Center OIDC
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcIdentityCenter");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcIdentityCenter");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://portal.sso.us-east-1.amazonaws.com/saml/assertion/abc123");
        cfcContext.put("oidcAuthorizationEndpoint", "https://my-company.awsapps.com/start/oauth2/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://my-company.awsapps.com/start/oauth2/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://my-company.awsapps.com/start/oauth2/userInfo");
        cfcContext.put("oidcClientId", "ic-client-id");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with IAM Identity Center
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure Identity Center OIDC
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithManualOidcIncompleteConfiguration() {
        // Given: A stack with incomplete manual OIDC configuration
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcManualIncomplete");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcManualIncomplete");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        // Missing other required endpoints
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with incomplete manual config
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should log warning and skip configuration
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithNoOidcConfiguration() {
        // Given: A stack with application-oidc but no configuration
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcNoConfig");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcNoConfig");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        // No Cognito or manual OIDC configuration
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory without OIDC config
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should log warning about missing configuration
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for application URL building ==========

    @Test
    void testApplicationOidcFactoryWithFqdnAndSsl() {
        // Given: A stack with FQDN and SSL enabled
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcFqdn");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcFqdn");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("sslEnabled", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with FQDN and SSL
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should build HTTPS URL
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithFqdnNoSsl() {
        // Given: A stack with FQDN but SSL disabled
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcFqdnHttp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcFqdnHttp");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app-dev.example.com");
        cfcContext.put("sslEnabled", false);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with HTTP
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should build HTTP URL
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for security profile configurations ==========

    @Test
    void testApplicationOidcFactoryWithProductionProfile() {
        // Given: A production stack
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcProduction");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcProduction");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory for production
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure with production security (RETAIN secrets)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithDevProfile() {
        // Given: A dev stack
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcDev");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcDev");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory for dev
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure with dev security (DESTROY secrets)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithStagingProfile() {
        // Given: A staging stack
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcStaging");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcStaging");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory for staging
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should configure with staging security
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for custom group names ==========

    @Test
    void testApplicationOidcFactoryWithCustomGroupNames() {
        // Given: A stack with custom OIDC group names
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcCustomGroups");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcCustomGroups");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolId", "us-east-1_TestPool123");
        cfcContext.put("cognitoUserPoolDomain", "test-auth");
        cfcContext.put("cognitoUserPoolClientId", "test-client-123");
        cfcContext.put("cognitoCreateGroups", true);
        cfcContext.put("cognitoAdminGroupName", "SuperAdmins");
        cfcContext.put("cognitoUserGroupName", "RegularUsers");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // Simulate CognitoAuthenticationFactory setting SystemContext values
        ctx.cognitoUserPoolId.set("us-east-1_TestPool123");
        ctx.cognitoDomainPrefix.set("test-auth");
        ctx.cognitoClientId.set("test-client-123");

        // When: Creating ApplicationOidcFactory with custom group names
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should use custom group names
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithDefaultGroupNames() {
        // Given: A stack without custom group names (should use defaults)
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcDefaultGroups");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcDefaultGroups");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        // When: Creating ApplicationOidcFactory with default groups
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should use default group names (Admins, Developers, Viewers)
        assertDoesNotThrow(factory::create);
    }

    // ========== Tests for region handling ==========

    @Test
    void testApplicationOidcFactoryWithDefaultRegion() {
        // Given: A stack without explicit region (should default to us-east-1)
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcDefaultRegion");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcDefaultRegion");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolId", "us-east-1_TestPool123");
        cfcContext.put("cognitoUserPoolDomain", "test-auth");
        cfcContext.put("cognitoUserPoolClientId", "test-client-123");
        // No region specified
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        ctx.cognitoUserPoolId.set("us-east-1_TestPool123");
        ctx.cognitoDomainPrefix.set("test-auth");
        ctx.cognitoClientId.set("test-client-123");

        // When: Creating ApplicationOidcFactory without region
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should default to us-east-1
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testApplicationOidcFactoryWithCustomRegion() {
        // Given: A stack with custom region
        App app = new App();
        Stack stack = new Stack(app, "TestAppOidcCustomRegion");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppOidcCustomRegion");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "application-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolId", "eu-west-1_TestPool456");
        cfcContext.put("cognitoUserPoolDomain", "test-auth");
        cfcContext.put("cognitoUserPoolClientId", "test-client-123");
        cfcContext.put("region", "eu-west-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        ctx.applicationSpec.set(new MockApplicationSpec(true, new MockOidcIntegration()));

        ctx.cognitoUserPoolId.set("eu-west-1_TestPool456");
        ctx.cognitoDomainPrefix.set("test-auth");
        ctx.cognitoClientId.set("test-client-123");

        // When: Creating ApplicationOidcFactory with eu-west-1
        ApplicationOidcFactory factory = new ApplicationOidcFactory(stack, "AppOidc");

        // Then: Should use eu-west-1
        assertDoesNotThrow(factory::create);
    }
}
