package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;

/**
 * OIDC Authentication Factory for ALB-based authentication with AWS IAM Identity Center.
 *
 * This factory handles OIDC authentication ONLY for AWS IAM Identity Center (formerly AWS SSO).
 * For Cognito User Pool authentication, use CognitoAuthenticationFactory instead.
 *
 * Provides:
 * - Infrastructure-level authentication before requests reach Jenkins
 * - Integration with AWS IAM Identity Center for enterprise SSO
 * - Compliance with security requirements (PCI-DSS Req 8, HIPAA §164.312(d), SOC 2 CC6.2, GDPR Art. 32)
 *
 * Configuration (MANUAL OIDC ENDPOINTS - Recommended):
 * - authMode: "alb-oidc" to enable this factory
 * - oidcIssuer: OIDC issuer URL from IAM Identity Center application
 * - oidcAuthorizationEndpoint: Authorization endpoint URL
 * - oidcTokenEndpoint: Token endpoint URL
 * - oidcUserInfoEndpoint: UserInfo endpoint URL
 * - oidcClientId: Client ID from IAM Identity Center application
 * - oidcClientSecretName: Secrets Manager secret name (default: jenkins/oidc/client-secret)
 *
 * Setup steps for IAM Identity Center:
 * 1. Go to AWS IAM Identity Center console
 * 2. Navigate to "Applications" > "Add application"
 * 3. Choose "I have an application I want to set up" > "OAuth 2.0" or "OIDC"
 * 4. Configure the application:
 *    - Redirect URLs: https://your-domain.com/oauth2/idpresponse
 *    - Grant types: Authorization code
 *    - Scopes: openid
 * 5. Copy the OIDC endpoints and client ID
 * 6. Add them to your deployment-context.json
 * 7. After deployment, update the client secret in AWS Secrets Manager
 *
 * Legacy Configuration (AUTO-CONSTRUCTED ENDPOINTS - Not recommended):
 * - authMode: "alb-oidc"
 * - ssoInstanceArn: AWS IAM Identity Center instance ARN
 *   Note: This auto-constructs endpoints but may not work with all IAM Identity Center configurations
 *
 * Note: For Cognito User Pool authentication, use CognitoAuthenticationFactory which provides
 * native ALB Cognito integration without requiring Secrets Manager.
 */
public class OidcAuthenticationFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(OidcAuthenticationFactory.class.getName());

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("stackName")
    private String stackName;

    // Manual OIDC configuration (recommended)
    @DeploymentContext("oidcIssuer")
    private String oidcIssuer;

    @DeploymentContext("oidcAuthorizationEndpoint")
    private String oidcAuthorizationEndpoint;

    @DeploymentContext("oidcTokenEndpoint")
    private String oidcTokenEndpoint;

    @DeploymentContext("oidcUserInfoEndpoint")
    private String oidcUserInfoEndpoint;

    @DeploymentContext("oidcClientId")
    private String oidcClientId;

    @DeploymentContext("oidcClientSecretName")
    private String oidcClientSecretName;

    // Legacy auto-construction (not recommended)
    @DeploymentContext("ssoInstanceArn")
    private String ssoInstanceArn;

    @DeploymentContext("region")
    private String region;

    public OidcAuthenticationFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only configure OIDC if authMode is "alb-oidc"
        if (!"alb-oidc".equals(authMode)) {
            LOG.info("ALB-OIDC authentication not enabled (authMode=" + authMode + ")");
            return;
        }

        // Priority 1: Check if manual OIDC endpoints are provided (for IAM Identity Center or external IdP)
        // Note: Cognito User Pool authentication is now handled by CognitoAuthenticationFactory directly
        boolean hasManualConfig = oidcIssuer != null && !oidcIssuer.isEmpty()
                && oidcAuthorizationEndpoint != null && !oidcAuthorizationEndpoint.isEmpty()
                && oidcTokenEndpoint != null && !oidcTokenEndpoint.isEmpty()
                && oidcUserInfoEndpoint != null && !oidcUserInfoEndpoint.isEmpty()
                && oidcClientId != null && !oidcClientId.isEmpty();

        if (hasManualConfig) {
            LOG.info("Configuring ALB-OIDC authentication with manually provided endpoints");
            LOG.info("OIDC Issuer: " + oidcIssuer);
            LOG.info("Client ID: " + oidcClientId);
            configureOidcAuthentication();
            return;
        }

        // Priority 3: Fall back to legacy auto-construction approach (not recommended)
        if (ssoInstanceArn != null && !ssoInstanceArn.isEmpty()) {
            LOG.warning("Using legacy auto-constructed OIDC endpoints from ssoInstanceArn");
            LOG.warning("This may not work with all IAM Identity Center configurations");
            LOG.warning("Recommended: Use Cognito (cognitoAutoProvision=true) or manually configure OIDC endpoints");
            LOG.info("SSO Instance ARN: " + ssoInstanceArn);
            configureOidcAuthentication();
            return;
        }

        // No OIDC configuration provided
        LOG.warning("ALB-OIDC enabled but no OIDC configuration provided");
        LOG.warning("Option 1 (Recommended): Use Cognito User Pool - set cognitoAutoProvision=true");
        LOG.warning("Option 2: Provide manual OIDC endpoints from IAM Identity Center:");
        LOG.warning("  - oidcIssuer, oidcAuthorizationEndpoint, oidcTokenEndpoint, oidcUserInfoEndpoint, oidcClientId");
        LOG.warning("Option 3 (Legacy): Provide ssoInstanceArn for auto-constructed endpoints (may not work)");
    }

    /**
     * Configure OIDC authentication on the HTTPS listener.
     * This adds a listener rule with OIDC authentication that forwards to the target group.
     * Uses manual endpoints if provided, otherwise auto-constructs from ssoInstanceArn.
     *
     * Creates a placeholder secret as a CDK resource if it doesn't exist, ensuring graceful
     * stack deletion. Users must update the secret value after deployment.
     */
    private void configureOidcAuthentication() {
        // Determine which endpoints to use
        String issuer;
        String authorizationEndpoint;
        String tokenEndpoint;
        String userInfoEndpoint;
        String clientId;
        String secretName;

        if (oidcIssuer != null && !oidcIssuer.isEmpty()) {
            // Use manually provided endpoints
            issuer = oidcIssuer;
            authorizationEndpoint = oidcAuthorizationEndpoint;
            tokenEndpoint = oidcTokenEndpoint;
            userInfoEndpoint = oidcUserInfoEndpoint;
            clientId = oidcClientId;
            secretName = (oidcClientSecretName != null && !oidcClientSecretName.isEmpty())
                    ? oidcClientSecretName
                    : stackName + "/jenkins/oidc/client-secret";
            LOG.info("Using manually configured OIDC endpoints");
        } else {
            // Auto-construct from SSO instance ARN (legacy)
            issuer = constructOidcIssuer(ssoInstanceArn);
            authorizationEndpoint = issuer + "/authorize";
            tokenEndpoint = issuer + "/token";
            userInfoEndpoint = issuer + "/userinfo";
            clientId = software.amazon.awscdk.Stack.of(this).getAccount();
            secretName = stackName + "/jenkins/oidc/client-secret";
            LOG.info("Using auto-constructed OIDC endpoints (legacy mode)");
        }

        LOG.info("OIDC Endpoints:");
        LOG.info("  Issuer: " + issuer);
        LOG.info("  Authorization: " + authorizationEndpoint);
        LOG.info("  Token: " + tokenEndpoint);
        LOG.info("  UserInfo: " + userInfoEndpoint);
        LOG.info("  Client ID: " + clientId);
        LOG.info("  Secret Name: " + secretName);

        // Only create secret if using manual OIDC endpoints (not IAM Identity Center)
        // IAM Identity Center path (ssoInstanceArn) has secret created by IdentityCenterFactory
        boolean isIdentityCenterPath = (ssoInstanceArn != null && !ssoInstanceArn.isEmpty());

        if (!isIdentityCenterPath) {
            // Create placeholder secret as a CDK resource for proper lifecycle management
            // This ensures the secret can be deleted gracefully when the stack is destroyed
            LOG.info("Creating placeholder secret in Secrets Manager: " + secretName);
            Secret.Builder.create(this, "OidcClientSecret")
                    .secretName(secretName)
                    .description("OIDC Client Secret for " + stackName + " (External IdP)")
                    .secretStringValue(SecretValue.unsafePlainText("PLACEHOLDER-UPDATE-WITH-ACTUAL-CLIENT-SECRET"))
                    .removalPolicy(RemovalPolicy.DESTROY)  // Allow deletion when stack is deleted
                    .build();

            LOG.warning("IMPORTANT: Placeholder secret created. Update with actual client secret:");
            LOG.warning("  aws secretsmanager put-secret-value --secret-id " + secretName + " --secret-string \"<your-client-secret>\"");
            LOG.info("Note: If secret already exists, deployment will fail. Delete the existing secret first or use a different secret name.");
        } else {
            LOG.info("Using secret created by IdentityCenterFactory: " + secretName);
            LOG.info("Note: Update the secret with your IAM Identity Center client secret after deployment");
        }

        // Wait for HTTPS listener to be available
        ctx.https.onSet(https -> {
            // Also need target group to forward to after authentication
            ctx.albTargetGroup.onSet(targetGroup -> {
                LOG.info("Adding OIDC authentication rule to HTTPS listener");

                // Create OIDC authentication action with forward to target group
                https.addAction("OidcAuth", AddApplicationActionProps.builder()
                    .priority(1)  // High priority to catch all requests before default action
                    .conditions(List.of(
                        ListenerCondition.pathPatterns(List.of("/*"))  // Match all paths
                    ))
                    .action(ListenerAction.authenticateOidc(
                        AuthenticateOidcOptions.builder()
                            .issuer(issuer)
                            .authorizationEndpoint(authorizationEndpoint)
                            .tokenEndpoint(tokenEndpoint)
                            .userInfoEndpoint(userInfoEndpoint)
                            .clientId(clientId)
                            .clientSecret(SecretValue.secretsManager(secretName))
                            .scope("openid")
                            .onUnauthenticatedRequest(UnauthenticatedAction.AUTHENTICATE)
                            .next(ListenerAction.forward(List.of(targetGroup)))  // Forward to target group after authentication
                            .build()
                    ))
                    .build());

                LOG.info("OIDC authentication configured successfully");
                LOG.info("  Target Group: " + targetGroup.getTargetGroupName());
                LOG.info("  Priority: 1 (authenticate then forward to target group)");
                LOG.info("  Authentication: All requests require OIDC authentication before reaching application");
            });
        });
    }


    /**
     * Construct OIDC issuer URL from AWS SSO instance ARN.
     * Format: arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx
     * OR just the instance ID: ssoins-xxxxxxxxxxxx
     * Returns: https://portal.sso.[region].amazonaws.com/saml/assertion/ssoins-xxxxxxxxxxxx
     *
     * Note: This is a placeholder URL structure. AWS SSO's actual OIDC endpoint may differ.
     * AWS SSO primarily uses SAML, not OIDC. For production use with AWS SSO:
     * 1. Register application in AWS SSO console
     * 2. Get actual OIDC endpoints from application configuration
     * 3. Store client secret in Secrets Manager
     *
     * Alternative: Use Amazon Cognito User Pool for simpler OIDC setup.
     */
    private String constructOidcIssuer(String instanceArnOrId) {
        // Extract instance ID from ARN or use as-is if already just the ID
        String instanceId;
        if (instanceArnOrId.contains("/")) {
            // Full ARN format: arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx
            instanceId = instanceArnOrId.substring(instanceArnOrId.lastIndexOf('/') + 1);
        } else {
            // Just the instance ID: ssoins-xxxxxxxxxxxx
            instanceId = instanceArnOrId;
        }

        // Construct issuer URL
        // Note: AWS SSO uses a different URL pattern depending on the region
        // This is a placeholder - actual OIDC URLs should come from SSO application registration
        return "https://portal.sso." + region + ".amazonaws.com/saml/assertion/" + instanceId;
    }

    /**
     * Extract AWS account ID from SSO instance ARN.
     * Format: arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx
     * The account ID is typically in the ARN structure, but IAM Identity Center ARNs
     * don't include account IDs in the standard format.
     *
     * @param instanceArnOrId Full ARN or just instance ID
     * @return The AWS account ID from CDK stack context
     */
    private String extractAccountIdFromArn(String instanceArnOrId) {
        // IAM Identity Center ARNs don't contain account IDs
        // Use the account from the CDK stack instead
        return software.amazon.awscdk.Stack.of(this).getAccount();
    }
}
