package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Mattermost using native OpenID Connect.
 *
 * <p><strong>Why Use Native OIDC (vs GitLab OAuth):</strong></p>
 * <ul>
 *   <li>Uses discovery endpoint for automatic configuration</li>
 *   <li>Proper single logout support via end_session_endpoint</li>
 *   <li>Standard OpenID Connect 1.0 compliance</li>
 *   <li>Works directly with AWS Cognito User Pools</li>
 * </ul>
 *
 * <p><strong>Limitations vs SAML:</strong></p>
 * <ul>
 *   <li>No automatic group synchronization from IdP</li>
 *   <li>No AD/LDAP sync integration</li>
 *   <li>Manual team/channel membership management required</li>
 * </ul>
 *
 * <p><strong>License Requirement:</strong></p>
 * <p>Native OpenID Connect requires Mattermost Enterprise or Professional.
 * For Team Edition (free), use GitLab OAuth instead.</p>
 *
 * <p><strong>Supported Providers:</strong></p>
 * <ul>
 *   <li>Amazon Cognito User Pools (oidcProvider: "cognito")</li>
 *   <li>Any OIDC-compliant provider with discovery endpoint</li>
 * </ul>
 *
 * @see <a href="https://docs.mattermost.com/onboard/sso-openidconnect.html">Mattermost OpenID Connect SSO</a>
 */
public class MattermostOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Native OpenID Connect (configured via MM_OPENIDSETTINGS_* environment variables)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        // Use native OpenID Connect settings (requires Enterprise/Professional license)
        env.put("MM_OPENIDSETTINGS_ENABLE", "true");
        env.put("MM_OPENIDSETTINGS_ID", config.getClientId());

        // Client secret is injected by ContainerFactory as MM_OPENIDSETTINGS_SECRET
        // from ECS secrets (MATTERMOST_OIDC_CLIENT_SECRET mapped to MM_OPENIDSETTINGS_SECRET)
        // DO NOT set it here - it will be added as an ECS secret by ContainerFactory

        // Discovery endpoint for automatic OIDC configuration
        // Cognito: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/openid-configuration
        String issuer = config.getIssuerUrl();
        if (issuer != null && !issuer.isEmpty()) {
            String discoveryEndpoint = issuer.endsWith("/")
                ? issuer + ".well-known/openid-configuration"
                : issuer + "/.well-known/openid-configuration";
            env.put("MM_OPENIDSETTINGS_DISCOVERYENDPOINT", discoveryEndpoint);
        }

        // Site URL (required for OAuth redirects)
        String siteUrl = getEffectiveSiteUrl(config);
        env.put("MM_SERVICESETTINGS_SITEURL", siteUrl);

        // Scopes - openid is required, profile and email provide user info
        env.put("MM_OPENIDSETTINGS_SCOPE", "openid profile email");

        // Login button customization
        String buttonText = "Sign in with AWS Cognito";
        if (config.getProviderType() != null) {
            if (config.getProviderType().equals("identity-center")) {
                buttonText = "Sign in with AWS IAM Identity Center";
            }
        }
        env.put("MM_OPENIDSETTINGS_BUTTONTEXT", buttonText);
        env.put("MM_OPENIDSETTINGS_BUTTONCOLOR", "#FF9900"); // AWS orange color

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        // Mattermost uses environment variables, not config files
        return null;
    }

    @Override
    public String getConfigurationFilePath() {
        // Not used - configuration via environment variables
        return null;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();
        commands.add("# Mattermost OIDC configured via environment variables");
        commands.add("echo 'Mattermost OIDC integration active' >> /var/log/userdata.log");
        return commands;
    }

    @Override
    public String getContainerStartupCommand() {
        // Mattermost uses a Go binary, not a shell script
        // The official image is distroless (no /bin/sh)
        return "/mattermost/bin/mattermost";
    }

    @Override
    public String getOidcCallbackPath() {
        // Native OpenID Connect uses /signup/openid/complete
        return "/signup/openid/complete";
    }

    @Override
    public boolean supportsCognito() {
        // Full support for Cognito OIDC
        return true;
    }

    @Override
    public boolean supportsIdentityCenterSaml() {
        // This is OIDC integration, not SAML
        // Identity Center supports OIDC, so technically yes, but for SAML use MattermostSamlIntegration
        return false;
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
                Mattermost OIDC Integration Completed
                ======================================

                1. Access Mattermost at your configured domain
                2. Click "Sign in with AWS Cognito" on the login page
                3. You will be redirected to Cognito for authentication
                4. After authentication, a Mattermost account will be created automatically

                User Management:
                - Users are auto-created on first OIDC login
                - Email addresses from Cognito are used for Mattermost accounts
                - Team membership must be managed manually (no automatic group sync)

                Granting Admin Privileges:
                Via Mattermost CLI:
                  docker exec -it <mattermost-container> mattermost user --email user@example.com --system-admin

                Or via System Console:
                  System Console > User Management > Users > [Select User] > Make System Admin

                Limitations (compared to SAML):
                - No automatic group/team synchronization
                - No AD/LDAP integration for team membership
                - Manual role management required

                For automatic group sync with SAML, consider:
                - oidcProvider: "cognito-saml" (deploys Keycloak as SAML bridge)
                - oidcProvider: "identity-center" (uses AWS IAM Identity Center SAML)
                """;
    }

    /**
     * Gets the effective site URL for Mattermost.
     * Uses applicationUrl if available, otherwise derives from redirectUrl.
     */
    private String getEffectiveSiteUrl(OidcConfiguration config) {
        String appUrl = config.getApplicationUrl();
        if (appUrl != null && !appUrl.isEmpty()) {
            return appUrl;
        }

        // Derive from redirect URL by removing the callback path
        String redirectUrl = config.getRedirectUrl();
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            String callbackPath = "/signup/openid/complete";
            if (redirectUrl.endsWith(callbackPath)) {
                return redirectUrl.substring(0, redirectUrl.length() - callbackPath.length());
            }
        }

        return redirectUrl;
    }
}
