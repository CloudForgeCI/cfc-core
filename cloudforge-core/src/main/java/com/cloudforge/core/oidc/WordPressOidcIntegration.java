package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for WordPress using OpenID Connect Generic plugin.
 *
 * <p>This integration uses the popular "OpenID Connect Generic" plugin
 * (daggerhart-openid-connect-generic) to enable OIDC authentication
 * with AWS Cognito or other OIDC providers.</p>
 *
 * <h2>Plugin Details:</h2>
 * <ul>
 *   <li>Name: OpenID Connect Generic Client</li>
 *   <li>Slug: daggerhart-openid-connect-generic</li>
 *   <li>URL: https://wordpress.org/plugins/daggerhart-openid-connect-generic/</li>
 *   <li>GitHub: https://github.com/oidc-wp/openid-connect-generic</li>
 * </ul>
 *
 * <h2>Configuration:</h2>
 * <p>The plugin can be configured via:</p>
 * <ul>
 *   <li>WordPress admin: Settings → OpenID Connect Client</li>
 *   <li>Environment variables (preferred for automation)</li>
 *   <li>wp-config.php defines</li>
 * </ul>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class WordPressOidcIntegration implements OidcIntegration {

    private static final String PLUGIN_SLUG = "daggerhart-openid-connect-generic";
    private static final String CALLBACK_PATH = "/wp-admin/admin-ajax.php?action=openid-connect-authorize";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "OpenID Connect Generic Plugin (daggerhart-openid-connect-generic)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        // OIDC endpoints
        env.put("OIDC_CLIENT_ID", config.getClientId());
        env.put("OIDC_ENDPOINT_LOGIN_URL", config.getAuthorizationEndpoint());
        env.put("OIDC_ENDPOINT_TOKEN_URL", config.getTokenEndpoint());
        env.put("OIDC_ENDPOINT_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("OIDC_ENDPOINT_END_SESSION_URL", config.getLogoutEndpoint());

        // Scopes
        env.put("OIDC_CLIENT_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");

        // Identity claim
        env.put("OIDC_IDENTITY_KEY", config.getUsernameClaim() != null ? config.getUsernameClaim() : "email");

        // Link existing users
        env.put("OIDC_LINK_EXISTING_USERS", "1");

        // Create new users if they don't exist
        env.put("OIDC_CREATE_IF_DOES_NOT_EXIST", "1");

        // Redirect after login
        env.put("OIDC_REDIRECT_USER_BACK", "1");

        // Login button text
        env.put("OIDC_LOGIN_BUTTON_TEXT", "Login with SSO");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        // WordPress OIDC plugin uses wp-config.php defines
        StringBuilder wpConfig = new StringBuilder();
        wpConfig.append("// CloudForge OIDC Configuration - OpenID Connect Generic Plugin\n");
        wpConfig.append("// Auto-generated - Do not edit manually\n\n");

        // Define constants for the plugin
        wpConfig.append("define('OIDC_CLIENT_ID', getenv('OIDC_CLIENT_ID'));\n");
        wpConfig.append("define('OIDC_CLIENT_SECRET', getenv('OIDC_CLIENT_SECRET'));\n");
        wpConfig.append("define('OIDC_ENDPOINT_LOGIN_URL', getenv('OIDC_ENDPOINT_LOGIN_URL'));\n");
        wpConfig.append("define('OIDC_ENDPOINT_TOKEN_URL', getenv('OIDC_ENDPOINT_TOKEN_URL'));\n");
        wpConfig.append("define('OIDC_ENDPOINT_USERINFO_URL', getenv('OIDC_ENDPOINT_USERINFO_URL'));\n");
        wpConfig.append("define('OIDC_ENDPOINT_END_SESSION_URL', getenv('OIDC_ENDPOINT_END_SESSION_URL'));\n");
        wpConfig.append("define('OIDC_CLIENT_SCOPE', getenv('OIDC_CLIENT_SCOPE') ?: 'openid email profile');\n");
        wpConfig.append("define('OIDC_IDENTITY_KEY', getenv('OIDC_IDENTITY_KEY') ?: 'email');\n");
        wpConfig.append("define('OIDC_LINK_EXISTING_USERS', true);\n");
        wpConfig.append("define('OIDC_CREATE_IF_DOES_NOT_EXIST', true);\n");
        wpConfig.append("define('OIDC_REDIRECT_USER_BACK', true);\n");
        wpConfig.append("define('OIDC_LOGIN_BUTTON_TEXT', 'Login with SSO');\n");

        return wpConfig.toString();
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/wp-content/mu-plugins/cloudforge-oidc-config.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Install WordPress OIDC plugin");
        commands.add("echo 'Installing OpenID Connect Generic plugin...' >> /var/log/userdata.log");

        // Install the OIDC plugin
        commands.add("wp plugin install " + PLUGIN_SLUG + " --activate --allow-root");

        // Create mu-plugins directory if it doesn't exist
        commands.add("mkdir -p /var/www/html/wp-content/mu-plugins");

        // Create OIDC configuration as mu-plugin (always loaded)
        commands.add("cat > /var/www/html/wp-content/mu-plugins/cloudforge-oidc-config.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("/**");
        commands.add(" * CloudForge OIDC Configuration");
        commands.add(" * Auto-generated - Do not edit manually");
        commands.add(" */");
        commands.add("");
        commands.add("// OIDC settings via environment variables");
        commands.add("add_filter('openid-connect-generic-settings', function($settings) {");
        commands.add("    $settings['client_id'] = getenv('OIDC_CLIENT_ID');");
        commands.add("    $settings['client_secret'] = getenv('OIDC_CLIENT_SECRET');");
        commands.add("    $settings['endpoint_login'] = getenv('OIDC_ENDPOINT_LOGIN_URL');");
        commands.add("    $settings['endpoint_token'] = getenv('OIDC_ENDPOINT_TOKEN_URL');");
        commands.add("    $settings['endpoint_userinfo'] = getenv('OIDC_ENDPOINT_USERINFO_URL');");
        commands.add("    $settings['endpoint_end_session'] = getenv('OIDC_ENDPOINT_END_SESSION_URL');");
        commands.add("    $settings['scope'] = getenv('OIDC_CLIENT_SCOPE') ?: 'openid email profile';");
        commands.add("    $settings['identity_key'] = getenv('OIDC_IDENTITY_KEY') ?: 'email';");
        commands.add("    $settings['link_existing_users'] = true;");
        commands.add("    $settings['create_if_does_not_exist'] = true;");
        commands.add("    $settings['redirect_user_back'] = true;");
        commands.add("    $settings['login_button_text'] = 'Login with SSO';");
        commands.add("    return $settings;");
        commands.add("});");
        commands.add("OIDC_EOF");

        // Set proper permissions
        commands.add("chown www-data:www-data /var/www/html/wp-content/mu-plugins/cloudforge-oidc-config.php");
        commands.add("chmod 644 /var/www/html/wp-content/mu-plugins/cloudforge-oidc-config.php");

        commands.add("echo 'WordPress OIDC plugin configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
            WordPress OIDC Integration Setup
            =================================

            The OpenID Connect Generic plugin has been installed and configured.

            Configuration is managed via environment variables:
            - OIDC_CLIENT_ID: OAuth client ID
            - OIDC_CLIENT_SECRET: OAuth client secret (from Secrets Manager)
            - OIDC_ENDPOINT_LOGIN_URL: Authorization endpoint
            - OIDC_ENDPOINT_TOKEN_URL: Token endpoint
            - OIDC_ENDPOINT_USERINFO_URL: UserInfo endpoint
            - OIDC_ENDPOINT_END_SESSION_URL: Logout endpoint

            To verify setup:
            1. Navigate to WordPress admin: Settings → OpenID Connect Client
            2. Verify settings are populated from environment variables
            3. Test login by logging out and clicking "Login with SSO"

            User provisioning:
            - New users are created automatically on first login
            - Existing users are linked by email address
            - Default role: Subscriber (can be changed in plugin settings)

            Plugin documentation:
            https://github.com/oidc-wp/openid-connect-generic

            Troubleshooting:
            - Check /var/log/nginx/error.log for PHP errors
            - Enable WordPress debug mode in wp-config.php
            - Verify callback URL is registered in Cognito/IdP
            """;
    }

    @Override
    public String getContainerStartupCommand() {
        return "/usr/local/bin/docker-entrypoint.sh";
    }

    @Override
    public String getOidcCallbackPath() {
        return CALLBACK_PATH;
    }

    @Override
    public boolean isDistroless() {
        return false;
    }

    @Override
    public boolean supportsAlbOidc() {
        return true;
    }

    @Override
    public boolean supportsCognito() {
        return true;
    }

    @Override
    public boolean supportsIdentityCenterSaml() {
        return false; // WordPress OIDC plugin doesn't support SAML directly
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }
}
