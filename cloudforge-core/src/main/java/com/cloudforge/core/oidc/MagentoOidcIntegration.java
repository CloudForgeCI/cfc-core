package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Magento 2 using miniOrange module.
 *
 * <p>This integration uses the miniOrange OAuth/OIDC SSO module
 * to enable OIDC authentication with AWS Cognito or other providers.</p>
 *
 * <h2>Module Details:</h2>
 * <ul>
 *   <li>Name: miniOrange OAuth Single Sign-On</li>
 *   <li>Marketplace: Adobe Commerce Marketplace</li>
 *   <li>Supports: OAuth 2.0, OpenID Connect</li>
 * </ul>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class MagentoOidcIntegration implements OidcIntegration {

    private static final String CALLBACK_PATH = "/mioauth/actions/sendAuthorizationRequest";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "miniOrange OAuth/OIDC SSO Module";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();

        env.put("MAGENTO_OIDC_CLIENT_ID", config.getClientId());
        env.put("MAGENTO_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("MAGENTO_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("MAGENTO_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("MAGENTO_OIDC_LOGOUT_URL", config.getLogoutEndpoint());
        env.put("MAGENTO_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        return """
            <?php
            /**
             * CloudForge OIDC Configuration for Magento
             * Auto-generated - Do not edit manually
             *
             * Configure via Stores > Configuration > miniOrange > OAuth Client
             */
            return [
                'client_id' => getenv('MAGENTO_OIDC_CLIENT_ID'),
                'client_secret' => getenv('MAGENTO_OIDC_CLIENT_SECRET'),
                'authorize_url' => getenv('MAGENTO_OIDC_AUTHORIZE_URL'),
                'token_url' => getenv('MAGENTO_OIDC_TOKEN_URL'),
                'userinfo_url' => getenv('MAGENTO_OIDC_USERINFO_URL'),
                'scope' => getenv('MAGENTO_OIDC_SCOPE') ?: 'openid email profile',
            ];
            """;
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/app/etc/cloudforge-oidc.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Install Magento OIDC module");
        commands.add("echo 'Configuring Magento OIDC...' >> /var/log/userdata.log");

        // Note: miniOrange module requires manual installation from marketplace
        // This configures the environment for when the module is installed
        commands.add("cat > /var/www/html/app/etc/cloudforge-oidc.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("// CloudForge OIDC Configuration");
        commands.add("return [");
        commands.add("    'enabled' => true,");
        commands.add("    'provider' => 'cognito',");
        commands.add("];");
        commands.add("OIDC_EOF");

        commands.add("chown nginx:nginx /var/www/html/app/etc/cloudforge-oidc.php");
        commands.add("chmod 644 /var/www/html/app/etc/cloudforge-oidc.php");

        commands.add("echo 'Magento OIDC configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
            Magento OIDC Integration Setup
            ==============================

            The miniOrange OAuth/OIDC SSO module must be installed from the
            Adobe Commerce Marketplace.

            Installation Steps:
            1. Purchase/download the module from marketplace.magento.com
            2. Install via composer: composer require miniorange/module-oauth-sso
            3. Enable the module: bin/magento module:enable MiniOrange_OAuth
            4. Run setup: bin/magento setup:upgrade
            5. Deploy static content: bin/magento setup:static-content:deploy

            Configuration:
            1. Navigate to Stores > Configuration > miniOrange > OAuth Client
            2. Enter the following settings:
               - Client ID: From environment variable MAGENTO_OIDC_CLIENT_ID
               - Client Secret: From Secrets Manager
               - Authorization URL: From MAGENTO_OIDC_AUTHORIZE_URL
               - Token URL: From MAGENTO_OIDC_TOKEN_URL
               - UserInfo URL: From MAGENTO_OIDC_USERINFO_URL

            Testing:
            1. Clear Magento cache: bin/magento cache:clean
            2. Navigate to customer login page
            3. Click "Login with SSO" button
            4. Verify redirect to Cognito and back

            Troubleshooting:
            - Check var/log/system.log for errors
            - Verify callback URL is registered in Cognito
            - Ensure HTTPS is properly configured
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
