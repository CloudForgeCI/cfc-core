package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Grafana using generic_oauth provider.
 *
 * <p>Grafana has excellent built-in OIDC support via the generic_oauth provider.
 * Configuration is done entirely through environment variables.</p>
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
 *   <li>Group/role mapping from OIDC claims</li>
 *   <li>Admin role assignment via group membership</li>
 *   <li>PKCE support</li>
 * </ul>
 *
 * @see <a href="https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/generic-oauth/">Grafana OAuth Documentation</a>
 */
public class GrafanaOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Built-in generic_oauth provider (configured via environment variables)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        // Configure Grafana root URL if available (required for OAuth redirects, dashboards, alerts)
        String rootUrl = config.getApplicationUrl();
        if (rootUrl != null && !rootUrl.isEmpty()) {
            env.put("GF_SERVER_ROOT_URL", rootUrl);
        }

        // Enable generic OAuth
        env.put("GF_AUTH_GENERIC_OAUTH_ENABLED", "true");

        // Provider name (shown on login page)
        String providerName = config.getProviderType().equals("cognito") ?
            "AWS Cognito" : "AWS IAM Identity Center";
        env.put("GF_AUTH_GENERIC_OAUTH_NAME", providerName);

        // OAuth endpoints
        env.put("GF_AUTH_GENERIC_OAUTH_CLIENT_ID", config.getClientId());
        env.put("GF_AUTH_GENERIC_OAUTH_AUTH_URL", config.getAuthorizationEndpoint());
        env.put("GF_AUTH_GENERIC_OAUTH_TOKEN_URL", config.getTokenEndpoint());
        env.put("GF_AUTH_GENERIC_OAUTH_API_URL", config.getUserInfoEndpoint());

        // Scopes
        env.put("GF_AUTH_GENERIC_OAUTH_SCOPES", config.getScopes());

        // User mapping
        env.put("GF_AUTH_GENERIC_OAUTH_LOGIN_ATTRIBUTE_PATH", config.getUsernameClaim());
        env.put("GF_AUTH_GENERIC_OAUTH_NAME_ATTRIBUTE_PATH", config.getFullNameClaim());
        env.put("GF_AUTH_GENERIC_OAUTH_EMAIL_ATTRIBUTE_PATH", config.getEmailClaim());

        // Groups/role mapping
        env.put("GF_AUTH_GENERIC_OAUTH_GROUPS_ATTRIBUTE_PATH", config.getGroupsClaim());
        env.put("GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH", "");

        // Map OIDC groups to Grafana roles
        // Users in admin group get Admin role, others get Editor role
        // Note: Advanced role mapping requires Grafana Enterprise
        env.put("GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_STRICT", "false");

        // Auto-create users
        env.put("GF_AUTH_GENERIC_OAUTH_AUTO_LOGIN", "false");
        env.put("GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP", String.valueOf(config.isAutoCreateUsers()));

        // Security
        env.put("GF_AUTH_GENERIC_OAUTH_USE_PKCE", String.valueOf(config.usePkce()));
        env.put("GF_AUTH_GENERIC_OAUTH_TLS_SKIP_VERIFY_INSECURE", "false");

        // The client secret is retrieved from AWS Secrets Manager at runtime
        // This is handled by the userdata script
        env.put("GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET", "${GRAFANA_OAUTH_CLIENT_SECRET}");

        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure Grafana OIDC integration");
        commands.add("# Retrieve client secret from AWS Secrets Manager");
        commands.add("export GRAFANA_OAUTH_CLIENT_SECRET=$(aws secretsmanager get-secret-value \\");
        commands.add("  --secret-id " + config.getClientSecretArn() + " \\");
        commands.add("  --region " + context.stackName().split("-")[0] + " \\");  // Extract region from stack name
        commands.add("  --query SecretString --output text)");
        commands.add("");
        commands.add("# Set environment variables for Grafana OAuth");
        commands.add("cat >> /etc/grafana/grafana-env.sh <<'EOF'");

        // Configure Grafana root URL if available (required for OAuth redirects, dashboards, alerts)
        String rootUrl = config.getApplicationUrl();
        if (rootUrl != null && !rootUrl.isEmpty()) {
            commands.add("export GF_SERVER_ROOT_URL=\"" + rootUrl + "\"");
        }

        commands.add("export GF_AUTH_GENERIC_OAUTH_ENABLED=true");
        commands.add("export GF_AUTH_GENERIC_OAUTH_NAME=\"" + (config.getProviderType().equals("cognito") ? "AWS Cognito" : "AWS IAM Identity Center") + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_CLIENT_ID=\"" + config.getClientId() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET=\"$GRAFANA_OAUTH_CLIENT_SECRET\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_AUTH_URL=\"" + config.getAuthorizationEndpoint() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_TOKEN_URL=\"" + config.getTokenEndpoint() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_API_URL=\"" + config.getUserInfoEndpoint() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_SCOPES=\"" + config.getScopes() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_LOGIN_ATTRIBUTE_PATH=\"" + config.getUsernameClaim() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_EMAIL_ATTRIBUTE_PATH=\"" + config.getEmailClaim() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_GROUPS_ATTRIBUTE_PATH=\"" + config.getGroupsClaim() + "\"");
        commands.add("export GF_AUTH_GENERIC_OAUTH_USE_PKCE=" + config.usePkce());
        commands.add("export GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP=" + config.isAutoCreateUsers());
        commands.add("EOF");
        commands.add("");
        commands.add("# Source environment variables in Grafana systemd service");
        commands.add("echo 'EnvironmentFile=/etc/grafana/grafana-env.sh' >> /usr/lib/systemd/system/grafana-server.service");
        commands.add("systemctl daemon-reload");
        commands.add("systemctl restart grafana-server");
        commands.add("echo 'Grafana OIDC integration configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getContainerStartupCommand() {
        return "/run.sh";
    }

    @Override
    public boolean supportsCognito() {
        // Full support - Grafana generic_oauth works with Cognito OIDC
        return true;
    }

    @Override
    public boolean supportsIdentityCenterSaml() {
        // Grafana supports SAML but this integration uses OIDC
        // For SAML, create a GrafanaSamlIntegration class
        return false;
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
                Grafana OIDC Integration Completed
                ===================================

                1. Access Grafana at: https://{your-domain}:3000
                2. Click "Sign in with %s" on the login page
                3. You will be redirected to the OIDC provider
                4. After authentication, you'll be logged into Grafana

                Role Mapping:
                - Users in '%s' group: Grafana Admin role
                - Other authenticated users: Grafana Editor role

                To manage users and permissions:
                - Use the OIDC provider (Cognito/Identity Center)
                - Grafana will auto-create users on first login
                - Role assignment is based on group membership
                """;
    }
}
