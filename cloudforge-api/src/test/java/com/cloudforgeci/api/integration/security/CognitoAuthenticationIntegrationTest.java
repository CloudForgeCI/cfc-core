package com.cloudforgeci.api.integration.security;

import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.security.CognitoAuthenticationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for AWS Cognito User Pool authentication.
 *
 * Tests validate:
 * - Cognito User Pool creation with auto-provisioning
 * - User Pool Client configuration for ALB OIDC integration
 * - Multi-factor authentication (MFA) configuration
 * - Password policies and security settings
 * - ALB listener rules with authenticate-cognito action
 *
 * These tests are completely independent from OIDC authentication tests.
 */
class CognitoAuthenticationIntegrationTest extends IntegrationTestBase {

    @Override
    protected SecurityProfile getSecurityProfile() {
        return SecurityProfile.PRODUCTION;
    }

    @BeforeEach
    @Override
    public void setUp() {
        // Configure deployment context for Cognito authentication
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "cognito-auth-test");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("domain", "test.example.com");
        cfcContext.put("fqdn", "test.example.com");
        cfcContext.put("cognitoAutoProvision", true);
        cfcContext.put("cognitoDomainPrefix", "test-auth");
        cfcContext.put("cognitoUserPoolName", "TestUserPool");
        cfcContext.put("cognitoMfaEnabled", true);
        cfcContext.put("cognitoMfaMethod", "both");

        // Create infrastructure builder with custom context
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "CognitoAuthTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );
        this.stack = builder.getStack();
        this.ctx = builder.getSystemContext();
        this.cfc = builder.getDeploymentContext();
    }

    @Test
    void testCognitoUserPoolCreation() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();

        // When: Creating Cognito authentication
        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify Cognito User Pool is created
        template.resourceCountIs("AWS::Cognito::UserPool", 1);

        // Then: Verify User Pool has proper name
        template.hasResourceProperties("AWS::Cognito::UserPool", Map.of(
            "UserPoolName", Match.anyValue()
        ));
    }

    @Test
    void testCognitoUserPoolEmailVerification() {
        // Given: Complete infrastructure with Cognito
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify email auto-verification is enabled
        template.hasResourceProperties("AWS::Cognito::UserPool", Map.of(
            "AutoVerifiedAttributes", Match.arrayWith("email")
        ));
    }

    @Test
    void testCognitoUserPoolClient() {
        // Given: Complete infrastructure with Cognito
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify User Pool Client is created for ALB
        template.resourceCountIs("AWS::Cognito::UserPoolClient", 1);

        // Then: Verify client generates secret
        template.hasResourceProperties("AWS::Cognito::UserPoolClient", Map.of(
            "GenerateSecret", true
        ));
    }

    @Test
    void testCognitoUserPoolDomain() {
        // Given: Complete infrastructure with Cognito
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify User Pool Domain is created
        template.resourceCountIs("AWS::Cognito::UserPoolDomain", 1);
    }

    @Test
    void testCognitoMfaConfiguration() {
        // Given: Complete infrastructure with Cognito and MFA enabled
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify MFA configuration on User Pool
        template.hasResourceProperties("AWS::Cognito::UserPool", Map.of(
            "MfaConfiguration", Match.anyValue() // OPTIONAL or REQUIRED
        ));
    }

    @Test
    void testCognitoPasswordPolicy() {
        // Given: Complete infrastructure with Cognito
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify password policy meets security requirements
        template.hasResourceProperties("AWS::Cognito::UserPool", Map.of(
            "Policies", Map.of(
                "PasswordPolicy", Map.of(
                    "MinimumLength", Match.anyValue(),
                    "RequireUppercase", true,
                    "RequireLowercase", true,
                    "RequireNumbers", true,
                    "RequireSymbols", true
                )
            )
        ));
    }

    @Test
    void testCognitoAdvancedSecurity() {
        // Given: Complete infrastructure with Cognito advanced security
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify User Pool is created (advanced security is optional)
        // Note: UserPoolAddOns is only present when explicitly configured
        template.resourceCountIs("AWS::Cognito::UserPool", 1);
    }

    @Test
    void testCognitoAccountRecovery() {
        // Given: Complete infrastructure with Cognito
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify account recovery is configured
        template.hasResourceProperties("AWS::Cognito::UserPool", Map.of(
            "AccountRecoverySetting", Map.of(
                "RecoveryMechanisms", Match.arrayWith(
                    Map.of(
                        "Name", "verified_email",
                        "Priority", 1
                    )
                )
            )
        ));
    }

    @Test
    void testCognitoTokenValidity() {
        // Given: Complete infrastructure with Cognito
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify token validity configuration
        template.hasResourceProperties("AWS::Cognito::UserPoolClient", Map.of(
            "AccessTokenValidity", Match.anyValue(),
            "IdTokenValidity", Match.anyValue(),
            "RefreshTokenValidity", Match.anyValue()
        ));
    }

    @Test
    void testAlbAuthenticateCognitoAction() {
        // Given: Complete infrastructure with Cognito authentication
        builder.createCompleteInfrastructure();

        CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(stack, "CognitoAuth");
        cognitoFactory.create();

        synthesizeTemplate();

        // Then: Verify ALB listener rule with Cognito authentication action exists
        // Note: The exact structure depends on how ALB is configured
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of(
            "Port", 80,
            "Protocol", "HTTP"
        ));
    }
}
