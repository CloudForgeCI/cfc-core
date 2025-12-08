package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SAML integration for Metabase (Pro/Enterprise editions).
 *
 * <p><strong>Important:</strong> Metabase does NOT support native OpenID Connect.
 * This integration uses SAML 2.0 which is available in Metabase Pro and Enterprise editions.
 * For open-source Metabase, use JWT-based authentication for embedding.</p>
 *
 * <p><strong>AWS Cognito as SAML IdP:</strong></p>
 * <ul>
 *   <li>Cognito User Pools can act as a SAML 2.0 Identity Provider</li>
 *   <li>Configure Cognito to issue SAML assertions to Metabase</li>
 *   <li>Metabase receives SAML assertions at /auth/sso endpoint</li>
 * </ul>
 *
 * <p><strong>IAM Identity Center as SAML IdP:</strong></p>
 * <ul>
 *   <li>Identity Center natively supports SAML 2.0</li>
 *   <li>Create a custom SAML application for Metabase</li>
 *   <li>Map attributes: email, firstName, lastName, groups</li>
 * </ul>
 *
 * <p><strong>Required Metabase Edition:</strong> Pro or Enterprise</p>
 *
 * <p><strong>SAML Attribute Requirements:</strong></p>
 * <ul>
 *   <li>email (required) - User's email address</li>
 *   <li>firstName (required) - User's first name</li>
 *   <li>lastName (required) - User's last name</li>
 *   <li>groups (optional) - For group sync</li>
 * </ul>
 *
 * @see <a href="https://www.metabase.com/docs/latest/people-and-groups/authenticating-with-saml">Metabase SAML Documentation</a>
 */
public class MetabaseSamlIntegration implements OidcIntegration {

    private static final String SAML_MOUNT_PATH = "/metabase/saml";

    @Override
    public boolean isSupported() {
        // SAML is only available in Metabase Pro/Enterprise
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "SAML 2.0 authentication (Metabase Pro/Enterprise required). " +
               "Configured via MB_SAML_* environment variables. " +
               "Note: Metabase does not support native OIDC - uses SAML instead.";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        // Enable SAML
        env.put("MB_SAML_ENABLED", "true");

        // Identity Provider configuration
        // For Cognito: Need to configure Cognito as SAML IdP
        // For Identity Center: Use the SAML metadata URL
        String samlIdpUri = getSamlIdpUri(config);
        env.put("MB_SAML_IDENTITY_PROVIDER_URI", samlIdpUri);

        // SAML attribute mappings
        // Standard SAML attributes that Cognito/Identity Center provide
        env.put("MB_SAML_ATTRIBUTE_EMAIL", "email");
        env.put("MB_SAML_ATTRIBUTE_FIRSTNAME", "firstName");
        env.put("MB_SAML_ATTRIBUTE_LASTNAME", "lastName");

        // Group sync configuration
        env.put("MB_SAML_GROUP_SYNC", "true");
        env.put("MB_SAML_ATTRIBUTE_GROUP", getGroupAttribute(config));

        // Application URL for SAML redirect
        String siteUrl = config.getApplicationUrl();
        if (siteUrl != null && !siteUrl.isEmpty()) {
            env.put("MB_SITE_URL", siteUrl);
        }

        // IdP certificate - placeholder for runtime injection
        // The actual certificate needs to be retrieved from Secrets Manager
        env.put("MB_SAML_IDENTITY_PROVIDER_CERTIFICATE", "${METABASE_SAML_IDP_CERTIFICATE}");

        // Optional: Configure SLO (Single Logout)
        String logoutUri = getSamlLogoutUri(config);
        if (logoutUri != null) {
            env.put("MB_SAML_IDENTITY_PROVIDER_LOGOUT_URI", logoutUri);
            // Required for SLO to work with cookies
            env.put("MB_SESSION_COOKIE_SAMESITE", "none");
        }

        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure Metabase SAML integration");
        commands.add("# NOTE: Metabase does not support native OIDC - using SAML 2.0 instead");
        commands.add("# Requires Metabase Pro or Enterprise edition");
        commands.add("");

        // Retrieve SAML IdP certificate from Secrets Manager
        commands.add("# Retrieve SAML IdP certificate from AWS Secrets Manager");
        commands.add("echo 'Retrieving SAML IdP certificate...' >> /var/log/userdata.log");
        commands.add("export METABASE_SAML_IDP_CERTIFICATE=$(aws secretsmanager get-secret-value \\");
        commands.add("  --secret-id " + config.getClientSecretArn() + " \\");
        commands.add("  --query SecretString --output text 2>/var/log/userdata.log)");
        commands.add("");
        commands.add("if [ -z \"$METABASE_SAML_IDP_CERTIFICATE\" ]; then");
        commands.add("  echo 'WARNING: Failed to retrieve SAML IdP certificate' >> /var/log/userdata.log");
        commands.add("  echo 'SAML authentication will not be available' >> /var/log/userdata.log");
        commands.add("fi");
        commands.add("");

        // Create environment file for Metabase SAML config
        commands.add("# Create Metabase SAML configuration file");
        commands.add("cat > /opt/metabase/metabase-saml-env.sh <<'EOF'");
        commands.add("export MB_SAML_ENABLED=true");

        String samlIdpUri = getSamlIdpUri(config);
        commands.add("export MB_SAML_IDENTITY_PROVIDER_URI=\"" + samlIdpUri + "\"");

        // SAML attribute mappings
        commands.add("export MB_SAML_ATTRIBUTE_EMAIL=\"email\"");
        commands.add("export MB_SAML_ATTRIBUTE_FIRSTNAME=\"firstName\"");
        commands.add("export MB_SAML_ATTRIBUTE_LASTNAME=\"lastName\"");

        // Group sync
        commands.add("export MB_SAML_GROUP_SYNC=true");
        commands.add("export MB_SAML_ATTRIBUTE_GROUP=\"" + getGroupAttribute(config) + "\"");

        // Site URL for redirects
        String siteUrl = config.getApplicationUrl();
        if (siteUrl != null && !siteUrl.isEmpty()) {
            commands.add("export MB_SITE_URL=\"" + siteUrl + "\"");
        }

        commands.add("EOF");
        commands.add("");

        // Add certificate to env file
        commands.add("# Add IdP certificate to configuration");
        commands.add("echo \"export MB_SAML_IDENTITY_PROVIDER_CERTIFICATE=\\\"$METABASE_SAML_IDP_CERTIFICATE\\\"\" >> /opt/metabase/metabase-saml-env.sh");
        commands.add("");

        // Source the environment and restart Metabase
        commands.add("# Apply SAML configuration");
        commands.add("chmod 600 /opt/metabase/metabase-saml-env.sh");
        commands.add("source /opt/metabase/metabase-saml-env.sh");
        commands.add("");

        // Restart container with SAML config
        commands.add("# Restart Metabase container with SAML configuration");
        commands.add("docker stop metabase 2>/dev/null || true");
        commands.add("docker rm metabase 2>/dev/null || true");
        commands.add("");

        commands.add("echo 'Metabase SAML integration configured' >> /var/log/userdata.log");
        commands.add("echo 'IMPORTANT: Metabase Pro/Enterprise edition required for SAML' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getContainerStartupCommand() {
        return "/app/run_metabase.sh";
    }

    @Override
    public boolean supportsCognito() {
        // Cognito requires manual SAML setup in Cognito console
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
                Metabase SAML Integration
                =========================

                IMPORTANT: SAML authentication requires Metabase Pro or Enterprise edition.
                Open-source Metabase does not support SAML - use JWT for embedding instead.

                Prerequisites:
                1. Metabase Pro or Enterprise license
                2. AWS Cognito configured as SAML IdP, or
                3. IAM Identity Center with custom SAML application

                Cognito SAML Setup:
                1. In Cognito User Pool, enable "SAML" under "App Integration"
                2. Download the SAML metadata from Cognito
                3. Configure Metabase with the IdP URI and certificate

                Identity Center SAML Setup:
                1. Create a custom SAML 2.0 application in Identity Center
                2. Set ACS URL to: https://{your-metabase}/auth/sso
                3. Map attributes: email, firstName, lastName
                4. Assign users/groups to the application

                Metabase Configuration:
                1. Access Metabase at: https://{your-domain}:3000
                2. Go to Admin > Settings > Authentication
                3. Enable SAML and verify settings
                4. Test SSO login

                SAML Redirect URL: https://{your-metabase}/auth/sso

                Group Mapping:
                - Configure group mappings in Admin > People > Groups
                - Map SAML groups to Metabase groups for permissions
                """;
    }

    /**
     * Gets the SAML Identity Provider URI based on the OIDC configuration.
     *
     * <p>For Cognito: Uses the Cognito SAML IdP endpoint
     * For Identity Center: Uses the Identity Center SAML SSO URL</p>
     */
    private String getSamlIdpUri(OidcConfiguration config) {
        String providerType = config.getProviderType();
        if ("cognito".equals(providerType)) {
            // Cognito SAML IdP endpoint
            // Format: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idpresponse
            // But for Metabase, we need the SSO URL from Cognito's SAML metadata
            return config.getIssuerUrl() + "/saml2/idp/SSO";
        } else if ("identity-center".equals(providerType)) {
            // Identity Center: authorizationEndpoint may already contain the SAML SSO URL
            // (set by ApplicationOidcFactory.buildIdentityCenterSamlConfiguration)
            // Or it may be an OIDC endpoint (from IdentityCenterOidcConfiguration in tests)
            String authEndpoint = config.getAuthorizationEndpoint();
            if (authEndpoint != null && authEndpoint.contains("/saml/")) {
                // Already a SAML URL
                return authEndpoint;
            }
            // Derive SAML URL from OIDC endpoint
            return authEndpoint.replace("/oauth2/authorize", "/saml/SSO");
        } else {
            // External OIDC provider - try to derive SAML URL
            return config.getAuthorizationEndpoint().replace("/oauth2/authorize", "/saml/SSO");
        }
    }

    /**
     * Gets the SAML logout URI for Single Logout (SLO).
     */
    private String getSamlLogoutUri(OidcConfiguration config) {
        String providerType = config.getProviderType();
        if ("cognito".equals(providerType)) {
            return config.getIssuerUrl() + "/saml2/logout";
        } else if ("identity-center".equals(providerType)) {
            // Identity Center doesn't have a standard SAML logout endpoint via API
            // Return null to skip SLO configuration
            return null;
        } else {
            return config.getAuthorizationEndpoint().replace("/oauth2/authorize", "/saml/logout");
        }
    }

    /**
     * Gets the SAML group attribute name based on provider type.
     */
    private String getGroupAttribute(OidcConfiguration config) {
        if (config.getProviderType().equals("cognito")) {
            // Cognito uses custom attributes for groups in SAML
            return "custom:groups";
        } else {
            // Identity Center uses standard groups attribute
            return "groups";
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
        return "MB_SAML_IDENTITY_PROVIDER_CERTIFICATE";
    }
}
