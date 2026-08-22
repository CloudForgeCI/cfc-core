package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Drupal using the native OpenID Connect module.
 *
 * <p>Drupal has excellent native support for OpenID Connect through
 * the contributed openid_connect module. This is one of the most
 * mature OIDC implementations in the PHP CMS ecosystem.</p>
 *
 * <h2>Module Details:</h2>
 * <ul>
 *   <li>Name: OpenID Connect / OAuth client</li>
 *   <li>URL: https://www.drupal.org/project/openid_connect</li>
 *   <li>Supports: OAuth 2.0, OpenID Connect, Multiple IdPs</li>
 * </ul>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class DrupalOidcIntegration implements OidcIntegration {

    private static final String CALLBACK_PATH = "/openid-connect/generic";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Native OpenID Connect Module (openid_connect)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        env.put("DRUPAL_OIDC_CLIENT_ID", config.getClientId());
        env.put("DRUPAL_OIDC_AUTHORIZATION_ENDPOINT", config.getAuthorizationEndpoint());
        env.put("DRUPAL_OIDC_TOKEN_ENDPOINT", config.getTokenEndpoint());
        env.put("DRUPAL_OIDC_USERINFO_ENDPOINT", config.getUserInfoEndpoint());
        env.put("DRUPAL_OIDC_END_SESSION_ENDPOINT", config.getLogoutEndpoint());
        env.put("DRUPAL_OIDC_SCOPES", config.getScopes() != null ? config.getScopes() : "openid email profile");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        return """
            <?php
            /**
             * CloudForge OIDC Configuration for Drupal
             * Auto-generated - Do not edit manually
             *
             * This file is included from settings.php
             */

            // OpenID Connect module settings
            $config['openid_connect.settings.generic'] = [
              'enabled' => TRUE,
              'settings' => [
                'client_id' => getenv('DRUPAL_OIDC_CLIENT_ID'),
                'client_secret' => getenv('DRUPAL_OIDC_CLIENT_SECRET'),
                'authorization_endpoint' => getenv('DRUPAL_OIDC_AUTHORIZATION_ENDPOINT'),
                'token_endpoint' => getenv('DRUPAL_OIDC_TOKEN_ENDPOINT'),
                'userinfo_endpoint' => getenv('DRUPAL_OIDC_USERINFO_ENDPOINT'),
                'end_session_endpoint' => getenv('DRUPAL_OIDC_END_SESSION_ENDPOINT'),
                'scopes' => explode(' ', getenv('DRUPAL_OIDC_SCOPES') ?: 'openid email profile'),
              ],
            ];

            // General OpenID Connect settings
            $config['openid_connect.settings'] = [
              'always_save_userinfo' => TRUE,
              'connect_existing_users' => TRUE,
              'override_registration_settings' => TRUE,
              'userinfo_mappings' => [
                'name' => 'preferred_username',
                'mail' => 'email',
              ],
            ];
            """;
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/sites/default/cloudforge-oidc.settings.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Install Drupal OIDC module");
        commands.add("echo 'Installing OpenID Connect module...' >> /var/log/userdata.log");

        // Install the OpenID Connect module via composer
        commands.add("cd /var/www/html && composer require drupal/openid_connect --no-interaction");

        // Enable the module via drush
        commands.add("drush -r /var/www/html en openid_connect -y");

        // Create OIDC configuration file
        commands.add("cat > /var/www/html/sites/default/cloudforge-oidc.settings.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("/**");
        commands.add(" * CloudForge OIDC Configuration");
        commands.add(" * Auto-generated - Do not edit manually");
        commands.add(" */");
        commands.add("");
        commands.add("$config['openid_connect.settings.generic'] = [");
        commands.add("  'enabled' => TRUE,");
        commands.add("  'settings' => [");
        commands.add("    'client_id' => getenv('DRUPAL_OIDC_CLIENT_ID'),");
        commands.add("    'client_secret' => getenv('DRUPAL_OIDC_CLIENT_SECRET'),");
        commands.add("    'authorization_endpoint' => getenv('DRUPAL_OIDC_AUTHORIZATION_ENDPOINT'),");
        commands.add("    'token_endpoint' => getenv('DRUPAL_OIDC_TOKEN_ENDPOINT'),");
        commands.add("    'userinfo_endpoint' => getenv('DRUPAL_OIDC_USERINFO_ENDPOINT'),");
        commands.add("    'end_session_endpoint' => getenv('DRUPAL_OIDC_END_SESSION_ENDPOINT'),");
        commands.add("  ],");
        commands.add("];");
        commands.add("OIDC_EOF");

        // Include the OIDC config in settings.php
        commands.add("echo \"\\nif (file_exists(__DIR__ . '/cloudforge-oidc.settings.php')) {\" >> /var/www/html/sites/default/settings.php");
        commands.add("echo \"  include __DIR__ . '/cloudforge-oidc.settings.php';\" >> /var/www/html/sites/default/settings.php");
        commands.add("echo \"}\" >> /var/www/html/sites/default/settings.php");

        // Set permissions
        commands.add("chown nginx:nginx /var/www/html/sites/default/cloudforge-oidc.settings.php");
        commands.add("chmod 644 /var/www/html/sites/default/cloudforge-oidc.settings.php");

        // Clear Drupal cache
        commands.add("drush -r /var/www/html cr");

        commands.add("echo 'Drupal OIDC module configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
            Drupal OIDC Integration Setup
            =============================

            The OpenID Connect module has been installed and configured.

            Configuration is managed via environment variables:
            - DRUPAL_OIDC_CLIENT_ID: OAuth client ID
            - DRUPAL_OIDC_CLIENT_SECRET: OAuth client secret (from Secrets Manager)
            - DRUPAL_OIDC_AUTHORIZATION_ENDPOINT: Authorization endpoint
            - DRUPAL_OIDC_TOKEN_ENDPOINT: Token endpoint
            - DRUPAL_OIDC_USERINFO_ENDPOINT: UserInfo endpoint
            - DRUPAL_OIDC_END_SESSION_ENDPOINT: Logout endpoint

            To verify setup:
            1. Navigate to Drupal admin: /admin/config/people/openid-connect
            2. Verify the "Generic OAuth 2.0" client is enabled
            3. Test login by visiting /user/login and clicking the OIDC button

            User provisioning:
            - New users are created automatically on first login
            - Existing users are linked by email address
            - User roles can be mapped via the module's role mapping feature

            Module documentation:
            https://www.drupal.org/docs/contributed-modules/openid-connect

            Troubleshooting:
            - Check /admin/reports/dblog for authentication errors
            - Enable verbose logging in module settings
            - Verify callback URL is registered in Cognito/IdP
            """;
    }

    @Override
    public String getContainerStartupCommand() {
        return "/usr/local/bin/docker-php-entrypoint php-fpm";
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
