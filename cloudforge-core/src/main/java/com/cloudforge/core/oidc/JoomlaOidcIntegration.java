package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Joomla using miniOrange plugin.
 *
 * <p>This integration uses the miniOrange OAuth/OIDC plugin
 * to enable OIDC authentication with AWS Cognito or other providers.</p>
 *
 * <h2>Plugin Details:</h2>
 * <ul>
 *   <li>Name: miniOrange OAuth Client</li>
 *   <li>URL: https://extensions.joomla.org</li>
 *   <li>Supports: OAuth 2.0, OpenID Connect</li>
 * </ul>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class JoomlaOidcIntegration implements OidcIntegration {

    private static final String CALLBACK_PATH = "/index.php?option=com_miniorange_oauth&task=callback";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "miniOrange OAuth Client Plugin";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        env.put("JOOMLA_OIDC_CLIENT_ID", config.getClientId());
        env.put("JOOMLA_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("JOOMLA_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("JOOMLA_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("JOOMLA_OIDC_LOGOUT_URL", config.getLogoutEndpoint());
        env.put("JOOMLA_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        return """
            <?php
            /**
             * CloudForge OIDC Configuration for Joomla
             * Auto-generated - Do not edit manually
             */
            defined('_JEXEC') or die;

            return [
                'client_id' => getenv('JOOMLA_OIDC_CLIENT_ID'),
                'client_secret' => getenv('JOOMLA_OIDC_CLIENT_SECRET'),
                'authorize_url' => getenv('JOOMLA_OIDC_AUTHORIZE_URL'),
                'token_url' => getenv('JOOMLA_OIDC_TOKEN_URL'),
                'userinfo_url' => getenv('JOOMLA_OIDC_USERINFO_URL'),
                'scope' => getenv('JOOMLA_OIDC_SCOPE') ?: 'openid email profile',
            ];
            """;
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/configuration_oidc.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure Joomla OIDC");
        commands.add("echo 'Configuring Joomla OIDC...' >> /var/log/userdata.log");

        // Create OIDC configuration file
        commands.add("cat > /var/www/html/configuration_oidc.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("/**");
        commands.add(" * CloudForge OIDC Configuration for Joomla");
        commands.add(" */");
        commands.add("defined('_JEXEC') or die;");
        commands.add("");
        commands.add("class CloudForgeOidcConfig {");
        commands.add("    public static function getConfig() {");
        commands.add("        return [");
        commands.add("            'client_id' => getenv('JOOMLA_OIDC_CLIENT_ID'),");
        commands.add("            'client_secret' => getenv('JOOMLA_OIDC_CLIENT_SECRET'),");
        commands.add("            'authorize_url' => getenv('JOOMLA_OIDC_AUTHORIZE_URL'),");
        commands.add("            'token_url' => getenv('JOOMLA_OIDC_TOKEN_URL'),");
        commands.add("            'userinfo_url' => getenv('JOOMLA_OIDC_USERINFO_URL'),");
        commands.add("        ];");
        commands.add("    }");
        commands.add("}");
        commands.add("OIDC_EOF");

        commands.add("chown apache:apache /var/www/html/configuration_oidc.php");
        commands.add("chmod 644 /var/www/html/configuration_oidc.php");

        commands.add("echo 'Joomla OIDC configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
            Joomla OIDC Integration Setup
            =============================

            The miniOrange OAuth Client plugin must be installed from the
            Joomla Extensions Directory.

            Installation Steps:
            1. Download the plugin from extensions.joomla.org
            2. Install via Extensions > Manage > Install
            3. Enable the plugin at Extensions > Plugins

            Configuration:
            1. Navigate to Components > miniOrange OAuth
            2. Add a new OAuth provider with:
               - Client ID: From environment variable JOOMLA_OIDC_CLIENT_ID
               - Client Secret: From Secrets Manager
               - Authorization URL: From JOOMLA_OIDC_AUTHORIZE_URL
               - Token URL: From JOOMLA_OIDC_TOKEN_URL
               - UserInfo URL: From JOOMLA_OIDC_USERINFO_URL

            Environment Variables:
            - JOOMLA_OIDC_CLIENT_ID: OAuth client ID
            - JOOMLA_OIDC_CLIENT_SECRET: OAuth client secret
            - JOOMLA_OIDC_AUTHORIZE_URL: Authorization endpoint
            - JOOMLA_OIDC_TOKEN_URL: Token endpoint
            - JOOMLA_OIDC_USERINFO_URL: UserInfo endpoint

            Testing:
            1. Clear Joomla cache: System > Clear Cache
            2. Navigate to login page
            3. Click "Login with SSO" button
            4. Verify redirect to Cognito and back

            Troubleshooting:
            - Check administrator/logs for errors
            - Verify callback URL is registered in Cognito
            - Ensure proper permissions on configuration files
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
