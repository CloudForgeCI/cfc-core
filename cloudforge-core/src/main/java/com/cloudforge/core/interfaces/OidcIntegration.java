package com.cloudforge.core.interfaces;

/**
 * Application-level OIDC integration interface.
 *
 * <p>This interface defines how applications integrate with OIDC providers
 * for authentication. Each application implements this to configure its
 * specific OIDC plugin/module.</p>
 *
 * <p><strong>CloudForge supports two separate authentication systems:</strong></p>
 * <ul>
 *   <li><strong>Amazon Cognito</strong> - Standalone user directory with OIDC</li>
 *   <li><strong>IAM Identity Center</strong> - Enterprise SSO with SAML/OIDC</li>
 * </ul>
 *
 * <p>These are completely separate systems and cannot be mixed.</p>
 *
 * @see ApplicationSpec#supportsOidcIntegration()
 */
public interface OidcIntegration {

    /**
     * Returns whether this application supports OIDC integration.
     *
     * @return true if application has OIDC support
     */
    boolean isSupported();

    /**
     * Returns the OIDC integration method for this application.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>jenkins: OIDC Plugin</li>
     *   <li>gitlab: Built-in OmniAuth</li>
     *   <li>grafana: Built-in generic_oauth</li>
     *   <li>sonarqube: OIDC Plugin</li>
     * </ul>
     *
     * @return integration method description
     */
    String getIntegrationMethod();

    /**
     * Returns environment variables needed for OIDC configuration.
     *
     * <p>These are passed to the container or EC2 userdata script.</p>
     *
     * <p>Example for Grafana:</p>
     * <pre>
     * GF_AUTH_GENERIC_OAUTH_ENABLED=true
     * GF_AUTH_GENERIC_OAUTH_NAME=Cognito
     * GF_AUTH_GENERIC_OAUTH_CLIENT_ID=${clientId}
     * GF_AUTH_GENERIC_OAUTH_AUTH_URL=${authUrl}
     * </pre>
     *
     * @param config OIDC configuration from provider
     * @return map of environment variable name to value
     */
    java.util.Map<String, String> getEnvironmentVariables(OidcConfiguration config);

    /**
     * Returns configuration file content for OIDC setup.
     *
     * <p>Some applications require configuration files instead of environment variables.</p>
     *
     * <p>Example for GitLab gitlab.rb:</p>
     * <pre>
     * gitlab_rails['omniauth_enabled'] = true
     * gitlab_rails['omniauth_providers'] = [
     *   {
     *     name: 'openid_connect',
     *     args: { ... }
     *   }
     * ]
     * </pre>
     *
     * @param config OIDC configuration from provider
     * @return configuration file content (optional)
     */
    default String getConfigurationFile(OidcConfiguration config) {
        return null;
    }

    /**
     * Returns the file path where configuration should be written.
     *
     * <p>Only used if getConfigurationFile() returns non-null.</p>
     *
     * @return configuration file path (optional)
     */
    default String getConfigurationFilePath() {
        return null;
    }

    /**
     * Returns UserData commands for setting up OIDC integration.
     *
     * <p>These commands are added to the EC2 userdata script to configure
     * OIDC integration during instance initialization.</p>
     *
     * @param config OIDC configuration from provider
     * @param context EC2 context with stack information
     * @return list of shell commands
     */
    java.util.List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context);

    /**
     * Returns post-deployment instructions for completing OIDC setup.
     *
     * <p>Some applications require manual steps after deployment (e.g., installing plugins).</p>
     *
     * @return human-readable instructions (optional)
     */
    default String getPostDeploymentInstructions() {
        return null;
    }

    /**
     * Returns the application startup command for Fargate containers.
     *
     * <p>This command is used to start the application after the OIDC configuration
     * file has been created. Each application has a different startup script.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Jenkins: /usr/local/bin/jenkins.sh</li>
     *   <li>GitLab: /assets/wrapper</li>
     *   <li>Grafana: /run.sh</li>
     *   <li>Mattermost: /mattermost/bin/mattermost (distroless - Go binary)</li>
     * </ul>
     *
     * @return startup command path
     */
    default String getContainerStartupCommand() {
        // Default: assume standard Unix convention
        return "/usr/local/bin/start.sh";
    }

    /**
     * Returns the OIDC callback path for this application.
     *
     * <p>This is the path where the OIDC provider redirects after authentication.
     * Each application has a different callback path based on its OIDC implementation.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Jenkins: /securityRealm/finishLogin</li>
     *   <li>Mattermost: /signup/gitlab/complete (uses GitLab OAuth provider for OIDC)</li>
     *   <li>GitLab: /users/auth/openid_connect/callback</li>
     *   <li>Grafana: /login/generic_oauth</li>
     * </ul>
     *
     * @return callback path (e.g., "/securityRealm/finishLogin")
     */
    default String getOidcCallbackPath() {
        // Default: Jenkins callback path (most common)
        return "/securityRealm/finishLogin";
    }

    /**
     * Returns whether the container image is distroless (has no shell).
     *
     * <p>Distroless images contain only the application binary and dependencies,
     * without a shell (/bin/sh). This affects how OIDC configuration is applied:</p>
     * <ul>
     *   <li><strong>Normal images:</strong> Use /bin/sh -c to write config files at startup</li>
     *   <li><strong>Distroless images:</strong> Must use environment variables only</li>
     * </ul>
     *
     * <p>Examples of distroless images:</p>
     * <ul>
     *   <li>mattermost/mattermost-team-edition - Go binary, no shell</li>
     *   <li>gcr.io/distroless/* - Google distroless images</li>
     * </ul>
     *
     * @return true if the container is distroless and has no shell
     */
    default boolean isDistroless() {
        return false;
    }

    // ==================== Authentication Capability Methods ====================

    /**
     * Returns whether this application supports ALB-level OIDC authentication.
     *
     * <p>ALB-level auth means authentication is handled by the ALB before traffic
     * reaches the application. The application sees already-authenticated users
     * via headers (X-Amzn-Oidc-*).</p>
     *
     * <p>Most applications support this as it's transparent to the application.</p>
     *
     * @return true if ALB-level OIDC is supported (default: true)
     */
    default boolean supportsAlbOidc() {
        return true;
    }

    /**
     * Returns whether this application supports Cognito as an identity provider.
     *
     * <p>Cognito provides:</p>
     * <ul>
     *   <li>User pool with email/password authentication</li>
     *   <li>MFA support (TOTP, SMS)</li>
     *   <li>OAuth 2.0 / OIDC endpoints</li>
     *   <li>Hosted UI for login</li>
     * </ul>
     *
     * @return true if Cognito OIDC is supported (default: true)
     */
    default boolean supportsCognito() {
        return true;
    }

    /**
     * Returns whether this application supports IAM Identity Center SAML.
     *
     * <p>IAM Identity Center (formerly AWS SSO) provides:</p>
     * <ul>
     *   <li>SAML 2.0 authentication</li>
     *   <li>Enterprise directory integration</li>
     *   <li>Group-based access control</li>
     *   <li>Centralized user management</li>
     * </ul>
     *
     * <p>Applications that use SAML (Mattermost, Metabase) support this.
     * Applications that only use OIDC may not.</p>
     *
     * @return true if IAM Identity Center SAML is supported (default: false)
     */
    default boolean supportsIdentityCenterSaml() {
        return false;
    }

    /**
     * Returns whether this application supports application-level OIDC.
     *
     * <p>Application-level OIDC means the application handles authentication itself
     * using its built-in OIDC/OAuth support. The application needs:</p>
     * <ul>
     *   <li>Client ID and secret</li>
     *   <li>OIDC endpoints (issuer, auth, token, userinfo)</li>
     *   <li>Redirect URL configuration</li>
     * </ul>
     *
     * <p>Examples: Jenkins OIDC plugin, GitLab OmniAuth, Grafana generic_oauth</p>
     *
     * @return true if application-level OIDC is supported (default: true if integration exists)
     */
    default boolean supportsApplicationOidc() {
        return isSupported();
    }

    /**
     * Returns the authentication type this integration uses.
     *
     * @return "OIDC" or "SAML"
     */
    default String getAuthenticationType() {
        return "OIDC";
    }

    // ==================== SAML Certificate Configuration ====================

    /**
     * Returns whether this application needs a SAML IdP certificate at startup.
     *
     * <p>SAML applications typically need the IdP's X.509 certificate to verify
     * SAML assertions. This certificate is fetched from the IdP metadata URL
     * and mounted into the container via an init container.</p>
     *
     * @return true if SAML certificate is required (default: true for SAML apps)
     */
    default boolean needsSamlCertificate() {
        return "SAML".equals(getAuthenticationType());
    }

    /**
     * Returns the directory path where SAML certificate should be mounted.
     *
     * <p>This path is used by the init container to write the certificate
     * and by the main container to read it.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Mattermost: /mattermost/saml (NOT /mattermost/config to avoid conflicts)</li>
     *   <li>Metabase: /metabase/saml</li>
     * </ul>
     *
     * @return mount path for SAML certificate directory
     */
    default String getSamlCertificateMountPath() {
        return "/app/saml";
    }

    /**
     * Returns the full file path for the SAML IdP certificate.
     *
     * <p>This is the complete path including filename where the certificate
     * will be written. Applications reference this path in their SAML configuration.</p>
     *
     * @return full path to the certificate file
     */
    default String getSamlCertificateFilePath() {
        return getSamlCertificateMountPath() + "/idp.crt";
    }

    /**
     * Returns the environment variable name for the SAML certificate path.
     *
     * <p>Applications configure SAML via environment variables. This returns
     * the variable name that holds the certificate file path.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Mattermost: MM_SAMLSETTINGS_IDPCERTIFICATEFILE</li>
     *   <li>Metabase: MB_SAML_IDENTITY_PROVIDER_CERTIFICATE</li>
     * </ul>
     *
     * @return environment variable name for certificate path, or null if not applicable
     */
    default String getSamlCertificateEnvVar() {
        return null;
    }
}
