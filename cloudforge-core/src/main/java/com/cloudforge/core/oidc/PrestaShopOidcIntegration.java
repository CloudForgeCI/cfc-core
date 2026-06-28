package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for PrestaShop using OAuth modules.
 *
 * <p>PrestaShop supports OIDC through third-party modules available
 * on the PrestaShop Addons marketplace. This integration configures
 * the environment for OAuth/OIDC authentication.</p>
 *
 * <h2>Module Options:</h2>
 * <ul>
 *   <li>Social Login modules from PrestaShop Addons</li>
 *   <li>Custom OAuth module integration</li>
 *   <li>ALB-level OIDC (recommended)</li>
 * </ul>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class PrestaShopOidcIntegration implements OidcIntegration {

    private static final String CALLBACK_PATH = "/module/oauth/callback";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "OAuth Social Login Module / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        env.put("PS_OIDC_CLIENT_ID", config.getClientId());
        env.put("PS_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("PS_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("PS_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("PS_OIDC_LOGOUT_URL", config.getLogoutEndpoint());
        env.put("PS_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        return """
            <?php
            /**
             * CloudForge OIDC Configuration for PrestaShop
             * Auto-generated - Do not edit manually
             */

            return [
                'client_id' => getenv('PS_OIDC_CLIENT_ID'),
                'client_secret' => getenv('PS_OIDC_CLIENT_SECRET'),
                'authorize_url' => getenv('PS_OIDC_AUTHORIZE_URL'),
                'token_url' => getenv('PS_OIDC_TOKEN_URL'),
                'userinfo_url' => getenv('PS_OIDC_USERINFO_URL'),
                'scope' => getenv('PS_OIDC_SCOPE') ?: 'openid email profile',
            ];
            """;
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/config/cloudforge-oidc.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure PrestaShop OIDC");
        commands.add("echo 'Configuring PrestaShop OIDC...' >> /var/log/userdata.log");

        // Create OIDC configuration file
        commands.add("cat > /var/www/html/config/cloudforge-oidc.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("/**");
        commands.add(" * CloudForge OIDC Configuration for PrestaShop");
        commands.add(" */");
        commands.add("");
        commands.add("return [");
        commands.add("    'enabled' => true,");
        commands.add("    'client_id' => getenv('PS_OIDC_CLIENT_ID'),");
        commands.add("    'client_secret' => getenv('PS_OIDC_CLIENT_SECRET'),");
        commands.add("    'authorize_url' => getenv('PS_OIDC_AUTHORIZE_URL'),");
        commands.add("    'token_url' => getenv('PS_OIDC_TOKEN_URL'),");
        commands.add("    'userinfo_url' => getenv('PS_OIDC_USERINFO_URL'),");
        commands.add("    'scope' => getenv('PS_OIDC_SCOPE') ?: 'openid email profile',");
        commands.add("];");
        commands.add("OIDC_EOF");

        commands.add("chown apache:apache /var/www/html/config/cloudforge-oidc.php");
        commands.add("chmod 644 /var/www/html/config/cloudforge-oidc.php");

        commands.add("echo 'PrestaShop OIDC configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
            PrestaShop OIDC Integration Setup
            ==================================

            PrestaShop supports OIDC through third-party modules or ALB-level
            authentication.

            Option 1: ALB OIDC (Recommended)
            --------------------------------
            Use ALB-level OIDC authentication for simplest integration:
            - Configure ALB listener rules with Cognito authentication
            - User identity passed via HTTP headers
            - No PrestaShop module required

            Option 2: OAuth Module
            ----------------------
            Install an OAuth module from PrestaShop Addons marketplace:
            1. Download a social login / OAuth module
            2. Install via Back Office > Modules
            3. Configure with environment variables:
               - PS_OIDC_CLIENT_ID: OAuth client ID
               - PS_OIDC_CLIENT_SECRET: OAuth client secret
               - PS_OIDC_AUTHORIZE_URL: Authorization endpoint
               - PS_OIDC_TOKEN_URL: Token endpoint
               - PS_OIDC_USERINFO_URL: UserInfo endpoint

            Environment Variables:
            - PS_OIDC_CLIENT_ID: OAuth client ID
            - PS_OIDC_CLIENT_SECRET: OAuth client secret (from Secrets Manager)
            - PS_OIDC_AUTHORIZE_URL: Authorization endpoint
            - PS_OIDC_TOKEN_URL: Token endpoint
            - PS_OIDC_USERINFO_URL: UserInfo endpoint

            Admin Authentication:
            For admin/back office authentication, consider using ALB OIDC
            with path-based rules for /admin* paths.

            Troubleshooting:
            - Check var/logs for errors
            - Verify callback URL is registered in Cognito
            - Clear PrestaShop cache after configuration changes
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
        return true; // ALB OIDC is actually the recommended approach for PrestaShop
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
