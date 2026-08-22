package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for CognitoAuthenticationFactory.
 *
 * Tests AWS Cognito User Pool creation and OIDC authentication including:
 * - Auto-provisioning User Pools
 * - OAuth 2.0 App Client configuration
 * - MFA support (TOTP, SMS, both)
 * - User groups and role-based access control
 * - ALB OIDC integration
 * - Compliance configurations
 */
class CognitoAuthenticationFactoryTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testCognitoAuthenticationFactoryCreation() {
        // Given: A stack with OIDC authentication
        App app = new App();
        Stack stack = new Stack(app, "TestCognito");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognito");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating CognitoAuthenticationFactory
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should create without errors
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithAutoProvision() {
        // Given: A stack with auto-provision enabled
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoAutoProv");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoAutoProv");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolName", "TestUserPool");
        cfcContext.put("cognitoDomainPrefix", "test-auth");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with auto-provision
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should create User Pool automatically
        assertDoesNotThrow(factory::create);
    }

    /** Managed Login branding (Cognito's own default styling, not the classic Hosted UI's plain
     *  look) — real regression this guards: the domain's own managedLoginVersion=2 has to be set
     *  via the L1 escape hatch (the L2 UserPoolDomain construct doesn't surface that property
     *  yet). Inspects the construct tree directly rather than {@code Template.fromStack} — full
     *  synthesis here trips SystemContext's own cross-factory validation (real VPC/ALB/Fargate
     *  required), the same reason OidcAuthenticationFactoryTest's equivalent tests are {@code
     *  @Disabled} rather than standing up that whole scaffold just for this. */
    @Test
    void testManagedLoginBrandingIsEnabledOnAutoProvisionedDomain() {
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoBranding");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoBranding");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-branding-auth");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");
        factory.create();

        var userPoolDomain = (software.amazon.awscdk.services.cognito.CfnUserPoolDomain)
            factory.getNode().findChild("UserPoolDomain").getNode().getDefaultChild();
        assertEquals(2, userPoolDomain.getManagedLoginVersion());

        var branding = (software.amazon.awscdk.services.cognito.CfnManagedLoginBranding)
            factory.getNode().findChild("ManagedLoginBranding");
        assertEquals(Boolean.TRUE, branding.getUseCognitoProvidedValues());
        assertNotNull(branding.getClientId());
        assertNotNull(branding.getUserPoolId());
    }

    @Test
    void testCognitoAuthenticationFactoryWithMfaTOTP() {
        // Given: A stack with TOTP MFA enabled
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoMfaTotp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoMfaTotp");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        cfcContext.put("cognitoMfaEnabled", true);
        cfcContext.put("cognitoMfaMethod", "totp");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with TOTP MFA
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should configure TOTP (authenticator app) MFA
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithMfaSMS() {
        // Given: A stack with SMS MFA enabled
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoMfaSms");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoMfaSms");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        cfcContext.put("cognitoMfaEnabled", true);
        cfcContext.put("cognitoMfaMethod", "sms");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with SMS MFA
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should configure SMS MFA
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithMfaBoth() {
        // Given: A stack with both TOTP and SMS MFA
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoMfaBoth");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoMfaBoth");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        cfcContext.put("cognitoMfaEnabled", true);
        cfcContext.put("cognitoMfaMethod", "both");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with both MFA methods
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should allow users to choose TOTP or SMS
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithUserGroups() {
        // Given: A stack with user groups enabled
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoGroups");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoGroups");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        cfcContext.put("cognitoCreateGroups", true);
        cfcContext.put("cognitoAdminGroupName", "Administrators");
        cfcContext.put("cognitoUserGroupName", "Users");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with user groups
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should create admin and user groups
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithExistingUserPool() {
        // Given: A stack referencing existing User Pool
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoExisting");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoExisting");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", false);
        cfcContext.put("cognitoUserPoolId", "us-east-1_TestPool123");
        cfcContext.put("cognitoUserPoolClientId", "test-client-id-123");
        cfcContext.put("cognitoDomainPrefix", "test-existing-pool");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with existing pool
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should use existing User Pool
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithDevProfile() {
        // Given: A stack with DEV security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestCognitoDev", SecurityProfile.DEV);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoDev");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating factory for DEV
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should create with relaxed security settings
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithStagingProfile() {
        // Given: A stack with STAGING security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestCognitoStaging", SecurityProfile.STAGING);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoStaging");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Creating factory for STAGING
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should create with production-like security
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithAllSecurityProfiles() {
        // Given: Each security profile with OIDC
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = new Stack(app, "TestCognitoProfile" + counter++);

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "TestCognitoProfile" + counter);
            cfcContext.put("securityProfile", profile.name());
            cfcContext.put("domain", "example.com");
            cfcContext.put("enableSsl", true);
            cfcContext.put("fqdn", "app.example.com");
            cfcContext.put("authMode", "alb-oidc");
            cfcContext.put("cognitoAutoProvision", true);
            cfcContext.put("cognitoDomainPrefix", "test-cognito-auth");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

            // When: Creating factory for each profile
            assertDoesNotThrow(factory::create,
                "CognitoAuthenticationFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testCognitoAuthenticationFactoryWithCustomDomainPrefix() {
        // Given: A stack with custom Cognito domain prefix
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoDomainPrefix");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoDomainPrefix");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "my-custom-auth-domain");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with custom domain prefix
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should use custom domain prefix
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithMinimalConfiguration() {
        // Given: A stack with minimal OIDC configuration
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoMinimal");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoMinimal");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating factory with minimal config
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should handle minimal configuration
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCognitoAuthenticationFactoryWithMaximalConfiguration() {
        // Given: A stack with all Cognito features enabled
        App app = new App();
        Stack stack = new Stack(app, "TestCognitoMaximal");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCognitoMaximal");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "app.example.com");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoUserPoolName", "MaximalUserPool");
        cfcContext.put("cognitoDomainPrefix", "maximal-auth");
        cfcContext.put("cognitoMfaEnabled", true);
        cfcContext.put("cognitoMfaMethod", "both");
        cfcContext.put("cognitoCreateGroups", true);
        cfcContext.put("cognitoAdminGroupName", "Administrators");
        cfcContext.put("cognitoUserGroupName", "StandardUsers");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating factory with maximal configuration
        CognitoAuthenticationFactory factory = new CognitoAuthenticationFactory(stack, "Cognito");

        // Then: Should handle all features
        assertDoesNotThrow(factory::create);
    }
}
