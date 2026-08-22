package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for SuiteCRM via built-in OAuth / ALB OIDC.
 *
 * <p>SuiteCRM 8.x includes built-in OAuth 2.0 / OIDC support. These env vars
 * configure the built-in OIDC provider. Authentication may also be handled at
 * the ALB layer via Cognito.</p>
 *
 * @since 3.2.0
 */
public class SuiteCrmOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "SuiteCRM Built-in OAuth 2.0 / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("SUITECRM_OIDC_CLIENT_ID", config.getClientId());
        env.put("SUITECRM_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("SUITECRM_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("SUITECRM_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("SUITECRM_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# SuiteCRM uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/index.php?module=OAuthClients&action=OAuthCallback";
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
    public boolean supportsApplicationOidc() {
        return false;
    }
}
