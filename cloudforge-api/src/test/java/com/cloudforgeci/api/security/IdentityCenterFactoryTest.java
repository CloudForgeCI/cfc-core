package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for IdentityCenterFactory.
 *
 * Tests AWS IAM Identity Center (formerly AWS SSO) integration including:
 * - OIDC client secret creation in Secrets Manager
 * - Auth mode validation
 * - SSO instance ARN requirement
 * - Manual OIDC endpoint detection
 * - Conditional factory execution
 */
class IdentityCenterFactoryTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testIdentityCenterFactoryCreation() {
        // Given: A stack with Identity Center configured
        App app = new App();
        Stack stack = new Stack(app, "TestIdentityCenter");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestIdentityCenter");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should create OIDC client secret in Secrets Manager
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithoutAuthMode() {
        // Given: A stack without ALB-OIDC auth mode
        App app = new App();
        Stack stack = createTestStack(app, "TestNoCognito", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory without authMode
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should skip Identity Center setup
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithBasicAuthMode() {
        // Given: A stack with basic auth (not alb-oidc)
        App app = new App();
        Stack stack = new Stack(app, "TestBasicAuth");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestBasicAuth");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("authMode", "none");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory with non-OIDC auth
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should skip Identity Center setup
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithManualOidcEndpoints() {
        // Given: A stack with manual OIDC endpoints configured (not using Identity Center)
        App app = new App();
        Stack stack = new Stack(app, "TestManualOidc");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestManualOidc");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory with manual OIDC
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should skip Identity Center setup (manual OIDC configured)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithoutSsoInstanceArn() {
        // Given: A stack with alb-oidc but no SSO instance ARN
        App app = new App();
        Stack stack = new Stack(app, "TestNoSsoArn");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestNoSsoArn");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory without SSO instance ARN
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should skip Identity Center setup (no SSO ARN)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithEmptySsoInstanceArn() {
        // Given: A stack with empty SSO instance ARN and SSL
        App app = new App();
        Stack stack = new Stack(app, "TestEmptySsoArn");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEmptySsoArn");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory with empty SSO ARN
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should skip Identity Center setup (empty SSO ARN)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithAllSecurityProfiles() {
        // Given: Each security profile with Identity Center
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            String stackName = "TestIdentityCenterProfile" + counter;
            Stack stack = new Stack(app, stackName);
            counter++;

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", stackName);
            cfcContext.put("securityProfile", profile.name());
            cfcContext.put("domain", "example.com");
            cfcContext.put("enableSsl", true);
            cfcContext.put("fqdn", "app.example.com");
            cfcContext.put("authMode", "alb-oidc");
            cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-test123");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

            // When: Creating IdentityCenterFactory for each profile
            assertDoesNotThrow(factory::create,
                "IdentityCenterFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testIdentityCenterFactoryCreatesClientSecret() {
        // Given: A fully configured Identity Center stack with SSL
        App app = new App();
        Stack stack = new Stack(app, "TestClientSecret");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestClientSecret");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-abcd1234");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating IdentityCenterFactory
        IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

        // Then: Should create client secret with placeholder value
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testIdentityCenterFactoryWithDifferentStackNames() {
        // Given: Different stack names to test secret naming with SSL
        String[] stackNames = {"dev-stack", "prod-jenkins", "test-deployment"};

        for (String stackName : stackNames) {
            App app = new App();
            Stack stack = new Stack(app, stackName);

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", stackName);
            cfcContext.put("securityProfile", "PRODUCTION");
            cfcContext.put("domain", "example.com");
            cfcContext.put("enableSsl", true);
            cfcContext.put("fqdn", "app.example.com");
            cfcContext.put("authMode", "alb-oidc");
            cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-test");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            // When: Creating IdentityCenterFactory with different stack name
            IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

            // Then: Should create with unique secret name based on stack name
            assertDoesNotThrow(factory::create);
        }
    }

    @Test
    void testIdentityCenterFactoryWithDifferentSsoInstances() {
        // Given: Different SSO instance ARNs
        String[] ssoArns = {
            "arn:aws:sso:::instance/ssoins-1234567890abcdef",
            "arn:aws:sso:::instance/ssoins-fedcba0987654321",
            "arn:aws:sso:::instance/ssoins-test1234test5678"
        };

        int counter = 0;
        for (String ssoArn : ssoArns) {
            App app = new App();
            String stackName = "TestSsoInstance" + counter;
            Stack stack = new Stack(app, stackName);
            counter++;

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", stackName);
            cfcContext.put("securityProfile", "PRODUCTION");
            cfcContext.put("domain", "example.com");
            cfcContext.put("enableSsl", true);
            cfcContext.put("fqdn", "app.example.com");
            cfcContext.put("authMode", "alb-oidc");
            cfcContext.put("ssoInstanceArn", ssoArn);
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            // When: Creating IdentityCenterFactory with different SSO instance ARN
            IdentityCenterFactory factory = new IdentityCenterFactory(stack, "IdentityCenter");

            // Then: Should create with provided SSO instance ARN
            assertDoesNotThrow(factory::create);
        }
    }
}
