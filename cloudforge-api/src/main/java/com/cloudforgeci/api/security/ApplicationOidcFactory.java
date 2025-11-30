package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;

/**
 * Application-level OIDC Authentication Factory.
 *
 * <p>This factory configures OIDC authentication WITHIN the application itself
 * (e.g., Jenkins OIDC plugin, GitLab OmniAuth, Grafana OAuth), as opposed to
 * ALB-level authentication which handles auth before requests reach the application.</p>
 *
 * <h2>Authentication Modes Comparison:</h2>
 * <ul>
 *   <li><strong>alb-oidc</strong>: Authentication at ALB - users auth before reaching app
 *       <ul>
 *           <li>+ Works with any application</li>
 *           <li>+ No application configuration needed</li>
 *           <li>- Requires HTTPS</li>
 *           <li>- All requests authenticated (can't have public pages)</li>
 *       </ul>
 *   </li>
 *   <li><strong>application-oidc</strong>: Authentication within application - app handles auth
 *       <ul>
 *           <li>+ Application controls auth (can have public pages)</li>
 *           <li>+ Works over HTTP or HTTPS</li>
 *           <li>+ Application-specific features (role mapping, etc.)</li>
 *           <li>- Requires application OIDC support</li>
 *           <li>- Application-specific configuration</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Supported Applications:</h2>
 * <p>Only applications that implement {@link ApplicationSpec#supportsOidcIntegration()}
 * and provide an {@link OidcIntegration} implementation can use application-level OIDC:</p>
 * <ul>
 *   <li>Jenkins (via oic-auth plugin)</li>
 *   <li>GitLab (via built-in OmniAuth)</li>
 *   <li>Grafana (via built-in generic_oauth)</li>
 *   <li>More applications coming soon</li>
 * </ul>
 *
 * <h2>Configuration:</h2>
 * <p><strong>Option 1: Amazon Cognito (Recommended)</strong></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "cognitoAutoProvision": true,
 *   "cognitoMfaEnabled": true
 * }
 * </pre>
 *
 * <p><strong>Option 2: IAM Identity Center</strong></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "oidcIssuer": "https://portal.sso.us-east-1.amazonaws.com/saml/assertion/...",
 *   "oidcAuthorizationEndpoint": "https://...",
 *   "oidcTokenEndpoint": "https://...",
 *   "oidcUserInfoEndpoint": "https://...",
 *   "oidcClientId": "client-id-from-identity-center"
 * }
 * </pre>
 *
 * <p><strong>Option 3: External OIDC Provider (Okta, Auth0, etc.)</strong></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "oidcIssuer": "https://your-domain.okta.com",
 *   "oidcAuthorizationEndpoint": "https://your-domain.okta.com/oauth2/v1/authorize",
 *   "oidcTokenEndpoint": "https://your-domain.okta.com/oauth2/v1/token",
 *   "oidcUserInfoEndpoint": "https://your-domain.okta.com/oauth2/v1/userinfo",
 *   "oidcClientId": "client-id-from-provider"
 * }
 * </pre>
 *
 * <h2>Post-Deployment Steps:</h2>
 * <ol>
 *   <li>Update the OIDC client secret in AWS Secrets Manager</li>
 *   <li>For Cognito: Secret is auto-populated</li>
 *   <li>For IAM Identity Center / External: Run:<br>
 *       {@code aws secretsmanager put-secret-value --secret-id STACK_NAME/APP_ID/oidc/client-secret --secret-string "YOUR_SECRET"}
 *   </li>
 *   <li>Application-specific setup (see application logs for instructions)</li>
 * </ol>
 *
 * @since 3.1.0
 */
public class ApplicationOidcFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(ApplicationOidcFactory.class.getName());

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("stackName")
    private String stackName;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    // Cognito Configuration (Option 1 - Recommended)
    @DeploymentContext("cognitoAutoProvision")
    private Boolean cognitoAutoProvision;

    // Read from DeploymentContext first (for manually configured values)
    @DeploymentContext("cognitoUserPoolId")
    private String cognitoUserPoolId;

    @DeploymentContext("cognitoUserPoolDomain")
    private String cognitoUserPoolDomain;

    @DeploymentContext("cognitoUserPoolClientId")
    private String cognitoUserPoolClientId;

    @DeploymentContext("cognitoUserPoolClientSecret")
    private String cognitoUserPoolClientSecret;

    // Note: CognitoAuthenticationFactory exports values to SystemContext Slots (cognitoUserPoolId, etc.)
    // We access these via ctx.cognitoUserPoolId.get() in buildCognitoConfiguration()
    // NOT via @SystemContext annotations, because injection happens in constructor
    // BEFORE CognitoAuthenticationFactory.create() sets the values

    @DeploymentContext("region")
    private String region;

    // Manual OIDC Configuration (Option 2 & 3)
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

    // For building application URL
    @DeploymentContext("fqdn")
    private String fqdn;

    @DeploymentContext("sslEnabled")
    private Boolean sslEnabled;

    // User group configuration for Cognito
    @DeploymentContext("cognitoCreateGroups")
    private Boolean cognitoCreateGroups;

    @DeploymentContext("cognitoAdminGroupName")
    private String cognitoAdminGroupName;

    @DeploymentContext("cognitoUserGroupName")
    private String cognitoUserGroupName;

    @SystemContext("alb")
    private software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer alb;

    @com.cloudforge.core.annotation.SystemContext("securityProfileConfig")
    private com.cloudforgeci.api.interfaces.SecurityProfileConfiguration securityProfileConfig;

    public ApplicationOidcFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        LOG.info("ApplicationOidcFactory.create() called - authMode = " + authMode);

        // Only configure if authMode is "application-oidc"
        if (!"application-oidc".equals(authMode)) {
            LOG.info("Application-level OIDC not enabled (authMode = " + authMode + ")");
            return;
        }

        LOG.info("========================================");
        LOG.info("Application-Level OIDC Configuration");
        LOG.info("========================================");

        // Check if application supports OIDC integration
        if (applicationSpec == null) {
            LOG.severe("ApplicationSpec not available - cannot configure application-level OIDC");
            LOG.severe("Ensure ApplicationSpec is set before creating ApplicationOidcFactory");
            return;
        }

        if (!applicationSpec.supportsOidcIntegration()) {
            LOG.warning("Application '" + applicationSpec.applicationId() + "' does not support OIDC integration");
            LOG.warning("Application-level OIDC is only supported for:");
            LOG.warning("  - Jenkins (oic-auth plugin)");
            LOG.warning("  - GitLab (built-in OmniAuth)");
            LOG.warning("  - Grafana (built-in generic_oauth)");
            LOG.warning("Consider using authMode='alb-oidc' for ALB-level authentication instead");
            return;
        }

        OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
        if (oidcIntegration == null) {
            LOG.severe("Application '" + applicationSpec.applicationId() + "' supports OIDC but getOidcIntegration() returned null");
            return;
        }

        LOG.info("Application: " + applicationSpec.applicationId());
        LOG.info("OIDC Integration Method: " + oidcIntegration.getIntegrationMethod());

        // Determine OIDC configuration source
        OidcConfiguration oidcConfig = buildOidcConfiguration();
        if (oidcConfig == null) {
            LOG.warning("No OIDC configuration provided");
            LOG.warning("Please configure one of:");
            LOG.warning("  1. Cognito (cognitoAutoProvision = true)");
            LOG.warning("  2. IAM Identity Center (provide oidcIssuer, oidcAuthorizationEndpoint, etc.)");
            LOG.warning("  3. External OIDC provider (provide oidcIssuer, oidcAuthorizationEndpoint, etc.)");
            return;
        }

        // Create OIDC client secret if needed
        createOidcClientSecret(oidcConfig);

        // Store OIDC configuration in SystemContext for ApplicationSpec to use
        // This allows ApplicationSpec.containerEnvironmentVariables() to add OIDC env vars
        // and runtime factories to generate OIDC configuration files
        ctx.applicationOidcConfig.set(oidcConfig);

        LOG.info("✅ OIDC configuration stored in SystemContext.applicationOidcConfig");
        LOG.info("   Provider: " + oidcConfig.getProviderType());
        LOG.info("   Groups enabled: " + oidcConfig.isGroupBasedAccessEnabled());
        LOG.info("   Application URL: " + (oidcConfig.getApplicationUrl() != null ? oidcConfig.getApplicationUrl() : "NOT SET"));
        LOG.info("Application-level OIDC configuration completed");
        LOG.info("OIDC Provider: " + oidcConfig.getProviderType());
        LOG.info("OIDC configuration stored in SystemContext for application integration");
        LOG.info("Integration will be applied during application deployment");

        // Log post-deployment instructions
        String postDeploymentInstructions = oidcIntegration.getPostDeploymentInstructions();
        if (postDeploymentInstructions != null && !postDeploymentInstructions.isBlank()) {
            LOG.info("\n" + postDeploymentInstructions);
        }

        LOG.info("========================================");
    }

    /**
     * Build OidcConfiguration from deployment context.
     * Priority: Cognito > Manual OIDC endpoints
     */
    private OidcConfiguration buildOidcConfiguration() {
        // Option 1: Cognito (auto-provisioned or existing)
        if (Boolean.TRUE.equals(cognitoAutoProvision) || cognitoUserPoolId != null) {
            return buildCognitoConfiguration();
        }

        // Option 2 & 3: Manual OIDC endpoints (IAM Identity Center or External)
        if (oidcIssuer != null && !oidcIssuer.isEmpty()) {
            return buildManualOidcConfiguration();
        }

        return null;
    }

    /**
     * Build OIDC configuration for Cognito User Pool.
     * Prioritizes SystemContext values (exported by CognitoAuthenticationFactory) over DeploymentContext.
     */
    private OidcConfiguration buildCognitoConfiguration() {
        // Priority 1: SystemContext values (exported by CognitoAuthenticationFactory.create())
        // Priority 2: DeploymentContext values (manually configured)
        String effectiveUserPoolId = ctx.cognitoUserPoolId.get().orElse(cognitoUserPoolId);
        String effectiveDomainPrefix = ctx.cognitoDomainPrefix.get().orElse(cognitoUserPoolDomain);
        String effectiveClientId = ctx.cognitoClientId.get().orElse(cognitoUserPoolClientId);

        if (effectiveUserPoolId == null || effectiveDomainPrefix == null) {
            LOG.warning("Cognito configuration incomplete");
            LOG.warning("  - User Pool ID: " + (effectiveUserPoolId != null ? "[REDACTED]" : "null"));
            LOG.warning("  - Domain Prefix: " + (effectiveDomainPrefix != null ? effectiveDomainPrefix : "null"));
            LOG.warning("Cognito User Pool will be provisioned by CognitoAuthenticationFactory");
            LOG.warning("OIDC configuration will be available after Cognito User Pool creation");
            return null;
        }

        String effectiveRegion = (region != null && !region.isEmpty()) ? region : "us-east-1";
        String issuer = "https://cognito-idp." + effectiveRegion + ".amazonaws.com/" + effectiveUserPoolId;
        String authEndpoint = "https://" + effectiveDomainPrefix + ".auth." + effectiveRegion + ".amazoncognito.com/oauth2/authorize";
        String tokenEndpoint = "https://" + effectiveDomainPrefix + ".auth." + effectiveRegion + ".amazoncognito.com/oauth2/token";
        String userInfoEndpoint = "https://" + effectiveDomainPrefix + ".auth." + effectiveRegion + ".amazoncognito.com/oauth2/userInfo";

        LOG.info("Using Cognito User Pool: " + effectiveUserPoolId);
        LOG.info("Cognito Domain: " + effectiveDomainPrefix);
        LOG.info("Client ID: [REDACTED]");

        String applicationUrl = buildApplicationUrl();
        if (applicationUrl != null) {
            LOG.info("Application URL: " + applicationUrl);
        }

        // Check if groups are enabled
        boolean groupsEnabled = (cognitoCreateGroups != null && cognitoCreateGroups);

        // Get group names from deployment context or use defaults
        // Cognito groups: "Jenkins-Admins" and "Jenkins-Users" are created by CognitoAuthenticationFactory
        // Map to application roles: Admin -> admin privileges, User -> developer privileges
        String adminGroup = (cognitoAdminGroupName != null && !cognitoAdminGroupName.isEmpty())
                ? cognitoAdminGroupName
                : "Jenkins-Admins";
        String developerGroup = (cognitoUserGroupName != null && !cognitoUserGroupName.isEmpty())
                ? cognitoUserGroupName
                : "Jenkins-Users";
        String viewerGroup = "Jenkins-Viewers";  // Optional third group for read-only access

        if (groupsEnabled) {
            LOG.info("OIDC Group Mapping (Groups Enabled):");
            LOG.info("  Admin Group: " + adminGroup);
            LOG.info("  Developer Group: " + developerGroup);
            LOG.info("  Viewer Group: " + viewerGroup);
        } else {
            LOG.info("OIDC Group Mapping: Groups disabled - all authenticated users get full access");
        }

        return new SimplifiedOidcConfiguration(
            "Cognito",  // Capital C to match case-sensitive check in createOidcClientSecret()
            issuer,
            authEndpoint,
            tokenEndpoint,
            userInfoEndpoint,
            effectiveClientId,
            buildClientSecretArn("Cognito"),
            "sub",
            "cognito:groups",
            "openid profile email",
            applicationUrl,
            groupsEnabled,
            adminGroup,
            developerGroup,
            viewerGroup
        );
    }

    /**
     * Build OIDC configuration from manual endpoints.
     */
    private OidcConfiguration buildManualOidcConfiguration() {
        if (oidcAuthorizationEndpoint == null || oidcTokenEndpoint == null ||
            oidcUserInfoEndpoint == null || oidcClientId == null) {
            LOG.warning("Manual OIDC configuration incomplete");
            LOG.warning("Required: oidcIssuer, oidcAuthorizationEndpoint, oidcTokenEndpoint, oidcUserInfoEndpoint, oidcClientId");
            return null;
        }

        // Determine provider type from issuer URL
        String providerType = "External";
        if (oidcIssuer.contains("amazoncognito.com")) {
            providerType = "Cognito";
        } else if (oidcIssuer.contains("amazonaws.com/saml")) {
            providerType = "IAM Identity Center";
        }

        LOG.info("Using " + providerType + " OIDC provider");
        LOG.info("Issuer: [REDACTED]");

        String applicationUrl = buildApplicationUrl();
        if (applicationUrl != null) {
            LOG.info("Application URL: " + applicationUrl);
        }

        // For external OIDC providers, use standard default group names
        // These can be overridden by providing cognitoAdminGroupName and cognitoUserGroupName
        String adminGroup = (cognitoAdminGroupName != null && !cognitoAdminGroupName.isEmpty())
                ? cognitoAdminGroupName
                : "Admins";
        String developerGroup = (cognitoUserGroupName != null && !cognitoUserGroupName.isEmpty())
                ? cognitoUserGroupName
                : "Developers";
        String viewerGroup = "Viewers";  // Standard viewer group

        // For manual OIDC configuration, assume groups are enabled (no way to know from context)
        boolean groupsEnabled = true;

        LOG.info("OIDC Group Mapping:");
        LOG.info("  Admin Group: " + adminGroup);
        LOG.info("  Developer Group: " + developerGroup);
        LOG.info("  Viewer Group: " + viewerGroup);

        return new SimplifiedOidcConfiguration(
            providerType,
            oidcIssuer,
            oidcAuthorizationEndpoint,
            oidcTokenEndpoint,
            oidcUserInfoEndpoint,
            oidcClientId,
            buildClientSecretArn(providerType.toLowerCase().replace(" ", "-")),
            "sub",  // Standard OIDC claim for username
            "groups",  // Standard OIDC claim for groups
            "openid profile email",
            applicationUrl,
            groupsEnabled,
            adminGroup,
            developerGroup,
            viewerGroup
        );
    }

    /**
     * Build client secret ARN for Secrets Manager.
     */
    private String buildClientSecretArn(String providerType) {
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";

        // For Cognito provider, use SystemContext secret name (exported by CognitoAuthenticationFactory)
        if ("cognito".equalsIgnoreCase(providerType)) {
            String secretNameFromSystem = ctx.cognitoClientSecretName.get().orElse(null);
            if (secretNameFromSystem != null && !secretNameFromSystem.isEmpty()) {
                return secretNameFromSystem;
            }
        }

        // For other providers, use DeploymentContext (manually configured)
        if (oidcClientSecretName != null && !oidcClientSecretName.isEmpty()) {
            return oidcClientSecretName;
        }

        // Default: Generate secret name
        return stackName + "/" + appId + "/oidc/client-secret";
    }

    /**
     * Build application URL from FQDN or ALB DNS name.
     * Priority: Custom FQDN > ALB DNS name
     */
    private String buildApplicationUrl() {
        boolean useHttps = Boolean.TRUE.equals(sslEnabled);
        String protocol = useHttps ? "https://" : "http://";

        // Priority 1: Use custom FQDN if configured
        if (fqdn != null && !fqdn.isEmpty()) {
            return protocol + fqdn;
        }

        // Priority 2: Use ALB DNS name if available
        if (alb != null) {
            return protocol + alb.getLoadBalancerDnsName();
        }

        // No URL available
        LOG.warning("No application URL available - neither FQDN nor ALB configured");
        return null;
    }

    /**
     * Find or create OIDC client secret in Secrets Manager.
     * For Cognito, the secret is populated by CognitoAuthenticationFactory.
     * For external providers, uses a simple "create-if-missing" pattern:
     * <ul>
     *   <li>If secret doesn't exist: Creates it with placeholder value</li>
     *   <li>If secret already exists: Leaves it alone (preserves user's actual value)</li>
     * </ul>
     *
     * <p><b>Removal Policy (based on security profile):</b></p>
     * <ul>
     *   <li>Production: Secret is RETAINED on stack deletion (prevents data loss)</li>
     *   <li>Dev/Staging: Secret is DESTROYED with stack (automatic cleanup)</li>
     * </ul>
     *
     * <p>To update the secret value after deployment, use AWS CLI:</p>
     * <pre>aws secretsmanager put-secret-value --secret-id SECRET_NAME --secret-string "NEW_VALUE"</pre>
     */
    private void createOidcClientSecret(OidcConfiguration config) {
        String secretName = config.getClientSecretArn();

        // Don't create secret for Cognito - CognitoAuthenticationFactory handles it
        if ("Cognito".equals(config.getProviderType())) {
            LOG.info("Cognito client secret will be managed by CognitoAuthenticationFactory");
            return;
        }

        // Determine removal policy based on security profile (injected via annotation)
        boolean isProduction = (securityProfileConfig != null && securityProfileConfig.getClass().getSimpleName().contains("Production"));
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
        String placeholderValue = "PLACEHOLDER-UPDATE-WITH-ACTUAL-CLIENT-SECRET";
        String description = "OIDC client secret for " + appId + " application-level authentication (" + config.getProviderType() + ")";

        LOG.info("Setting up OIDC client secret in Secrets Manager: " + secretName);

        // Simple approach: Create secret if it doesn't exist, leave alone if it does
        // The ignoreErrorCodesMatching handles the "already exists" case gracefully
        AwsSdkCall createSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("createSecret")
                .parameters(java.util.Map.of(
                        "Name", secretName,
                        "Description", description,
                        "SecretString", placeholderValue
                ))
                .physicalResourceId(PhysicalResourceId.of("ApplicationOidcClientSecret-" + secretName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceExistsException")  // If exists, leave it alone
                .build();

        // Delete secret on stack deletion (unless in production)
        AwsSdkCall deleteSecretCall = null;
        if (!isProduction) {
            deleteSecretCall = AwsSdkCall.builder()
                    .service("SecretsManager")
                    .action("deleteSecret")
                    .parameters(java.util.Map.of(
                            "SecretId", secretName,
                            "ForceDeleteWithoutRecovery", true
                    ))
                    .physicalResourceId(PhysicalResourceId.of("ApplicationOidcClientSecret-" + secretName))
                    .region(region)
                    .ignoreErrorCodesMatching("ResourceNotFoundException")  // Ignore if already deleted
                    .build();
        }

        AwsCustomResource.Builder secretResourceBuilder = AwsCustomResource.Builder.create(this, "ApplicationOidcClientSecretResource")
                .onCreate(createSecretCall)
                .onUpdate(createSecretCall)  // Same behavior on update - create only if missing
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of(
                                        "arn:aws:secretsmanager:" + region + ":" + Stack.of(this).getAccount() + ":secret:" + secretName + "*"
                                ))
                                .build()
                ));

        // Only set onDelete if not production (RETAIN behavior)
        if (deleteSecretCall != null) {
            secretResourceBuilder.onDelete(deleteSecretCall);
        }

        AwsCustomResource secretResource = secretResourceBuilder.build();

        if (isProduction) {
            LOG.info("Production mode: OIDC client secret will be RETAINED on stack deletion for safety");
            LOG.info("  You must manually delete the secret from AWS Secrets Manager if needed");
        } else {
            LOG.info("Non-production mode: OIDC client secret will be DESTROYED with stack");
        }

        LOG.warning("IMPORTANT: Update the client secret after deployment with your actual OIDC provider secret:");
        LOG.warning("  aws secretsmanager put-secret-value \\");
        LOG.warning("    --secret-id " + secretName + " \\");
        LOG.warning("    --secret-string \"YOUR_ACTUAL_CLIENT_SECRET_FROM_OIDC_PROVIDER\"");

        // Store the Custom Resource in SystemContext for dependency tracking
        // This ensures ECS tasks don't start before the secret is created
        ctx.applicationOidcClientSecretResource.set(secretResource);
    }

    /**
     * Simplified OIDC configuration implementation.
     */
    private static class SimplifiedOidcConfiguration implements OidcConfiguration {
        private final String providerType;
        private final String issuerUrl;
        private final String authorizationEndpoint;
        private final String tokenEndpoint;
        private final String userInfoEndpoint;
        private final String clientId;
        private final String clientSecretArn;
        private final String usernameClaim;
        private final String groupsClaim;
        private final String scopes;
        private final String applicationUrl;
        private final boolean groupsEnabled;
        private final String adminGroupName;
        private final String developerGroupName;
        private final String viewerGroupName;

        public SimplifiedOidcConfiguration(String providerType, String issuerUrl,
                                          String authorizationEndpoint, String tokenEndpoint,
                                          String userInfoEndpoint, String clientId,
                                          String clientSecretArn, String usernameClaim,
                                          String groupsClaim, String scopes, String applicationUrl,
                                          boolean groupsEnabled, String adminGroupName,
                                          String developerGroupName, String viewerGroupName) {
            this.providerType = providerType;
            this.issuerUrl = issuerUrl;
            this.authorizationEndpoint = authorizationEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.userInfoEndpoint = userInfoEndpoint;
            this.clientId = clientId;
            this.clientSecretArn = clientSecretArn;
            this.usernameClaim = usernameClaim;
            this.groupsClaim = groupsClaim;
            this.scopes = scopes;
            this.applicationUrl = applicationUrl;
            this.groupsEnabled = groupsEnabled;
            this.adminGroupName = adminGroupName;
            this.developerGroupName = developerGroupName;
            this.viewerGroupName = viewerGroupName;
        }

        @Override
        public String getProviderType() { return providerType; }

        @Override
        public String getIssuerUrl() { return issuerUrl; }

        @Override
        public String getAuthorizationEndpoint() { return authorizationEndpoint; }

        @Override
        public String getTokenEndpoint() { return tokenEndpoint; }

        @Override
        public String getUserInfoEndpoint() { return userInfoEndpoint; }

        @Override
        public String getClientId() { return clientId; }

        @Override
        public String getClientSecretArn() { return clientSecretArn; }

        @Override
        public String getJwksUri() {
            // Standard OIDC discovery: /.well-known/jwks.json
            return issuerUrl + "/.well-known/jwks.json";
        }

        @Override
        public String getRedirectUrl() {
            // Application-specific redirect URL - will be constructed by application's OIDC integration
            // Each application has different callback paths (e.g., /securityRealm/finishLogin for Jenkins)
            // Runtime factories should use the application's OidcIntegration to get the correct path
            return null;
        }

        @Override
        public String getUsernameClaim() { return usernameClaim; }

        @Override
        public String getGroupsClaim() { return groupsClaim; }

        @Override
        public String getScopes() { return scopes; }

        @Override
        public String getApplicationUrl() { return applicationUrl; }

        @Override
        public String getAdminGroupName() {
            return adminGroupName != null ? adminGroupName : "Admins";
        }

        @Override
        public String getDeveloperGroupName() {
            return developerGroupName != null ? developerGroupName : "Developers";
        }

        @Override
        public String getViewerGroupName() {
            return viewerGroupName != null ? viewerGroupName : "Viewers";
        }

        @Override
        public boolean isGroupBasedAccessEnabled() {
            return groupsEnabled;
        }
    }
}
