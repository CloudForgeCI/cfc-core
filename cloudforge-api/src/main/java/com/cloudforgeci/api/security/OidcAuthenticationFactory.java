package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.enums.AuthMode;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;

/**
 * OIDC Authentication Factory for ALB-based authentication with any OIDC provider.
 *
 * This factory handles OIDC authentication for manual endpoint configuration (IAM Identity Center,
 * Okta, Auth0, or any external IdP). For Cognito User Pool authentication, use
 * CognitoAuthenticationFactory instead.
 *
 * Provides:
 * - Infrastructure-level authentication before requests reach the application
 * - Integration with any OIDC-compliant identity provider via manual endpoint configuration
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
    private AuthMode authMode;

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

    // ========== Path-Based Authentication ==========
    // These settings control which paths require authentication at the ALB level

    @DeploymentContext("protectedPaths")
    private java.util.List<String> protectedPaths;

    @DeploymentContext("additionalProtectedPaths")
    private java.util.List<String> additionalProtectedPaths;

    @DeploymentContext("publicPaths")
    private java.util.List<String> publicPaths;

    @com.cloudforge.core.annotation.SystemContext("applicationSpec")
    private com.cloudforge.core.interfaces.ApplicationSpec applicationSpec;

    public OidcAuthenticationFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only configure OIDC if authMode is ALB_OIDC
        if (authMode != AuthMode.ALB_OIDC) {
            LOG.info("ALB-OIDC authentication not enabled (authMode = " + authMode + ")");
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
            LOG.info("OIDC Issuer: [REDACTED]");
            LOG.info("Client ID: [REDACTED]");
            configureOidcAuthentication();
            return;
        }

        // Fallback: legacy auto-construction approach from ssoInstanceArn (not recommended, may not work)
        if (ssoInstanceArn != null && !ssoInstanceArn.isEmpty()) {
            LOG.warning("Using legacy auto-constructed OIDC endpoints from ssoInstanceArn");
            LOG.warning("This may not work with all IAM Identity Center configurations");
            LOG.warning("Recommended: Use Cognito (cognitoAutoProvision = true) or manually configure OIDC endpoints");
            LOG.info("SSO Instance ARN: " + ssoInstanceArn);
            configureOidcAuthentication();
            return;
        }

        // No OIDC configuration provided
        LOG.warning("ALB-OIDC enabled but no OIDC configuration provided");
        LOG.warning("Option 1 (Recommended): Use Cognito User Pool - set cognitoAutoProvision = true");
        LOG.warning("Option 2: Provide manual OIDC endpoints from IAM Identity Center:");
        LOG.warning("  - oidcIssuer, oidcAuthorizationEndpoint, oidcTokenEndpoint, oidcUserInfoEndpoint, oidcClientId");
        LOG.warning("Option 3 (Legacy): Provide ssoInstanceArn for auto-constructed endpoints (may not work)");
    }

    /**
     * Configure OIDC authentication on the HTTPS listener.
     * This adds a listener rule with OIDC authentication that forwards to the target group.
     * Uses manual endpoints if provided, otherwise auto-constructs from ssoInstanceArn.
     *
     * <p>Path-based authentication allows protecting only specific paths while
     * leaving other paths public. This is configured via:</p>
     * <ul>
     *   <li>ApplicationSpec.protectedPaths() - Application default protected paths</li>
     *   <li>DeploymentContext.protectedPaths - Override/replace application defaults</li>
     *   <li>DeploymentContext.additionalProtectedPaths - Add to application defaults</li>
     *   <li>DeploymentContext.publicPaths - Explicitly exclude paths from protection</li>
     * </ul>
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
            clientId = Stack.of(this).getAccount();
            secretName = stackName + "/jenkins/oidc/client-secret";
            LOG.info("Using auto-constructed OIDC endpoints (legacy mode)");
        }

        LOG.info("OIDC Endpoints:");
        LOG.info("  Issuer: [REDACTED]");
        LOG.info("  Authorization: [REDACTED]");
        LOG.info("  Token: [REDACTED]");
        LOG.info("  UserInfo: [REDACTED]");
        LOG.info("  Client ID: [REDACTED]");
        LOG.info("  Secret Name: [REDACTED]");

        // Only create secret if using manual OIDC endpoints (not IAM Identity Center)
        // IAM Identity Center path (ssoInstanceArn) has secret created by IdentityCenterFactory
        boolean isIdentityCenterPath = (ssoInstanceArn != null && !ssoInstanceArn.isEmpty());

        if (!isIdentityCenterPath) {
            // Create placeholder secret as a CDK resource for proper lifecycle management
            // This ensures the secret can be deleted gracefully when the stack is destroyed
            LOG.info("Creating placeholder secret in Secrets Manager: [REDACTED]");
            Secret.Builder.create(this, "OidcClientSecret")
                    .secretName(secretName)
                    .description("OIDC Client Secret for " + stackName + " (External IdP)")
                    .secretStringValue(SecretValue.unsafePlainText("PLACEHOLDER-UPDATE-WITH-ACTUAL-CLIENT-SECRET"))
                    .removalPolicy(RemovalPolicy.DESTROY)  // Allow deletion when stack is deleted
                    .build();

            LOG.warning("IMPORTANT: Placeholder secret created. Update with actual client secret:");
            LOG.warning("  aws secretsmanager put-secret-value --secret-id [SECRET_NAME] --secret-string \"<your-client-secret>\"");
            LOG.info("Note: If secret already exists, deployment will fail. Delete the existing secret first or use a different secret name.");
        } else {
            LOG.info("Using secret created by IdentityCenterFactory: [REDACTED]");
            LOG.info("Note: Update the secret with your IAM Identity Center client secret after deployment");
        }

        // Store OIDC config for use in listener rules
        final String finalIssuer = issuer;
        final String finalAuthEndpoint = authorizationEndpoint;
        final String finalTokenEndpoint = tokenEndpoint;
        final String finalUserInfoEndpoint = userInfoEndpoint;
        final String finalClientId = clientId;
        final String finalSecretName = secretName;

        // Wait for HTTPS listener to be available
        ctx.https.onSet(https -> {
            // Also need target group to forward to after authentication
            ctx.albTargetGroup.onSet(targetGroup -> {
                LOG.info("Adding OIDC authentication rule to HTTPS listener");

                // Calculate effective paths
                List<String> effectiveProtectedPaths = calculateEffectiveProtectedPaths();
                List<String> effectivePublicPaths = calculateEffectivePublicPaths();

                if (effectiveProtectedPaths.isEmpty() && effectivePublicPaths.isEmpty()) {
                    // No path configuration — authenticate all paths (most secure)
                    LOG.info("No path configuration — authenticating all paths");
                    configureFullOidcAuthenticationRule(https, targetGroup, finalIssuer, finalAuthEndpoint,
                            finalTokenEndpoint, finalUserInfoEndpoint, finalClientId, finalSecretName);
                } else {
                    // Path-based rules: explicit public paths bypass auth; everything else requires auth
                    LOG.info("Path-based authentication: " + effectiveProtectedPaths.size()
                            + " explicitly protected, " + effectivePublicPaths.size()
                            + " explicitly public (all other paths require auth)");
                    configurePathBasedOidcAuthenticationRules(https, targetGroup, finalIssuer, finalAuthEndpoint,
                            finalTokenEndpoint, finalUserInfoEndpoint, finalClientId, finalSecretName,
                            effectiveProtectedPaths, effectivePublicPaths);
                }

                LOG.info("OIDC authentication configured successfully");
                LOG.info("  Target Group: " + targetGroup.getTargetGroupName());
            });
        });
    }

    /**
     * Calculate the effective list of protected paths based on ApplicationSpec and DeploymentContext.
     *
     * <p>Priority:</p>
     * <ol>
     *   <li>If DeploymentContext.protectedPaths is set, it replaces ApplicationSpec defaults</li>
     *   <li>Otherwise, use ApplicationSpec.protectedPaths()</li>
     *   <li>Add any DeploymentContext.additionalProtectedPaths</li>
     *   <li>Remove any DeploymentContext.publicPaths from the result</li>
     * </ol>
     *
     * @return List of path patterns requiring authentication (empty = protect everything)
     */
    private List<String> calculateEffectiveProtectedPaths() {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();

        // Step 1: Start with base protected paths
        if (protectedPaths != null && !protectedPaths.isEmpty()) {
            // DeploymentContext overrides ApplicationSpec
            paths.addAll(protectedPaths);
            LOG.info("Using DeploymentContext.protectedPaths: " + protectedPaths);
        } else if (applicationSpec != null && !applicationSpec.protectedPaths().isEmpty()) {
            // Use ApplicationSpec defaults
            paths.addAll(applicationSpec.protectedPaths());
            LOG.info("Using ApplicationSpec.protectedPaths(): " + applicationSpec.protectedPaths());
        }

        // Step 2: Add additional protected paths from DeploymentContext
        if (additionalProtectedPaths != null && !additionalProtectedPaths.isEmpty()) {
            paths.addAll(additionalProtectedPaths);
            LOG.info("Adding DeploymentContext.additionalProtectedPaths: " + additionalProtectedPaths);
        }

        // Step 3: Remove public paths from DeploymentContext
        if (publicPaths != null && !publicPaths.isEmpty()) {
            paths.removeAll(publicPaths);
            LOG.info("Removing DeploymentContext.publicPaths: " + publicPaths);
        }

        // Step 4: Remove public paths from ApplicationSpec
        if (applicationSpec != null && !applicationSpec.publicPaths().isEmpty()) {
            paths.removeAll(applicationSpec.publicPaths());
            LOG.info("Removing ApplicationSpec.publicPaths(): " + applicationSpec.publicPaths());
        }

        List<String> result = new java.util.ArrayList<>(paths);
        LOG.info("Effective protected paths: " + (result.isEmpty() ? "[ALL PATHS]" : result));
        return result;
    }

    /**
     * Compute the explicit public paths that must bypass authentication.
     * These paths get dedicated forward-only rules at the highest priority so they
     * can never be shadowed by a protected-path rule (e.g. a wildcard in protectedPaths).
     *
     * Sources (union):
     *   - DeploymentContext.publicPaths
     *   - ApplicationSpec.publicPaths()
     */
    private List<String> calculateEffectivePublicPaths() {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        if (publicPaths != null && !publicPaths.isEmpty()) {
            paths.addAll(publicPaths);
            LOG.info("Public paths from DeploymentContext: " + publicPaths);
        }
        if (applicationSpec != null && !applicationSpec.publicPaths().isEmpty()) {
            paths.addAll(applicationSpec.publicPaths());
            LOG.info("Public paths from ApplicationSpec: " + applicationSpec.publicPaths());
        }
        List<String> result = new java.util.ArrayList<>(paths);
        LOG.info("Effective public paths (no auth): " + (result.isEmpty() ? "[none]" : result));
        return result;
    }

    /**
     * Configure OIDC authentication rule that protects all paths (original behavior).
     */
    private void configureFullOidcAuthenticationRule(
            ApplicationListener https,
            IApplicationTargetGroup targetGroup,
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String userInfoEndpoint,
            String clientId,
            String secretName) {

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
                    .next(ListenerAction.forward(List.of(targetGroup)))
                    .build()
            ))
            .build());

        LOG.info("  Priority: 1 (authenticate then forward to target group)");
        LOG.info("  Authentication: All requests require OIDC authentication before reaching application");
    }

    /**
     * Configure path-based OIDC authentication rules.
     *
     * <p>Rule ordering (highest → lowest ALB priority):</p>
     * <ol>
     *   <li>Explicit public paths — forward without auth (health checks, webhooks, etc.)</li>
     *   <li>Explicitly protected paths — authenticate then forward</li>
     *   <li>Catch-all {@code /*} — authenticate then forward (secure default; no unauthenticated fallthrough)</li>
     * </ol>
     *
     * <p>Public paths are placed at the highest priority so they cannot be shadowed by a
     * wildcard pattern in {@code protectedPaths} (e.g. {@code /*}).</p>
     */
    private void configurePathBasedOidcAuthenticationRules(
            ApplicationListener https,
            IApplicationTargetGroup targetGroup,
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String userInfoEndpoint,
            String clientId,
            String secretName,
            List<String> protectedPathPatterns,
            List<String> publicPathPatterns) {

        // ALB rules support up to 5 path-pattern values per condition
        int batchSize = 5;
        int priority = 1;

        // Step 1: Explicit public paths — forward without auth (highest priority)
        for (int i = 0; i < publicPathPatterns.size(); i += batchSize) {
            List<String> batch = publicPathPatterns.subList(i, Math.min(i + batchSize, publicPathPatterns.size()));
            String ruleName = "PublicPath" + (i == 0 ? "" : "-" + (i / batchSize + 1));
            https.addAction(ruleName, AddApplicationActionProps.builder()
                .priority(priority)
                .conditions(List.of(ListenerCondition.pathPatterns(batch)))
                .action(ListenerAction.forward(List.of(targetGroup)))
                .build());
            LOG.info("  Rule '" + ruleName + "' (priority " + priority + "): Forward without auth for " + batch);
            priority++;
        }

        // Step 2: Explicitly protected paths — authenticate then forward
        for (int i = 0; i < protectedPathPatterns.size(); i += batchSize) {
            List<String> batch = protectedPathPatterns.subList(i, Math.min(i + batchSize, protectedPathPatterns.size()));
            String ruleName = "OidcAuth" + (i == 0 ? "" : "-" + (i / batchSize + 1));
            https.addAction(ruleName, AddApplicationActionProps.builder()
                .priority(priority)
                .conditions(List.of(ListenerCondition.pathPatterns(batch)))
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
                        .next(ListenerAction.forward(List.of(targetGroup)))
                        .build()
                ))
                .build());
            LOG.info("  Rule '" + ruleName + "' (priority " + priority + "): Authenticate for paths " + batch);
            priority++;
        }

        // Step 3: Catch-all — authenticate then forward (secure default; replaces old forward-only catch-all)
        // Any path not explicitly listed in publicPaths or protectedPaths requires authentication.
        https.addAction("AuthCatchAll", AddApplicationActionProps.builder()
            .priority(priority)
            .conditions(List.of(ListenerCondition.pathPatterns(List.of("/*"))))
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
                    .next(ListenerAction.forward(List.of(targetGroup)))
                    .build()
            ))
            .build());

        LOG.info("  Rule 'AuthCatchAll' (priority " + priority + "): Authenticate all remaining paths (secure default)");
        LOG.info("  Only paths listed in publicPaths bypass authentication; everything else requires OIDC");
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
     * @return The AWS account ID from CDK stack context
     */
    @SuppressWarnings("unused")
    private String extractAccountIdFromArn() {
        // IAM Identity Center ARNs don't contain account IDs
        // Use the account from the CDK stack instead
        return Stack.of(this).getAccount();
    }
}
