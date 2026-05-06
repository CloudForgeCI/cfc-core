package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Mattermost Team Edition using GitLab OAuth provider.
 *
 * <p><strong>Why GitLab OAuth (vs Native OIDC):</strong></p>
 * <ul>
 *   <li>Works with Mattermost Team Edition (free)</li>
 *   <li>No enterprise license required</li>
 *   <li>Compatible with any OIDC provider (Cognito, Keycloak, etc.)</li>
 * </ul>
 *
 * <p><strong>Limitations:</strong></p>
 * <ul>
 *   <li>No single logout support (logging out of Mattermost doesn't log out of Cognito)</li>
 *   <li>Manual endpoint configuration (no discovery endpoint)</li>
 *   <li>No automatic group synchronization from IdP</li>
 * </ul>
 *
 * <p><strong>For Enterprise/Professional:</strong></p>
 * <p>Use {@link MattermostOidcIntegration} instead for native OIDC with
 * discovery endpoint and single logout support.</p>
 *
 * @see MattermostOidcIntegration
 * @see <a href="https://docs.mattermost.com/onboard/sso-gitlab.html">Mattermost GitLab SSO</a>
 */
public class MattermostGitLabOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "GitLab OAuth (configured via MM_GITLABSETTINGS_* environment variables)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        // Use GitLab OAuth provider (works with Team Edition - free)
        env.put("MM_GITLABSETTINGS_ENABLE", "true");
        env.put("MM_GITLABSETTINGS_ID", config.getClientId());

        // Client secret is injected by ContainerFactory as MM_GITLABSETTINGS_SECRET
        // from ECS secrets
        // DO NOT set it here - it will be added as an ECS secret by ContainerFactory

        // OIDC endpoints (manual configuration - no discovery)
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
            if ("identity-center-saml".equals(config.getProviderType())) {
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
        commands.add("# Mattermost GitLab OAuth configured via environment variables");
        commands.add("echo 'Mattermost GitLab OAuth integration active' >> /var/log/userdata.log");
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
        // GitLab OAuth uses /signup/gitlab/complete
        return "/signup/gitlab/complete";
    }

    @Override
    public boolean supportsCognito() {
        // Full support for Cognito OIDC via GitLab OAuth provider
        return true;
    }

    @Override
    public boolean supportsIdentityCenterSaml() {
        // This is OIDC integration, not SAML
        return false;
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
                Mattermost Team Edition OIDC Integration Completed
                ==================================================

                1. Access Mattermost at your configured domain
                2. Click "Sign in with AWS Cognito" on the login page
                3. You will be redirected to Cognito for authentication
                4. After authentication, a Mattermost account will be created automatically

                User Management:
                - Users are auto-created on first OIDC login
                - Email addresses from Cognito are used for Mattermost accounts
                - Team membership must be managed manually (no automatic group sync)

                ⚠️ Note: GitLab OAuth Limitation
                - Logging out of Mattermost does NOT log out of Cognito
                - Users remain authenticated with Cognito until session expires
                - For single logout support, upgrade to Enterprise and use native OIDC

                Granting Admin Privileges:
                Via Mattermost CLI:
                  docker exec -it <mattermost-container> mattermost user --email user@example.com --system-admin

                Or via System Console:
                  System Console > User Management > Users > [Select User] > Make System Admin
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
