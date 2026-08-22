package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for GitLab using OmniAuth OpenID Connect.
 *
 * <p>GitLab has built-in OIDC support through the OmniAuth framework.
 * Configuration is done via gitlab.rb for Omnibus installations.</p>
 *
 * <p><strong>Supported OIDC Providers:</strong></p>
 * <ul>
 *   <li>Amazon Cognito</li>
 *   <li>IAM Identity Center</li>
 *   <li>Any OIDC-compliant provider</li>
 * </ul>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Auto-create users on first login</li>
 *   <li>Group synchronization from OIDC claims</li>
 *   <li>Admin role assignment</li>
 *   <li>Block external OAuth sign-ins</li>
 * </ul>
 *
 * @see <a href="https://docs.gitlab.com/ee/administration/auth/oidc.html">GitLab OIDC Documentation</a>
 */
public class GitLabOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Built-in OmniAuth OpenID Connect (configured via gitlab.rb)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        // GitLab Docker containers use GITLAB_OMNIBUS_CONFIG environment variable
        // This is the recommended way to configure GitLab in containers
        Map<String, String> envVars = new HashMap<>();

        // Build the configuration as a Ruby string that will be evaluated
        StringBuilder gitlabConfig = new StringBuilder();

        // Configure external URL if available (required for OAuth redirects, clone URLs, webhooks)
        String externalUrl = config.getApplicationUrl();
        boolean usesHttps = externalUrl != null && externalUrl.startsWith("https://");

        if (externalUrl != null && !externalUrl.isEmpty()) {
            gitlabConfig.append(String.format("external_url '%s'; ", externalUrl));
        }

        // Configure reverse proxy settings when behind ALB with HTTPS
        // This prevents redirect loops by telling GitLab to trust proxy headers
        if (usesHttps) {
            gitlabConfig.append("nginx['listen_port'] = 80; ");
            gitlabConfig.append("nginx['listen_https'] = false; ");
            gitlabConfig.append("nginx['proxy_set_headers'] = { 'X-Forwarded-Proto' => 'https', 'X-Forwarded-Ssl' => 'on' }; ");
        }

        gitlabConfig.append("gitlab_rails['omniauth_enabled'] = true; ");
        gitlabConfig.append("gitlab_rails['omniauth_allow_single_sign_on'] = ['openid_connect']; ");
        gitlabConfig.append("gitlab_rails['omniauth_block_auto_created_users'] = false; ");
        gitlabConfig.append("gitlab_rails['omniauth_auto_link_user'] = ['openid_connect']; ");

        gitlabConfig.append("gitlab_rails['omniauth_providers'] = [");
        gitlabConfig.append("{");
        gitlabConfig.append("name: 'openid_connect', ");
        gitlabConfig.append(String.format("label: '%s', ",
            "cognito".equalsIgnoreCase(config.getProviderType()) ? "AWS Cognito" : "AWS IAM Identity Center"));
        gitlabConfig.append("args: {");
        gitlabConfig.append("name: 'openid_connect', ");
        gitlabConfig.append("scope: ['openid', 'profile', 'email'], ");
        gitlabConfig.append("response_type: 'code', ");
        gitlabConfig.append(String.format("issuer: '%s', ", config.getIssuerUrl()));
        gitlabConfig.append("discovery: true, ");
        gitlabConfig.append("client_auth_method: 'query', ");
        gitlabConfig.append(String.format("uid_field: '%s', ", config.getUsernameClaim()));
        gitlabConfig.append("send_scope_to_token_endpoint: true, ");
        gitlabConfig.append(String.format("pkce: %s, ", config.usePkce()));
        gitlabConfig.append("client_options: {");
        gitlabConfig.append(String.format("identifier: '%s', ", config.getClientId()));
        gitlabConfig.append("secret: ENV['GITLAB_OIDC_CLIENT_SECRET'], ");
        gitlabConfig.append(String.format("redirect_uri: '%s'", config.getRedirectUrl()));
        gitlabConfig.append("}");
        gitlabConfig.append("}");
        gitlabConfig.append("}");
        gitlabConfig.append("];");

        envVars.put("GITLAB_OMNIBUS_CONFIG", gitlabConfig.toString());

        return envVars;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        // GitLab Docker containers use GITLAB_OMNIBUS_CONFIG environment variable
        // No config file needed - return null to skip file writing
        return null;
    }

    @Override
    public String getConfigurationFilePath() {
        // GitLab Docker containers use GITLAB_OMNIBUS_CONFIG environment variable
        // No config file path needed
        return null;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure GitLab OIDC integration");
        commands.add("# Retrieve client secret from AWS Secrets Manager");
        commands.add("export GITLAB_OIDC_CLIENT_SECRET=$(aws secretsmanager get-secret-value \\");
        commands.add("  --secret-id " + config.getClientSecretArn() + " \\");
        commands.add("  --query SecretString --output text)");
        commands.add("");

        // Create gitlab.rb configuration
        commands.add("# Append OIDC configuration to gitlab.rb");
        commands.add("cat >> /etc/gitlab/gitlab.rb <<'EOFGITLAB'");
        commands.add("");
        commands.add("# GitLab OIDC Configuration");
        commands.add("# Added by CloudForge");
        commands.add("");

        // Configure external URL if available (required for OAuth redirects, clone URLs, webhooks)
        String externalUrl = config.getApplicationUrl();
        if (externalUrl != null && !externalUrl.isEmpty()) {
            commands.add("# Configure GitLab external URL for reverse proxy");
            commands.add("external_url '" + externalUrl + "'");
            commands.add("");
        }

        commands.add("gitlab_rails['omniauth_enabled'] = true");
        commands.add("gitlab_rails['omniauth_allow_single_sign_on'] = ['openid_connect']");
        commands.add("gitlab_rails['omniauth_block_auto_created_users'] = false");
        commands.add("gitlab_rails['omniauth_auto_link_user'] = ['openid_connect']");
        commands.add("");
        commands.add("gitlab_rails['omniauth_providers'] = [");
        commands.add("  {");
        commands.add("    name: 'openid_connect',");
        commands.add("    label: '" + ("cognito".equalsIgnoreCase(config.getProviderType()) ? "AWS Cognito" : "AWS IAM Identity Center") + "',");
        commands.add("    args: {");
        commands.add("      name: 'openid_connect',");
        commands.add("      scope: ['openid', 'profile', 'email'],");
        commands.add("      response_type: 'code',");
        commands.add("      issuer: '" + config.getIssuerUrl() + "',");
        commands.add("      discovery: true,");
        commands.add("      client_auth_method: 'query',");
        commands.add("      uid_field: '" + config.getUsernameClaim() + "',");
        commands.add("      send_scope_to_token_endpoint: true,");
        commands.add("      pkce: " + config.usePkce() + ",");
        commands.add("      client_options: {");
        commands.add("        identifier: '" + config.getClientId() + "',");
        commands.add("        secret: '${GITLAB_OIDC_CLIENT_SECRET}',");
        commands.add("        redirect_uri: '" + config.getRedirectUrl() + "'");
        commands.add("      }");
        commands.add("    }");
        commands.add("  }");
        commands.add("]");
        commands.add("EOFGITLAB");
        commands.add("");

        // Replace secret placeholder with actual value
        commands.add("# Replace secret placeholder with actual value");
        commands.add("sed -i \"s|\\${GITLAB_OIDC_CLIENT_SECRET}|$GITLAB_OIDC_CLIENT_SECRET|g\" /etc/gitlab/gitlab.rb");
        commands.add("");

        // Reconfigure GitLab
        commands.add("# Reconfigure GitLab to apply changes");
        commands.add("gitlab-ctl reconfigure");
        commands.add("echo 'GitLab OIDC integration configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getContainerStartupCommand() {
        // GitLab's official omnibus container uses /assets/init-container as the default CMD
        // Per https://github.com/gitlabhq/omnibus-gitlab/blob/master/docker/Dockerfile
        // The init-container script handles initialization, signals, runit, and GitLab reconfiguration
        return "/assets/init-container";
    }

    @Override
    public boolean supportsCognito() {
        // Full support - GitLab OmniAuth works with Cognito OIDC
        return true;
    }

    @Override
    public boolean supportsIdentityCenterSaml() {
        // GitLab supports SAML (Premium/Ultimate) but this integration uses OIDC
        // For SAML, create a GitLabSamlIntegration class
        return false;
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }

    @Override
    public String getOidcCallbackPath() {
        return "/users/auth/openid_connect/callback";
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
                GitLab OIDC Integration Completed
                ==================================

                1. Access GitLab at: https://{your-domain}
                2. Click "Sign in with %s" on the login page
                3. You will be redirected to the OIDC provider
                4. After authentication, a GitLab account will be created automatically

                User Management:
                - Users are auto-created on first OIDC login
                - User accounts are linked to OIDC identity
                - To grant admin privileges, use GitLab Rails console:
                  gitlab-rails console
                  user = User.find_by(email: 'user@example.com')
                  user.admin = true
                  user.save!

                Group Synchronization:
                - Consider GitLab Group Sync for automatic role mapping
                - Requires GitLab Premium/Ultimate for SAML Group Links
                """;
    }
}
