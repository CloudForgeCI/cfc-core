package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Mattermost using GitLab OAuth provider.
 *
 * <p><strong>Why Use OIDC:</strong></p>
 * <ul>
 *   <li>Simpler configuration - no certificate management</li>
 *   <li>Works directly with AWS Cognito User Pools</li>
 *   <li>Standard OAuth 2.0 / OpenID Connect flow</li>
 *   <li>Automatic user provisioning on first login</li>
 * </ul>
 *
 * <p><strong>Limitations vs SAML:</strong></p>
 * <ul>
 *   <li>No automatic group synchronization from IdP</li>
 *   <li>No AD/LDAP sync integration</li>
 *   <li>Manual team/channel membership management required</li>
 * </ul>
 *
 * <p><strong>Implementation Note:</strong></p>
 * <p>Mattermost doesn't have native generic OIDC support. Instead, we use the "GitLab" OAuth provider
 * which is actually a generic OIDC implementation. The MM_GITLABSETTINGS_* environment variables
 * configure generic OIDC endpoints.</p>
 *
 * <p><strong>Supported Providers:</strong></p>
 * <ul>
 *   <li>Amazon Cognito User Pools (oidcProvider: "cognito")</li>
 *   <li>Any OIDC-compliant provider</li>
 * </ul>
 *
 * @see <a href="https://docs.mattermost.com/onboard/sso-gitlab.html">Mattermost GitLab SSO (OIDC)</a>
 */
public class MattermostOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "OpenID Connect via GitLab OAuth provider (configured via MM_GITLABSETTINGS_* environment variables)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        // Mattermost uses "GitLab" OAuth provider for generic OIDC
        env.put("MM_GITLABSETTINGS_ENABLE", "true");
        env.put("MM_GITLABSETTINGS_ID", config.getClientId());

        // Client secret is injected by ContainerFactory as MM_GITLABSETTINGS_SECRET
        // from ECS secrets (MATTERMOST_OIDC_CLIENT_SECRET mapped to MM_GITLABSETTINGS_SECRET)
        // DO NOT set it here - it will be added as an ECS secret by ContainerFactory

        // OIDC endpoints
        env.put("MM_GITLABSETTINGS_AUTHENDPOINT", config.getAuthorizationEndpoint());
        env.put("MM_GITLABSETTINGS_TOKENENDPOINT", config.getTokenEndpoint());
        env.put("MM_GITLABSETTINGS_USERAPIENDPOINT", config.getUserInfoEndpoint());

        // Site URL (required for OAuth redirects)
        String siteUrl = getEffectiveSiteUrl(config);
        env.put("MM_SERVICESETTINGS_SITEURL", siteUrl);

        // Scopes - openid is required, profile and email provide user info
        env.put("MM_GITLABSETTINGS_SCOPE", "openid profile email");

        // Login button customization
        String buttonText = "Sign in with AWS Cognito";
        if (config.getProviderType() != null) {
            if (config.getProviderType().equals("identity-center")) {
                buttonText = "Sign in with AWS IAM Identity Center";
            }
        }
        env.put("MM_GITLABSETTINGS_BUTTONTEXT", buttonText);
        env.put("MM_GITLABSETTINGS_BUTTONCOLOR", "#FF9900"); // AWS orange color

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
        // Mattermost uses GitLab OAuth provider for generic OIDC
        // The callback path is /signup/gitlab/complete
        return "/signup/gitlab/complete";
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
            String callbackPath = "/signup/gitlab/complete";
            if (redirectUrl.endsWith(callbackPath)) {
                return redirectUrl.substring(0, redirectUrl.length() - callbackPath.length());
            }
        }

        return redirectUrl;
    }
}
