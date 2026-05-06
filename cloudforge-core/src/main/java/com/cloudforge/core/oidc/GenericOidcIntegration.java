package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic OIDC integration for PHP platforms without specific plugins.
 *
 * <p>This integration provides a base implementation for platforms that
 * either use ALB-level OIDC or have minimal application-level OIDC support.
 * It can be customized per platform via constructor parameters.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * return new GenericOidcIntegration("myplatform", "/oauth/callback");
 * }</pre>
 *
 * @since 3.1.0
 * @see OidcIntegration
 */
public class GenericOidcIntegration implements OidcIntegration {

    private final String platformId;
    private final String callbackPath;

    /**
     * Creates a generic OIDC integration for the specified platform.
     *
     * @param platformId the platform identifier (e.g., "opencart", "sylius")
     * @param callbackPath the OIDC callback path (e.g., "/oauth/callback")
     */
    public GenericOidcIntegration(String platformId, String callbackPath) {
        this.platformId = platformId;
        this.callbackPath = callbackPath;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Generic OAuth/OIDC / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        String prefix = platformId.toUpperCase().replace("-", "_");

        env.put(prefix + "_OIDC_CLIENT_ID", config.getClientId());
        env.put(prefix + "_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put(prefix + "_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put(prefix + "_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put(prefix + "_OIDC_LOGOUT_URL", config.getLogoutEndpoint());
        env.put(prefix + "_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        String prefix = platformId.toUpperCase().replace("-", "_");
        return String.format("""
            <?php
            /**
             * CloudForge OIDC Configuration for %s
             * Auto-generated - Do not edit manually
             */

            return [
                'enabled' => true,
                'client_id' => getenv('%s_OIDC_CLIENT_ID'),
                'client_secret' => getenv('%s_OIDC_CLIENT_SECRET'),
                'authorize_url' => getenv('%s_OIDC_AUTHORIZE_URL'),
                'token_url' => getenv('%s_OIDC_TOKEN_URL'),
                'userinfo_url' => getenv('%s_OIDC_USERINFO_URL'),
                'scope' => getenv('%s_OIDC_SCOPE') ?: 'openid email profile',
            ];
            """, platformId, prefix, prefix, prefix, prefix, prefix, prefix);
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/www/html/config/cloudforge-oidc.php";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();
        String prefix = platformId.toUpperCase().replace("-", "_");

        commands.add("# Configure " + platformId + " OIDC");
        commands.add("echo 'Configuring " + platformId + " OIDC...' >> /var/log/userdata.log");

        commands.add("mkdir -p /var/www/html/config");
        commands.add("cat > /var/www/html/config/cloudforge-oidc.php << 'OIDC_EOF'");
        commands.add("<?php");
        commands.add("/**");
        commands.add(" * CloudForge OIDC Configuration for " + platformId);
        commands.add(" */");
        commands.add("");
        commands.add("return [");
        commands.add("    'enabled' => true,");
        commands.add("    'client_id' => getenv('" + prefix + "_OIDC_CLIENT_ID'),");
        commands.add("    'client_secret' => getenv('" + prefix + "_OIDC_CLIENT_SECRET'),");
        commands.add("    'authorize_url' => getenv('" + prefix + "_OIDC_AUTHORIZE_URL'),");
        commands.add("    'token_url' => getenv('" + prefix + "_OIDC_TOKEN_URL'),");
        commands.add("    'userinfo_url' => getenv('" + prefix + "_OIDC_USERINFO_URL'),");
        commands.add("];");
        commands.add("OIDC_EOF");

        commands.add("chown www-data:www-data /var/www/html/config/cloudforge-oidc.php 2>/dev/null || true");
        commands.add("chmod 644 /var/www/html/config/cloudforge-oidc.php");

        commands.add("echo '" + platformId + " OIDC configured' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getPostDeploymentInstructions() {
        return String.format("""
            %s OIDC Integration Setup
            %s

            This platform uses a generic OIDC configuration. For application-level
            OIDC, you may need to install a compatible OAuth/OIDC plugin or module.

            Recommended Approach: ALB OIDC
            -------------------------------
            The simplest integration is ALB-level OIDC authentication:
            - Configure ALB listener rules with Cognito authentication
            - User identity passed via HTTP headers (x-amzn-oidc-*)
            - No application plugin required

            Environment Variables:
            - %s_OIDC_CLIENT_ID: OAuth client ID
            - %s_OIDC_CLIENT_SECRET: OAuth client secret (from Secrets Manager)
            - %s_OIDC_AUTHORIZE_URL: Authorization endpoint
            - %s_OIDC_TOKEN_URL: Token endpoint
            - %s_OIDC_USERINFO_URL: UserInfo endpoint

            Configuration File:
            - Location: /var/www/html/config/cloudforge-oidc.php
            - Contains environment-based OIDC settings

            For Application-Level OIDC:
            1. Check the platform's extension marketplace for OAuth plugins
            2. Install and configure with the environment variables above
            3. Register the callback URL in Cognito: https://your-domain%s

            Troubleshooting:
            - Check application logs for authentication errors
            - Verify callback URL is registered in Cognito/IdP
            - Ensure HTTPS is properly configured
            """,
            platformId.substring(0, 1).toUpperCase() + platformId.substring(1),
            "=".repeat(platformId.length() + 24),
            platformId.toUpperCase().replace("-", "_"),
            platformId.toUpperCase().replace("-", "_"),
            platformId.toUpperCase().replace("-", "_"),
            platformId.toUpperCase().replace("-", "_"),
            platformId.toUpperCase().replace("-", "_"),
            callbackPath);
    }

    @Override
    public String getContainerStartupCommand() {
        return "/usr/local/bin/docker-php-entrypoint php-fpm";
    }

    @Override
    public String getOidcCallbackPath() {
        return callbackPath;
    }

    @Override
    public boolean isDistroless() {
        return false;
    }

    @Override
    public boolean supportsAlbOidc() {
        return true; // ALB OIDC is always the fallback option
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

    /**
     * Returns the platform identifier.
     *
     * @return platform ID
     */
    public String getPlatformId() {
        return platformId;
    }
}
