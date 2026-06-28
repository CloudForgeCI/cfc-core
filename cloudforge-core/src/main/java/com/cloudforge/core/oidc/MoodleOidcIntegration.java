package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Moodle using the Microsoft OpenID Connect plugin.
 *
 * <p>Moodle has excellent OIDC support through the official Microsoft
 * auth_oidc plugin, which works with any OIDC provider (not just Microsoft).</p>
 *
 * <h2>Plugin Details:</h2>
 * <ul>
 *   <li>Name: OpenID Connect (auth_oidc)</li>
 *   <li>URL: https://moodle.org/plugins/auth_oidc</li>
 *   <li>GitHub: https://github.com/microsoft/moodle-auth_oidc</li>
 *   <li>Supports: Microsoft Entra ID, Cognito, Generic OIDC</li>
 * </ul>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class MoodleOidcIntegration implements OidcIntegration {

    private static final String CALLBACK_PATH = "/auth/oidc/";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Microsoft OpenID Connect Plugin (auth_oidc)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        env.put("MOODLE_OIDC_IDP_TYPE", "other"); // generic OIDC
        env.put("MOODLE_OIDC_CLIENT_ID", config.getClientId());
        env.put("MOODLE_OIDC_AUTH_ENDPOINT", config.getAuthorizationEndpoint());
        env.put("MOODLE_OIDC_TOKEN_ENDPOINT", config.getTokenEndpoint());
        env.put("MOODLE_OIDC_USERINFO_ENDPOINT", config.getUserInfoEndpoint());
        env.put("MOODLE_OIDC_LOGOUT_ENDPOINT", config.getLogoutEndpoint());
        env.put("MOODLE_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        return """
            <?php
            /**
             * CloudForge OIDC Configuration for Moodle
             * Auto-generated - Do not edit manually
             *
             * Include this in config.php or use CLI to configure
             */

            // OIDC plugin settings (auth_oidc)
            $CFG->auth_oidc_idptype = 'other'; // Generic OIDC provider
            $CFG->auth_oidc_clientid = getenv('MOODLE_OIDC_CLIENT_ID');
            $CFG->auth_oidc_clientsecret = getenv('MOODLE_OIDC_CLIENT_SECRET');
            $CFG->auth_oidc_authendpoint = getenv('MOODLE_OIDC_AUTH_ENDPOINT');
            $CFG->auth_oidc_tokenendpoint = getenv('MOODLE_OIDC_TOKEN_ENDPOINT');
            $CFG->auth_oidc_oidcresource = getenv('MOODLE_OIDC_USERINFO_ENDPOINT');
            $CFG->auth_oidc_scope = getenv('MOODLE_OIDC_SCOPE') ?: 'openid email profile';

            // Auto-create users
            $CFG->auth_oidc_createaccountonlogin = 1;

            // Link existing users by email
            $CFG->auth_oidc_linkexistingusers = 1;
            """;
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/cloudforge-oidc-config.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Install Moodle OIDC plugin");
        commands.add("echo 'Installing Moodle OIDC plugin...' >> /var/log/userdata.log");

        // Download and install the auth_oidc plugin
        commands.add("cd /var/www/html/auth");
        commands.add("if [ ! -d oidc ]; then");
        commands.add("    git clone https://github.com/microsoft/moodle-auth_oidc.git oidc");
        commands.add("    chown -R apache:apache oidc");
        commands.add("    echo 'OIDC plugin downloaded' >> /var/log/userdata.log");
        commands.add("fi");

        // Create config include
        commands.add("cat > /var/www/html/cloudforge-oidc-config.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("// CloudForge OIDC Configuration");
        commands.add("$CFG->auth_oidc_idptype = 'other';");
        commands.add("$CFG->auth_oidc_clientid = getenv('MOODLE_OIDC_CLIENT_ID');");
        commands.add("$CFG->auth_oidc_clientsecret = getenv('MOODLE_OIDC_CLIENT_SECRET');");
        commands.add("$CFG->auth_oidc_authendpoint = getenv('MOODLE_OIDC_AUTH_ENDPOINT');");
        commands.add("$CFG->auth_oidc_tokenendpoint = getenv('MOODLE_OIDC_TOKEN_ENDPOINT');");
        commands.add("$CFG->auth_oidc_oidcresource = getenv('MOODLE_OIDC_USERINFO_ENDPOINT');");
        commands.add("OIDC_EOF");

        commands.add("chown apache:apache /var/www/html/cloudforge-oidc-config.php");
        commands.add("chmod 644 /var/www/html/cloudforge-oidc-config.php");

        // Add include to config.php if it exists
        commands.add("if [ -f /var/www/html/config.php ]; then");
        commands.add("    if ! grep -q 'cloudforge-oidc-config.php' /var/www/html/config.php; then");
        commands.add("        echo \"require_once(__DIR__ . '/cloudforge-oidc-config.php');\" >> /var/www/html/config.php");
        commands.add("    fi");
        commands.add("fi");

        commands.add("echo 'Moodle OIDC plugin configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
            Moodle OIDC Integration Setup
            =============================

            The Microsoft OpenID Connect plugin (auth_oidc) has been installed.
            This plugin works with AWS Cognito and other OIDC providers.

            Configuration via Environment Variables:
            - MOODLE_OIDC_CLIENT_ID: OAuth client ID
            - MOODLE_OIDC_CLIENT_SECRET: OAuth client secret (from Secrets Manager)
            - MOODLE_OIDC_AUTH_ENDPOINT: Authorization endpoint
            - MOODLE_OIDC_TOKEN_ENDPOINT: Token endpoint
            - MOODLE_OIDC_USERINFO_ENDPOINT: UserInfo endpoint

            Manual Configuration Steps:
            1. Log into Moodle as admin
            2. Navigate to Site administration > Plugins > Authentication
            3. Enable "OpenID Connect" authentication
            4. Click "Settings" for OpenID Connect
            5. Configure:
               - Identity Provider Type: Other
               - Client ID/Secret: From environment variables
               - Endpoints: From environment variables

            User Provisioning:
            - New users created automatically on first login
            - Existing users can be linked by email
            - User role mapping available via plugin settings

            Testing:
            1. Navigate to the Moodle login page
            2. Click "OpenID Connect" login button
            3. Verify redirect to Cognito and successful login

            Plugin Documentation:
            https://github.com/microsoft/moodle-auth_oidc

            Troubleshooting:
            - Check Site administration > Reports > Logs
            - Enable debugging in config.php
            - Verify callback URL in Cognito: https://your-moodle/auth/oidc/
            """;
    }

    @Override
    public String getContainerStartupCommand() {
        return "/usr/local/bin/docker-php-entrypoint apache2-foreground";
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
        return false;
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }
}
