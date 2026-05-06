package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforgeci.api.util.CfnStringUtils;
import software.amazon.awscdk.Fn;
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
 * @since 3.0.0
 */
public class ApplicationOidcFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(ApplicationOidcFactory.class.getName());

    @DeploymentContext("authMode")
    private AuthMode authMode;

    @DeploymentContext("oidcProvider")
    private String oidcProvider;

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

    @DeploymentContext("cognitoDomainPrefix")
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

    // IAM Identity Center SAML Configuration (Option 2)
    @DeploymentContext("autoProvisionIdentityCenter")
    private Boolean autoProvisionIdentityCenter;

    @DeploymentContext("ssoInstanceArn")
    private String ssoInstanceArn;

    // Manual OIDC Configuration (Option 3)
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

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

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

        // Only configure if authMode is APPLICATION_OIDC
        if (authMode != AuthMode.APPLICATION_OIDC) {
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

        // Build OIDC configuration
        OidcConfiguration oidcConfig = buildOidcConfiguration();
        if (oidcConfig == null) {
            LOG.warning("No OIDC configuration provided");
            LOG.warning("Please configure one of:");
            LOG.warning("  1. Cognito (cognitoAutoProvision = true)");
            LOG.warning("  2. IAM Identity Center (autoProvisionIdentityCenter = true)");
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
     * Build OidcConfiguration from deployment context based on oidcProvider.
     *
     * <p>oidcProvider values:</p>
     * <ul>
     *   <li>"cognito" - Cognito User Pool OIDC only</li>
     *   <li>"cognito-saml" - Hybrid: Cognito (users/groups) + Identity Center (SAML)</li>
     *   <li>"identity-center-saml" - Pure Identity Center SAML (future)</li>
     * </ul>
     */
    private OidcConfiguration buildOidcConfiguration() {
        // Route based on oidcProvider value
        if ("cognito-saml".equals(oidcProvider)) {
            // Hybrid Architecture: Cognito manages users/groups, Identity Center provides SAML
            LOG.info("oidcProvider=cognito-saml → Hybrid architecture (Cognito + Identity Center)");
            return buildIdentityCenterSamlConfiguration();
        }

        if ("identity-center-saml".equals(oidcProvider)) {
            // Pure Identity Center SAML (future implementation)
            LOG.info("oidcProvider=identity-center-saml → Pure Identity Center SAML");
            return buildIdentityCenterSamlConfiguration();
        }

        // Detect cognito-saml hybrid architecture even when oidcProvider is not set
        // This happens when both cognitoAutoProvision and autoProvisionIdentityCenter are true with ssoInstanceArn
        if (Boolean.TRUE.equals(cognitoAutoProvision) && Boolean.TRUE.equals(autoProvisionIdentityCenter) && ssoInstanceArn != null) {
            LOG.info("Detected hybrid architecture: cognitoAutoProvision + autoProvisionIdentityCenter + ssoInstanceArn");
            LOG.info("→ Using Identity Center SAML with Cognito user management");
            return buildIdentityCenterSamlConfiguration();
        }

        if ("cognito".equals(oidcProvider) || Boolean.TRUE.equals(cognitoAutoProvision) || cognitoUserPoolId != null) {
            // Cognito User Pool OIDC
            LOG.info("oidcProvider=cognito → Cognito User Pool OIDC");
            return buildCognitoConfiguration();
        }

        // Manual OIDC endpoints (External IdP)
        if (oidcIssuer != null && !oidcIssuer.isEmpty()) {
            LOG.info("Manual OIDC configuration → External IdP");
            return buildManualOidcConfiguration();
        }

        return null;
    }

    /**
     * Build configuration for Cognito User Pool.
     *
     * <p>For SAML apps (like Mattermost), returns SAML endpoints from the same User Pool.
     * For OIDC apps, returns OAuth2 endpoints. Same User Pool supports both.</p>
     *
     * <p>Prioritizes SystemContext values (exported by CognitoAuthenticationFactory) over DeploymentContext.</p>
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

        // Check if application requires SAML authentication
        // IMPORTANT: Only use SAML if oidcProvider is "cognito-saml" or "identity-center-saml"
        // Even if the app's integration returns "SAML" as auth type, we should use OIDC for "cognito" provider
        boolean appRequiresSaml = "cognito-saml".equals(oidcProvider) ||
            "identity-center-saml".equals(oidcProvider);

        // Cognito base URL for IdP endpoints
        String cognitoIdpBase = "https://cognito-idp." + effectiveRegion + ".amazonaws.com/" + effectiveUserPoolId;
        String cognitoAuthBase = "https://" + effectiveDomainPrefix + ".auth." + effectiveRegion + ".amazoncognito.com";

        // Select endpoints based on app requirements (SAML vs OIDC)
        String issuer;
        String authEndpoint;
        String tokenEndpoint;
        String userInfoEndpoint;
        String logoutEndpoint;
        String providerType;

        if (appRequiresSaml) {
            // SAML endpoints from the same Cognito User Pool
            LOG.info("Application requires SAML - using Cognito SAML endpoints");
            providerType = "cognito";  // lowercase for MattermostSamlIntegration checks
            issuer = cognitoIdpBase;
            authEndpoint = cognitoIdpBase + "/saml2/idp/SSO";
            tokenEndpoint = cognitoIdpBase + "/saml2/idp/metadata";  // Repurposed for metadata URL
            userInfoEndpoint = cognitoIdpBase + "/saml2/idp/metadata";
            logoutEndpoint = cognitoIdpBase + "/saml2/logout";
        } else {
            // OAuth2/OIDC endpoints
            providerType = "Cognito";  // Capital C for OIDC path
            issuer = cognitoIdpBase;
            authEndpoint = cognitoAuthBase + "/oauth2/authorize";
            tokenEndpoint = cognitoAuthBase + "/oauth2/token";
            userInfoEndpoint = cognitoAuthBase + "/oauth2/userInfo";
            logoutEndpoint = cognitoAuthBase + "/logout";
        }

        LOG.info("Using Cognito User Pool: " + effectiveUserPoolId);
        LOG.info("Cognito Domain: " + effectiveDomainPrefix);
        LOG.info("Auth Type: " + (appRequiresSaml ? "SAML" : "OIDC"));
        LOG.info("Client ID: [REDACTED]");

        String applicationUrl = buildApplicationUrl();
        if (applicationUrl != null) {
            LOG.info("Application URL: " + applicationUrl);
        }

        // Check if groups are enabled
        boolean groupsEnabled = (cognitoCreateGroups != null && cognitoCreateGroups);

        // Get group names from deployment context or use defaults based on app ID
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "App";
        String appPrefix = appId.substring(0, 1).toUpperCase() + appId.substring(1);  // Capitalize first letter
        String adminGroup = (cognitoAdminGroupName != null && !cognitoAdminGroupName.isEmpty())
                ? cognitoAdminGroupName
                : appPrefix + "-Admins";
        String developerGroup = (cognitoUserGroupName != null && !cognitoUserGroupName.isEmpty())
                ? cognitoUserGroupName
                : appPrefix + "-Users";
        String viewerGroup = appPrefix + "-Viewers";

        if (groupsEnabled) {
            LOG.info("Group Mapping (Groups Enabled):");
            LOG.info("  Admin Group: " + adminGroup);
            LOG.info("  Developer Group: " + developerGroup);
            LOG.info("  Viewer Group: " + viewerGroup);
        } else {
            LOG.info("Group Mapping: Groups disabled - all authenticated users get full access");
        }

        // Build redirect URL from application URL + callback path
        String redirectUrl = null;
        if (applicationUrl != null && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            OidcIntegration integration = applicationSpec.getOidcIntegration();
            if (integration != null) {
                String callbackPath = integration.getOidcCallbackPath();
                redirectUrl = applicationUrl + callbackPath;
                LOG.info("Redirect URL: " + redirectUrl);
            }
        }

        return new SimplifiedOidcConfiguration(
            providerType,
            issuer,
            authEndpoint,
            tokenEndpoint,
            userInfoEndpoint,
            logoutEndpoint,
            effectiveClientId,
            buildClientSecretArn(providerType),
            "sub",
            "cognito:groups",
            appRequiresSaml ? "" : "openid profile email",  // Scopes not used for SAML
            applicationUrl,
            redirectUrl,  // Add redirect URL
            groupsEnabled,
            adminGroup,
            developerGroup,
            viewerGroup
        );
    }

    /**
     * Build configuration for IAM Identity Center SAML.
     *
     * <p>This method reads SAML configuration from SystemContext, which is populated
     * by IdentityCenterSamlFactory. Although this is SAML (not OIDC), we use the
     * OidcConfiguration interface to pass the IdP URLs to the application's
     * SAML integration (e.g., MattermostSamlIntegration).</p>
     *
     * <p>The key difference from OIDC is that we use SAML-specific URLs:</p>
     * <ul>
     *   <li>issuerUrl -> SAML IdP Entity ID</li>
     *   <li>authorizationEndpoint -> SAML SSO URL</li>
     *   <li>tokenEndpoint -> Not used (SAML doesn't have token endpoint)</li>
     *   <li>userInfoEndpoint -> Not used (attributes come in SAML assertion)</li>
     * </ul>
     */
    private OidcConfiguration buildIdentityCenterSamlConfiguration() {
        // Read SAML configuration from SystemContext (set by IdentityCenterSamlFactory)
        String samlSsoUrl = ctx.samlIdpSsoUrl.get().orElse(null);
        String samlMetadataUrl = ctx.samlIdpMetadataUrl.get().orElse(null);
        String samlEntityId = ctx.samlIdpEntityId.get().orElse("urn:amazon:webservices");
        String samlConfigSecretArn = ctx.samlConfigSecretArn.get().orElse(null);
        String siteUrl = ctx.samlSiteUrl.get().orElse(null);

        // If SystemContext values not yet available, construct from ssoInstanceArn
        if (samlSsoUrl == null && ssoInstanceArn != null) {
            // Extract instance ID from ARN: arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx
            String instanceId = ssoInstanceArn.substring(ssoInstanceArn.lastIndexOf("/") + 1);
            String effectiveRegion = (region != null && !region.isEmpty()) ? region : "us-east-1";

            // Use /saml/SSO for the SSO endpoint (not /saml/assertion)
            // This is what applications like Metabase expect for SAML authentication
            samlSsoUrl = "https://portal.sso." + effectiveRegion + ".amazonaws.com/saml/SSO/" + instanceId;
            samlMetadataUrl = "https://portal.sso." + effectiveRegion + ".amazonaws.com/saml/metadata/" + instanceId;

            LOG.info("Constructed Identity Center SAML URLs from SSO Instance ARN");
        }

        if (samlSsoUrl == null) {
            LOG.warning("Identity Center SAML configuration incomplete - SSO URL not available");
            LOG.warning("IdentityCenterSamlFactory should set samlIdpSsoUrl in SystemContext");
            return null;
        }

        LOG.info("Using IAM Identity Center SAML provider");
        LOG.info("SAML SSO URL: " + samlSsoUrl);
        LOG.info("SAML Metadata URL: " + (samlMetadataUrl != null ? samlMetadataUrl : "Not configured"));

        String applicationUrl = siteUrl != null ? siteUrl : buildApplicationUrl();
        if (applicationUrl != null) {
            LOG.info("Application URL: " + applicationUrl);
        }

        // For Identity Center, use standard group names
        String adminGroup = "Admins";
        String developerGroup = "Users";
        String viewerGroup = "Viewers";

        LOG.info("SAML Group Mapping (configure in Identity Center):");
        LOG.info("  Admin Group: " + adminGroup);
        LOG.info("  User Group: " + developerGroup);

        // Build redirect URL for SAML (ACS URL)
        String redirectUrl = null;
        if (applicationUrl != null && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            OidcIntegration integration = applicationSpec.getOidcIntegration();
            if (integration != null) {
                String callbackPath = integration.getOidcCallbackPath();
                redirectUrl = applicationUrl + callbackPath;
                LOG.info("SAML ACS URL: " + redirectUrl);
            }
        }

        // Return configuration that MattermostSamlIntegration can use
        // Note: tokenEndpoint and userInfoEndpoint are not used for SAML
        return new SimplifiedOidcConfiguration(
            "identity-center-saml",  // Provider type - used by MattermostSamlIntegration to select correct URLs
            samlEntityId,       // Issuer URL (IdP Entity ID)
            samlSsoUrl,         // Authorization endpoint (SAML SSO URL)
            samlMetadataUrl,    // Token endpoint (repurposed for metadata URL)
            samlMetadataUrl,    // UserInfo endpoint (repurposed for metadata URL)
            null,               // Logout endpoint
            "identity-center-saml",  // Client ID (not used for SAML, but required by interface)
            null,               // Client secret ARN - SAML doesn't use OAuth client secrets
            "preferred_username", // Username claim/attribute
            "groups",           // Groups claim/attribute
            "",                 // Scopes (not used for SAML)
            applicationUrl,
            redirectUrl,        // SAML ACS URL
            true,               // Groups enabled
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

        // Build redirect URL from application URL + callback path
        String redirectUrl = null;
        if (applicationUrl != null && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            OidcIntegration integration = applicationSpec.getOidcIntegration();
            if (integration != null) {
                String callbackPath = integration.getOidcCallbackPath();
                redirectUrl = applicationUrl + callbackPath;
                LOG.info("Redirect URL: " + redirectUrl);
            }
        }

        // For external providers, logout endpoint is typically null (not standardized)
        String logoutEndpoint = null;

        return new SimplifiedOidcConfiguration(
            providerType,
            oidcIssuer,
            oidcAuthorizationEndpoint,
            oidcTokenEndpoint,
            oidcUserInfoEndpoint,
            logoutEndpoint,
            oidcClientId,
            buildClientSecretArn(providerType.toLowerCase().replace(" ", "-")),
            "sub",  // Standard OIDC claim for username
            "groups",  // Standard OIDC claim for groups
            "openid profile email",
            applicationUrl,
            redirectUrl,  // Add redirect URL
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
     * Build application URL from domain configuration.
     * Priority: fqdn > subdomain.domain > domain > ALB DNS name (with Private CA)
     *
     * <p>For application-oidc mode, the application URL is used for OIDC redirect URLs.
     * When using ALB DNS name with enableSsl=true, HTTPS is used with AWS Private CA certificate.</p>
     */
    private String buildApplicationUrl() {
        // Priority 1: Use custom FQDN if configured (always HTTPS for custom domains)
        if (fqdn != null && !fqdn.isEmpty()) {
            return "https://" + fqdn;
        }

        // Priority 2: Use subdomain.domain if both are configured
        if (domain != null && !domain.isEmpty()) {
            if (subdomain != null && !subdomain.isEmpty()) {
                return "https://" + subdomain + "." + domain;
            }
            // Priority 3: Use domain only
            return "https://" + domain;
        }

        // Priority 4: Use ALB DNS name if available
        // With enableSsl=true: uses HTTPS with Private CA certificate
        // Without enableSsl: uses HTTP (not recommended for OIDC)
        // IMPORTANT: ALB DNS names contain mixed case (e.g., nJjqXp6M1K6T) but Cognito
        // callback URLs are case-sensitive. We must lowercase the DNS name.
        // See: https://github.com/aws/aws-cdk/issues/11171
        if (alb != null) {
            boolean useHttps = Boolean.TRUE.equals(enableSsl);
            String protocol = useHttps ? "https://" : "http://";
            // Lowercase the ALB DNS name for Cognito callback URL compatibility
            String lowercaseDns = CfnStringUtils.toLowerCase(alb.getLoadBalancerDnsName());
            String albUrl = Fn.join("", List.of(protocol, lowercaseDns));
            LOG.info("Using ALB DNS name for application URL (lowercased): " + albUrl);
            if (useHttps) {
                LOG.info("HTTPS enabled with AWS Private CA certificate");
                LOG.warning("NOTE: Private CA certificates are not trusted by browsers. Users will see certificate warnings.");
            } else {
                LOG.warning("HTTP is not recommended for OIDC. Set enableSsl=true for HTTPS with Private CA.");
            }
            return albUrl;
        }

        // No URL available
        LOG.warning("No application URL available - neither FQDN, domain, nor ALB configured");
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
        // Use equalsIgnoreCase because SAML apps use "cognito" (lowercase) while OIDC uses "Cognito"
        if ("cognito".equalsIgnoreCase(config.getProviderType())) {
            LOG.info("Cognito client secret will be managed by CognitoAuthenticationFactory");
            return;
        }

        // Don't create secret for Identity Center SAML - SAML doesn't use client secrets
        if ("identity-center-saml".equals(config.getProviderType())) {
            LOG.info("Identity Center SAML does not require client secret - skipping");
            return;
        }

        // Determine removal policy based on security profile (injected via annotation)
        boolean isProduction = (securityProfileConfig != null && securityProfileConfig.getClass().getSimpleName().contains("Production"));
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
        String placeholderValue = "PLACEHOLDER-UPDATE-WITH-ACTUAL-CLIENT-SECRET";
        String description = "OIDC client secret for " + appId + " application-level authentication (" + config.getProviderType() + ")";

        LOG.info("Setting up OIDC client secret in Secrets Manager for application: " + appId);

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

        LOG.warning("IMPORTANT: Update the client secret after deployment with your actual OIDC provider secret");
        LOG.warning("  Use AWS Console > Secrets Manager or AWS CLI to update the secret value");
        LOG.warning("  Secret name pattern: " + appId + "/oidc/client-secret");

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
        private final String logoutEndpoint;
        private final String clientId;
        private final String clientSecretArn;
        private final String usernameClaim;
        private final String groupsClaim;
        private final String scopes;
        private final String applicationUrl;
        private final String redirectUrl;
        private final boolean groupsEnabled;
        private final String adminGroupName;
        private final String developerGroupName;
        private final String viewerGroupName;

        public SimplifiedOidcConfiguration(String providerType, String issuerUrl,
                                          String authorizationEndpoint, String tokenEndpoint,
                                          String userInfoEndpoint, String logoutEndpoint,
                                          String clientId, String clientSecretArn, String usernameClaim,
                                          String groupsClaim, String scopes, String applicationUrl,
                                          String redirectUrl, boolean groupsEnabled, String adminGroupName,
                                          String developerGroupName, String viewerGroupName) {
            this.providerType = providerType;
            this.issuerUrl = issuerUrl;
            this.authorizationEndpoint = authorizationEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.userInfoEndpoint = userInfoEndpoint;
            this.logoutEndpoint = logoutEndpoint;
            this.clientId = clientId;
            this.clientSecretArn = clientSecretArn;
            this.usernameClaim = usernameClaim;
            this.groupsClaim = groupsClaim;
            this.scopes = scopes;
            this.applicationUrl = applicationUrl;
            this.redirectUrl = redirectUrl;
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
        public String getLogoutEndpoint() { return logoutEndpoint; }

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
            // Redirect URL built from applicationUrl + application's callback path
            return redirectUrl;
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
