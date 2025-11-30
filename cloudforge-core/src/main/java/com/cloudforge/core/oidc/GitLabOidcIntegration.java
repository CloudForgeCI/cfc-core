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
        // GitLab uses gitlab.rb configuration file instead of environment variables
        // Return empty map
        return new HashMap<>();
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        StringBuilder gitlabConfig = new StringBuilder();
        gitlabConfig.append("# GitLab OIDC Configuration\n");
        gitlabConfig.append("# Added by CloudForge\n");
        gitlabConfig.append("\n");

        // Configure external URL if available (required for OAuth redirects, clone URLs, webhooks)
        String externalUrl = config.getApplicationUrl();
        if (externalUrl != null && !externalUrl.isEmpty()) {
            gitlabConfig.append("# Configure GitLab external URL for reverse proxy\n");
            gitlabConfig.append(String.format("external_url '%s'\n", externalUrl));
            gitlabConfig.append("\n");
        }

        gitlabConfig.append("gitlab_rails['omniauth_enabled'] = true\n");
        gitlabConfig.append("gitlab_rails['omniauth_allow_single_sign_on'] = ['openid_connect']\n");
        gitlabConfig.append("gitlab_rails['omniauth_block_auto_created_users'] = false\n");
        gitlabConfig.append("gitlab_rails['omniauth_auto_link_user'] = ['openid_connect']\n");
        gitlabConfig.append("\n");
        gitlabConfig.append("gitlab_rails['omniauth_providers'] = [\n");
        gitlabConfig.append("  {\n");
        gitlabConfig.append("    name: 'openid_connect',\n");
        gitlabConfig.append(String.format("    label: '%s',\n",
            config.getProviderType().equals("cognito") ? "AWS Cognito" : "AWS IAM Identity Center"));
        gitlabConfig.append("    args: {\n");
        gitlabConfig.append("      name: 'openid_connect',\n");
        gitlabConfig.append("      scope: ['openid', 'profile', 'email'],\n");
        gitlabConfig.append("      response_type: 'code',\n");
        gitlabConfig.append(String.format("      issuer: '%s',\n", config.getIssuerUrl()));
        gitlabConfig.append("      discovery: true,\n");
        gitlabConfig.append("      client_auth_method: 'query',\n");
        gitlabConfig.append(String.format("      uid_field: '%s',\n", config.getUsernameClaim()));
        gitlabConfig.append("      send_scope_to_token_endpoint: true,\n");
        gitlabConfig.append(String.format("      pkce: %s,\n", config.usePkce()));
        gitlabConfig.append("      client_options: {\n");
        gitlabConfig.append(String.format("        identifier: '%s',\n", config.getClientId()));
        gitlabConfig.append("        secret: '${GITLAB_OIDC_CLIENT_SECRET}',\n");
        gitlabConfig.append(String.format("        redirect_uri: '%s'\n", config.getRedirectUrl()));
        gitlabConfig.append("      }\n");
        gitlabConfig.append("    }\n");
        gitlabConfig.append("  }\n");
        gitlabConfig.append("]\n");

        return gitlabConfig.toString();
    }

    @Override
    public String getConfigurationFilePath() {
        return "/etc/gitlab/gitlab.rb";
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
        commands.add("    label: '" + (config.getProviderType().equals("cognito") ? "AWS Cognito" : "AWS IAM Identity Center") + "',");
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
        return "/assets/wrapper";
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
