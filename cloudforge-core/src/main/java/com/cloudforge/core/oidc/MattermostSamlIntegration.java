package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SAML 2.0 integration for Mattermost.
 *
 * <p><strong>Why SAML over OIDC:</strong></p>
 * <ul>
 *   <li>SAML supports group synchronization via AD/LDAP integration</li>
 *   <li>OIDC in Mattermost does NOT support LDAP data sync</li>
 *   <li>SAML enables automatic team/channel membership management</li>
 *   <li>Role mapping from IdP groups to Mattermost roles</li>
 * </ul>
 *
 * <p><strong>AWS Cognito as SAML IdP:</strong></p>
 * <ul>
 *   <li>Configure Cognito User Pool as SAML 2.0 Identity Provider</li>
 *   <li>Set ACS URL to: https://{mattermost}/login/sso/saml</li>
 *   <li>Map attributes: email, firstName, lastName, groups</li>
 * </ul>
 *
 * <p><strong>IAM Identity Center as SAML IdP:</strong></p>
 * <ul>
 *   <li>Create custom SAML 2.0 application in Identity Center</li>
 *   <li>Configure attribute mappings for user attributes</li>
 *   <li>Assign users and groups to the application</li>
 * </ul>
 *
 * <p><strong>Group Sync (Enterprise):</strong></p>
 * <p>With SAML + AD/LDAP sync enabled, Mattermost can:</p>
 * <ul>
 *   <li>Automatically add users to teams based on group membership</li>
 *   <li>Manage channel membership via groups</li>
 *   <li>Assign admin roles based on group membership</li>
 * </ul>
 *
 * @see <a href="https://docs.mattermost.com/onboard/sso-saml.html">Mattermost SAML Documentation</a>
 * @see <a href="https://docs.mattermost.com/onboard/sso-saml-ldapsync.html">SAML + AD/LDAP Sync</a>
 */
public class MattermostSamlIntegration implements OidcIntegration {

    /**
     * Mount path for the SAML certificate directory.
     * <p><strong>Important:</strong> Do NOT use /mattermost/config because
     * Mattermost needs write access to /mattermost/config/config.json.</p>
     */
    private static final String SAML_MOUNT_PATH = "/mattermost/saml";

    /**
     * Full path to the SAML IdP certificate file.
     * @deprecated Use {@link #getSamlCertificateFilePath()} instead
     */
    @Deprecated
    public static final String SAML_CERTIFICATE_MOUNT_PATH = SAML_MOUNT_PATH + "/idp.crt";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "SAML 2.0 authentication (configured via MM_SAMLSETTINGS_* environment variables). " +
               "Supports group sync via AD/LDAP integration for automatic team/channel membership.";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        // Check provider type - delegate to OIDC for "cognito", use SAML for "cognito-saml" or "identity-center"
        if ("cognito".equals(config.getProviderType())) {
            // Delegate to OIDC integration
            MattermostOidcIntegration oidcIntegration = new MattermostOidcIntegration();
            return oidcIntegration.getEnvironmentVariables(config);
        }

        Map<String, String> env = new HashMap<>();

        // Enable SAML login
        env.put("MM_SAMLSETTINGS_ENABLE", "true");

        // Identity Provider configuration
        String idpUrl = getSamlSsoUrl(config);
        env.put("MM_SAMLSETTINGS_IDPURL", idpUrl);

        // IdP Issuer/Entity ID
        env.put("MM_SAMLSETTINGS_IDPDESCRIPTORURL", config.getIssuerUrl());

        // IdP Metadata URL - stored for reference, but Mattermost REQUIRES the actual certificate file
        // The metadata URL is used by ContainerFactory's init container to fetch the certificate
        String metadataUrl = getSamlMetadataUrl(config);
        if (metadataUrl != null) {
            env.put("MM_SAMLSETTINGS_IDPMETADATAURL", metadataUrl);
        }

        // Service Provider configuration
        // Use applicationUrl if available, otherwise derive from redirectUrl
        String siteUrl = getEffectiveSiteUrl(config);
        env.put("MM_SERVICESETTINGS_SITEURL", siteUrl);
        // Service Provider Identifier (Entity ID) - REQUIRED
        // This is how Mattermost identifies itself to the IdP
        env.put("MM_SAMLSETTINGS_SERVICEPROVIDERIDENTIFIER", siteUrl);
        // Mattermost SAML callback URL (ACS URL)
        env.put("MM_SAMLSETTINGS_ASSERTIONCONSUMERSERVICEURL", siteUrl + "/login/sso/saml");

        // IdP certificate file path - ALWAYS required by Mattermost
        // For ECS Fargate deployments, ContainerFactory creates an init container that:
        // 1. Fetches the SAML metadata XML from the metadata URL
        // 2. Extracts the X509Certificate from the XML
        // 3. Writes it to this path in a shared volume
        // The main container then mounts this volume and reads the certificate
        env.put("MM_SAMLSETTINGS_IDPCERTIFICATEFILE", SAML_CERTIFICATE_MOUNT_PATH);

        // Signing configuration
        env.put("MM_SAMLSETTINGS_VERIFY", "true");
        env.put("MM_SAMLSETTINGS_ENCRYPT", "false");
        env.put("MM_SAMLSETTINGS_SIGNREQUEST", "false");

        // Attribute mappings - standard SAML attributes
        env.put("MM_SAMLSETTINGS_EMAILATTRIBUTE", "email");
        env.put("MM_SAMLSETTINGS_USERNAMEATTRIBUTE", getUsernameAttribute(config));
        env.put("MM_SAMLSETTINGS_FIRSTNAMEATTRIBUTE", "firstName");
        env.put("MM_SAMLSETTINGS_LASTNAMEATTRIBUTE", "lastName");

        // Optional attributes
        env.put("MM_SAMLSETTINGS_NICKNAMEATTRIBUTE", "");
        env.put("MM_SAMLSETTINGS_POSITIONATTRIBUTE", "");
        env.put("MM_SAMLSETTINGS_LOCALEATTRIBUTE", "");

        // Admin attribute for automatic admin role assignment
        // Must enable EnableAdminAttribute first, then set the attribute condition
        // Format: "field=value" - users with matching SAML attribute become System Admins
        // Disabled by default - enable when IdP sends admin role information
        env.put("MM_SAMLSETTINGS_ENABLEADMINATTRIBUTE", "false");
        env.put("MM_SAMLSETTINGS_ADMINATTRIBUTE", "");

        // Guest attribute for automatic guest role assignment
        // Format: "field=value" - users with matching SAML attribute become Guests
        // Disabled by default - enable when IdP sends guest role information
        env.put("MM_SAMLSETTINGS_GUESTATTRIBUTE", "");

        // Login button customization
        String providerType = config.getProviderType();
        String buttonText;
        if ("cognito".equals(providerType) || "cognito-saml".equals(providerType)) {
            buttonText = "Sign in with AWS Cognito";
        } else {
            buttonText = "Sign in with AWS IAM Identity Center";
        }
        env.put("MM_SAMLSETTINGS_LOGINBUTTONTEXT", buttonText);

        // Enable AD/LDAP sync with SAML (Enterprise feature)
        // This allows group-based team/channel management
        env.put("MM_SAMLSETTINGS_ENABLESYNCWITHLDAP", "true");

        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure Mattermost SAML 2.0 integration");
        commands.add("# SAML provides group sync support that OIDC lacks");
        commands.add("");

        // Create certificate directory
        commands.add("# Create certificate directory for SAML");
        commands.add("mkdir -p /opt/mattermost/config/saml");
        commands.add("chown -R 2000:2000 /opt/mattermost/config/saml");
        commands.add("");

        // Retrieve SAML IdP certificate from Secrets Manager
        commands.add("# Retrieve SAML IdP certificate from AWS Secrets Manager");
        commands.add("echo 'Retrieving SAML IdP certificate...' >> /var/log/userdata.log");
        commands.add("SAML_CERT=$(aws secretsmanager get-secret-value \\");
        commands.add("  --secret-id " + config.getClientSecretArn() + " \\");
        commands.add("  --query SecretString --output text 2>>/var/log/userdata.log)");
        commands.add("");
        commands.add("if [ -z \"$SAML_CERT\" ]; then");
        commands.add("  echo 'ERROR: Failed to retrieve SAML IdP certificate' >> /var/log/userdata.log");
        commands.add("  export SAML_RETRIEVAL_FAILED=true");
        commands.add("else");
        commands.add("  echo \"$SAML_CERT\" > /opt/mattermost/config/saml/idp-certificate.crt");
        commands.add("  chmod 644 /opt/mattermost/config/saml/idp-certificate.crt");
        commands.add("  chown 2000:2000 /opt/mattermost/config/saml/idp-certificate.crt");
        commands.add("  echo 'SAML IdP certificate saved' >> /var/log/userdata.log");
        commands.add("fi");
        commands.add("");

        // Create SAML environment file
        commands.add("# Create Mattermost SAML configuration");
        commands.add("cat > /opt/mattermost/mattermost-saml-env.sh <<'EOF'");
        commands.add("export MM_SAMLSETTINGS_ENABLE=true");

        String idpUrl = getSamlSsoUrl(config);
        commands.add("export MM_SAMLSETTINGS_IDPURL=\"" + idpUrl + "\"");
        commands.add("export MM_SAMLSETTINGS_IDPDESCRIPTORURL=\"" + config.getIssuerUrl() + "\"");

        String metadataUrl = getSamlMetadataUrl(config);
        if (metadataUrl != null) {
            commands.add("export MM_SAMLSETTINGS_IDPMETADATAURL=\"" + metadataUrl + "\"");
        }

        // Service provider config - use applicationUrl or derive from redirectUrl
        String siteUrl = getEffectiveSiteUrl(config);
        commands.add("export MM_SERVICESETTINGS_SITEURL=\"" + siteUrl + "\"");
        // Service Provider Identifier (Entity ID) - REQUIRED
        commands.add("export MM_SAMLSETTINGS_SERVICEPROVIDERIDENTIFIER=\"" + siteUrl + "\"");
        commands.add("export MM_SAMLSETTINGS_ASSERTIONCONSUMERSERVICEURL=\"" + siteUrl + "/login/sso/saml\"");

        // Certificate path
        commands.add("export MM_SAMLSETTINGS_IDPCERTIFICATEFILE=\"/mattermost/config/saml/idp-certificate.crt\"");

        // Security settings
        commands.add("export MM_SAMLSETTINGS_VERIFY=true");
        commands.add("export MM_SAMLSETTINGS_ENCRYPT=false");
        commands.add("export MM_SAMLSETTINGS_SIGNREQUEST=false");

        // Attribute mappings
        commands.add("export MM_SAMLSETTINGS_EMAILATTRIBUTE=\"email\"");
        commands.add("export MM_SAMLSETTINGS_USERNAMEATTRIBUTE=\"" + getUsernameAttribute(config) + "\"");
        commands.add("export MM_SAMLSETTINGS_FIRSTNAMEATTRIBUTE=\"firstName\"");
        commands.add("export MM_SAMLSETTINGS_LASTNAMEATTRIBUTE=\"lastName\"");

        // Role attributes - disabled by default (empty = disabled)
        // To enable: set EnableAdminAttribute=true and AdminAttribute="field=value"
        commands.add("export MM_SAMLSETTINGS_ENABLEADMINATTRIBUTE=false");
        commands.add("export MM_SAMLSETTINGS_ADMINATTRIBUTE=\"\"");
        commands.add("export MM_SAMLSETTINGS_GUESTATTRIBUTE=\"\"");

        // Button text
        String providerType = config.getProviderType();
        String buttonText = ("cognito".equals(providerType) || "cognito-saml".equals(providerType)) ?
            "Sign in with AWS Cognito" : "Sign in with AWS IAM Identity Center";
        commands.add("export MM_SAMLSETTINGS_LOGINBUTTONTEXT=\"" + buttonText + "\"");

        // Enable LDAP sync
        commands.add("export MM_SAMLSETTINGS_ENABLESYNCWITHLDAP=true");

        commands.add("EOF");
        commands.add("");

        // Verify certificate was retrieved
        commands.add("if [ \"$SAML_RETRIEVAL_FAILED\" = \"true\" ]; then");
        commands.add("  echo 'SAML configuration incomplete - certificate missing' >> /var/log/userdata.log");
        commands.add("  exit 1");
        commands.add("fi");
        commands.add("");

        commands.add("# Source environment for container startup");
        commands.add("chmod 600 /opt/mattermost/mattermost-saml-env.sh");
        commands.add("source /opt/mattermost/mattermost-saml-env.sh");
        commands.add("");

        commands.add("echo 'Mattermost SAML integration configured' >> /var/log/userdata.log");
        commands.add("echo 'Group sync available via AD/LDAP integration (Enterprise)' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getContainerStartupCommand() {
        // Mattermost uses a Go binary, not a shell script
        // The official image is distroless (no /bin/sh)
        return "/mattermost/bin/mattermost";
    }

    @Override
    public boolean isDistroless() {
        // Mattermost official image is distroless - contains only Go binary
        // No /bin/sh available, must use environment variables only
        return true;
    }

    @Override
    public boolean supportsCognito() {
        // Cognito requires manual SAML setup in Cognito console
        // Not auto-provisioned, but works if user configures it
        return true;
    }

    @Override
    public boolean supportsIdentityCenterSaml() {
        // Full support - auto-provisioned via IdentityCenterSamlFactory
        return true;
    }

    @Override
    public String getAuthenticationType() {
        return "SAML";
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
                Mattermost SAML 2.0 Integration (Enterprise Edition)
                ====================================================

                EDITION INFO:
                This deployment uses Mattermost Enterprise Edition which runs
                in free mode by default. SAML authentication works without a
                license, but group sync requires an Enterprise license.

                To activate Enterprise features:
                1. Go to System Console > Edition and License
                2. Upload license file or start a 30-day trial
                3. Enterprise features unlock immediately

                AWS Cognito Setup:
                1. Enable SAML 2.0 in Cognito User Pool (App Integration)
                2. Download SAML metadata/certificate from Cognito
                3. Store certificate in AWS Secrets Manager
                4. Configure attribute mappings in Cognito:
                   - email -> email
                   - given_name -> firstName
                   - family_name -> lastName

                IAM Identity Center Setup:
                1. Create custom SAML 2.0 application
                2. Set ACS URL: https://{your-mattermost}/login/sso/saml
                3. Set Entity ID: https://{your-mattermost}
                4. Map attributes: email, firstName, lastName
                5. Assign users/groups to the application

                Mattermost Configuration:
                1. Access: https://{your-domain}:8065
                2. System Console > Authentication > SAML 2.0
                3. Verify settings match your IdP configuration
                4. Test SSO login

                Group Sync (requires Enterprise license + AD/LDAP):
                Group sync requires both an Enterprise license AND a separate
                AD/LDAP server (e.g., AWS Managed Microsoft AD).
                1. System Console > Authentication > AD/LDAP
                2. Configure AD/LDAP connection
                3. Enable "Enable AD/LDAP Synchronization with SAML"
                4. Configure group-to-team/channel mappings
                """;
    }

    /**
     * Gets the effective site URL for Mattermost.
     *
     * <p>Priority order:</p>
     * <ol>
     *   <li>applicationUrl if configured (custom domain like https://mattermost.example.com)</li>
     *   <li>Derived from redirectUrl (extract base URL from callback URL)</li>
     * </ol>
     */
    private String getEffectiveSiteUrl(OidcConfiguration config) {
        // First try applicationUrl (custom domain)
        String appUrl = config.getApplicationUrl();
        if (appUrl != null && !appUrl.isEmpty()) {
            return appUrl;
        }

        // Fallback: derive from redirectUrl
        // redirectUrl is typically: https://alb-dns-name.region.elb.amazonaws.com/login/sso/saml
        // or https://mattermost.example.com/login/sso/saml
        String redirectUrl = config.getRedirectUrl();
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            // Extract base URL (everything before the path)
            try {
                java.net.URI uri = new java.net.URI(redirectUrl);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                int port = uri.getPort();

                if (port > 0 && port != 443 && port != 80) {
                    return scheme + "://" + host + ":" + port;
                } else {
                    return scheme + "://" + host;
                }
            } catch (Exception e) {
                // If URI parsing fails, return a placeholder
                return "https://mattermost.example.com";
            }
        }

        // Last resort fallback
        return "https://mattermost.example.com";
    }

    /**
     * Gets the SAML SSO URL for the identity provider.
     */
    private String getSamlSsoUrl(OidcConfiguration config) {
        String providerType = config.getProviderType();
        if ("cognito".equals(providerType) || "cognito-saml".equals(providerType)) {
            // Cognito SAML SSO endpoint (via Keycloak for cognito-saml)
            return config.getIssuerUrl() + "/saml2/idp/SSO";
        } else if ("identity-center-saml".equals(providerType)) {
            // Identity Center SAML SSO URL
            // If authorizationEndpoint already contains SAML path (set by ApplicationOidcFactory),
            // use it directly. Otherwise, derive from OIDC endpoint.
            String authEndpoint = config.getAuthorizationEndpoint();
            if (authEndpoint != null && authEndpoint.contains("/saml/")) {
                return authEndpoint;
            }
            // Derive SAML URL from OIDC endpoint (for tests using IdentityCenterOidcConfiguration)
            return authEndpoint.replace("/oauth2/authorize", "/saml/SSO");
        } else {
            // External OIDC provider - try to derive SAML URL
            return config.getAuthorizationEndpoint().replace("/oauth2/authorize", "/saml/SSO");
        }
    }

    /**
     * Gets the SAML metadata URL for automatic IdP configuration.
     */
    private String getSamlMetadataUrl(OidcConfiguration config) {
        String providerType = config.getProviderType();
        if ("cognito".equals(providerType) || "cognito-saml".equals(providerType)) {
            // Cognito provides SAML metadata at this URL (via Keycloak for cognito-saml)
            return config.getIssuerUrl() + "/saml2/idp/metadata";
        } else if ("identity-center-saml".equals(providerType)) {
            // Identity Center SAML metadata URL
            // If tokenEndpoint already contains SAML path (set by ApplicationOidcFactory),
            // use it directly. Otherwise, derive from OIDC endpoint.
            String tokenEndpoint = config.getTokenEndpoint();
            if (tokenEndpoint != null && tokenEndpoint.contains("/saml/")) {
                return tokenEndpoint;
            }
            // Derive SAML metadata URL from OIDC endpoint
            String authEndpoint = config.getAuthorizationEndpoint();
            return authEndpoint.replace("/oauth2/authorize", "/saml/metadata");
        } else {
            // External OIDC provider - try to derive metadata URL
            return config.getAuthorizationEndpoint().replace("/oauth2/authorize", "/saml/metadata");
        }
    }

    /**
     * Gets the username attribute based on provider type.
     */
    private String getUsernameAttribute(OidcConfiguration config) {
        if (config.getProviderType().equals("cognito")) {
            // Cognito uses preferred_username or custom attribute
            return "preferred_username";
        } else {
            // Identity Center uses preferred_username
            return "preferred_username";
        }
    }

    // ==================== SAML Certificate Configuration ====================

    @Override
    public String getSamlCertificateMountPath() {
        return SAML_MOUNT_PATH;
    }

    @Override
    public String getSamlCertificateFilePath() {
        return SAML_MOUNT_PATH + "/idp.crt";
    }

    @Override
    public String getSamlCertificateEnvVar() {
        return "MM_SAMLSETTINGS_IDPCERTIFICATEFILE";
    }
}
