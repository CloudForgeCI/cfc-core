package com.cloudforgeci.api.integration.security;

import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.security.OidcAuthenticationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for external OIDC authentication (Okta, Auth0, IAM Identity Center).
 *
 * Tests validate:
 * - OIDC authentication configuration with external identity providers
 * - ALB authenticate-oidc action configuration
 * - Session management and token handling
 * - Integration with IAM Identity Center
 * - Support for multiple OIDC providers (Okta, Auth0, etc.)
 *
 * These tests are completely independent from Cognito authentication tests.
 *
 * NOTE: These tests are currently disabled because OIDC authentication is not yet configured.
 * To enable: Set up OIDC provider and configure deployment context with OIDC endpoints.
 */
@Disabled("OIDC authentication not yet configured - use CognitoAuthenticationIntegrationTest for now")
class OidcAuthenticationIntegrationTest extends IntegrationTestBase {

    @Override
    protected SecurityProfile getSecurityProfile() {
        return SecurityProfile.PRODUCTION;
    }

    @BeforeEach
    @Override
    public void setUp() {
        // Configure deployment context for OIDC authentication
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "oidc-auth-test");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", true);
        cfcContext.put("domain", "test.example.com");
        cfcContext.put("fqdn", "test.example.com");

        // OIDC provider configuration (would come from real provider)
        cfcContext.put("oidcIssuer", "https://example.okta.com");
        cfcContext.put("oidcAuthorizationEndpoint", "https://example.okta.com/oauth2/v1/authorize");
        cfcContext.put("oidcTokenEndpoint", "https://example.okta.com/oauth2/v1/token");
        cfcContext.put("oidcUserInfoEndpoint", "https://example.okta.com/oauth2/v1/userinfo");
        cfcContext.put("oidcClientId", "test-client-id");
        cfcContext.put("oidcClientSecretArn", "arn:aws:secretsmanager:us-east-1:123456789012:secret:oidc-client-secret");

        // Create infrastructure builder with custom context
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "OidcAuthTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );
        this.stack = builder.getStack();
        this.ctx = builder.getSystemContext();
        this.cfc = builder.getDeploymentContext();
    }

    @Test
    void testOidcAuthenticationConfiguration() {
        // Given: Complete infrastructure with OIDC
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify OIDC authentication action on ALB listener
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Actions", Match.arrayWith(
                Map.of(
                    "Type", "authenticate-oidc",
                    "AuthenticateOidcConfig", Map.of(
                        "Issuer", Match.anyValue(),
                        "AuthorizationEndpoint", Match.anyValue(),
                        "TokenEndpoint", Match.anyValue(),
                        "UserInfoEndpoint", Match.anyValue(),
                        "ClientId", Match.anyValue()
                    )
                )
            )
        ));
    }

    @Test
    void testOidcSecretsManagerIntegration() {
        // Given: Complete infrastructure with OIDC
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify Secrets Manager secret for client credentials
        template.resourceCountIs("AWS::SecretsManager::Secret", 1);
    }

    @Test
    void testOidcAlbListenerRule() {
        // Given: Complete infrastructure with OIDC
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify ALB listener rule with OIDC authentication
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Priority", Match.anyValue(),
            "Conditions", Match.arrayWith(
                Map.of(
                    "Field", "path-pattern",
                    "Values", Match.anyValue()
                )
            )
        ));
    }

    @Test
    void testOidcSessionManagement() {
        // Given: Complete infrastructure with OIDC
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify session cookie configuration
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Actions", Match.arrayWith(
                Map.of(
                    "Type", "authenticate-oidc",
                    "AuthenticateOidcConfig", Map.of(
                        "SessionCookieName", Match.anyValue(),
                        "SessionTimeout", Match.anyValue()
                    )
                )
            )
        ));
    }

    @Test
    void testOidcScopeConfiguration() {
        // Given: Complete infrastructure with OIDC
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify OIDC scopes are configured
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Actions", Match.arrayWith(
                Map.of(
                    "Type", "authenticate-oidc",
                    "AuthenticateOidcConfig", Map.of(
                        "Scope", "openid profile email"
                    )
                )
            )
        ));
    }

    @Test
    void testOidcMultipleProviderSupport() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify OIDC configuration supports various providers
        // (Okta, Auth0, Google, etc.) via generic OIDC endpoints
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Actions", Match.arrayWith(
                Map.of(
                    "Type", "authenticate-oidc",
                    "AuthenticateOidcConfig", Map.of(
                        "Issuer", Match.anyValue()
                    )
                )
            )
        ));
    }

    @Test
    void testOidcIamIdentityCenterIntegration() {
        // Given: Complete infrastructure with IAM Identity Center
        builder.createCompleteInfrastructure();

        OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(stack, "OidcAuth");
        oidcFactory.create();

        synthesizeTemplate();

        // Then: Verify IAM Identity Center can be used as OIDC provider
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Actions", Match.arrayWith(
                Map.of(
                    "Type", "authenticate-oidc"
                )
            )
        ));
    }
}
