package com.cloudforge.core.interfaces;

/**
 * OIDC configuration for application-level authentication.
 *
 * <p>This interface provides OIDC endpoints and credentials for integrating
 * CloudForge-managed authentication (Cognito, IAM Identity Center) with
 * application-level authentication systems.</p>
 *
 * <p><strong>Supported Applications:</strong></p>
 * <ul>
 *   <li>Jenkins (via OIDC plugin)</li>
 *   <li>GitLab (built-in OIDC)</li>
 *   <li>Grafana (built-in OIDC)</li>
 *   <li>SonarQube (OIDC plugin)</li>
 *   <li>Nexus (OIDC support)</li>
 * </ul>
 *
 * <p><strong>CloudForge 3.0.0: Universal Authentication Integration</strong></p>
 *
 * @see ApplicationSpec#getOidcIntegration()
 */
public interface OidcConfiguration {

    /**
     * Returns the OIDC provider type.
     *
     * @return provider type (cognito, identity-center, external)
     */
    String getProviderType();

    /**
     * Returns the OIDC issuer URL.
     * This is the base URL for the OIDC provider.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Cognito: https://cognito-idp.{region}.amazonaws.com/{userPoolId}</li>
     *   <li>Identity Center: https://{tenant}.awsapps.com/start</li>
     * </ul>
     *
     * @return issuer URL
     */
    String getIssuerUrl();

    /**
     * Returns the OIDC authorization endpoint.
     *
     * <p>This is where users are redirected to authenticate.</p>
     *
     * @return authorization endpoint URL
     */
    String getAuthorizationEndpoint();

    /**
     * Returns the OIDC token endpoint.
     *
     * <p>This is where applications exchange authorization codes for tokens.</p>
     *
     * @return token endpoint URL
     */
    String getTokenEndpoint();

    /**
     * Returns the OIDC userinfo endpoint.
     *
     * <p>This is where applications retrieve user profile information.</p>
     *
     * @return userinfo endpoint URL
     */
    String getUserInfoEndpoint();

    /**
     * Returns the OIDC JWKS (JSON Web Key Set) endpoint.
     *
     * <p>This is where applications retrieve public keys to verify JWT signatures.</p>
     *
     * @return JWKS endpoint URL
     */
    String getJwksUri();

    /**
     * Returns the OIDC logout endpoint.
     *
     * <p>For Cognito, this is: https://{domain}.auth.{region}.amazoncognito.com/logout</p>
     * <p>The full logout URL requires client_id and logout_uri parameters.</p>
     *
     * @return logout endpoint URL (may be null if not supported)
     */
    default String getLogoutEndpoint() {
        return null;
    }

    /**
     * Returns the OAuth 2.0 client ID.
     *
     * <p>This is the application identifier registered with the OIDC provider.</p>
     *
     * @return client ID
     */
    String getClientId();

    /**
     * Returns the AWS Secrets Manager ARN for the client secret.
     *
     * <p>The client secret is stored securely in AWS Secrets Manager.
     * Applications retrieve it at runtime using IAM permissions.</p>
     *
     * @return Secrets Manager ARN for client secret
     */
    String getClientSecretArn();

    /**
     * Returns the redirect URL for this application.
     *
     * <p>This is the callback URL where the OIDC provider redirects after authentication.
     * Typically: https://{application-domain}/oauth2/callback or similar.</p>
     *
     * @return redirect URL
     */
    String getRedirectUrl();

    /**
     * Returns the OAuth 2.0 scopes requested by this application.
     *
     * <p>Common scopes: openid, profile, email, groups</p>
     *
     * @return space-separated list of scopes
     */
    String getScopes();

    /**
     * Returns the claim name for username mapping.
     *
     * <p>This JWT claim is used as the application username.</p>
     * <p>Common values: preferred_username, email, sub</p>
     *
     * @return username claim name
     */
    String getUsernameClaim();

    /**
     * Returns the claim name for user's full name.
     *
     * <p>Common values: name, full_name</p>
     *
     * @return full name claim name (optional)
     */
    default String getFullNameClaim() {
        return "name";
    }

    /**
     * Returns the claim name for user's email.
     *
     * <p>Common values: email</p>
     *
     * @return email claim name (optional)
     */
    default String getEmailClaim() {
        return "email";
    }

    /**
     * Returns the claim name for group membership.
     *
     * <p>This is used for role-based access control in applications.</p>
     * <p>Common values: cognito:groups, groups</p>
     *
     * @return groups claim name (optional)
     */
    default String getGroupsClaim() {
        return "cognito:groups";
    }

    /**
     * Returns whether to automatically create users on first login.
     *
     * <p>Most applications support auto-provisioning via OIDC.</p>
     *
     * @return true if users should be auto-created
     */
    default boolean isAutoCreateUsers() {
        return true;
    }

    /**
     * Returns the admin group name for role mapping.
     *
     * <p>Users in this group receive admin privileges in the application.</p>
     *
     * @return admin group name (optional)
     */
    default String getAdminGroupName() {
        return "Admins";
    }

    /**
     * Returns the developer group name for role mapping.
     *
     * <p>Users in this group receive build and configure permissions in the application.</p>
     *
     * @return developer group name (optional)
     */
    default String getDeveloperGroupName() {
        return "Developers";
    }

    /**
     * Returns the viewer group name for role mapping.
     *
     * <p>Users in this group receive read-only permissions in the application.</p>
     *
     * @return viewer group name (optional)
     */
    default String getViewerGroupName() {
        return "Viewers";
    }

    /**
     * Returns whether to use PKCE (Proof Key for Code Exchange).
     *
     * <p>PKCE is recommended for enhanced security, especially for public clients.</p>
     *
     * @return true if PKCE should be used
     */
    default boolean usePkce() {
        return true;
    }

    /**
     * Returns additional OIDC configuration properties.
     *
     * <p>Application-specific OIDC settings can be passed here.</p>
     *
     * @return map of additional properties
     */
    default java.util.Map<String, String> getAdditionalProperties() {
        return java.util.Collections.emptyMap();
    }

    /**
     * Returns the application root URL.
     *
     * <p>This is the base URL where the application is accessible (e.g., https://jenkins.example.com).
     * Used by applications to configure their root URL for features like email notifications,
     * PR status updates, and environment variables like BUILD_URL.</p>
     *
     * @return application root URL (may be null if not configured)
     */
    default String getApplicationUrl() {
        return null;
    }

    /**
     * Returns whether group-based access control is enabled.
     *
     * <p>When false, applications should grant full access to all authenticated users
     * rather than using group-based permissions. This is useful when Cognito groups
     * are not created (cognitoCreateGroups = false).</p>
     *
     * @return true if group-based access control is enabled, false for open access
     */
    default boolean isGroupBasedAccessEnabled() {
        return true;
    }
}
