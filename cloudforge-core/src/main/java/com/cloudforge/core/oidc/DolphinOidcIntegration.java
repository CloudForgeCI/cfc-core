package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Dolphin/UNA via OAuth module / ALB OIDC.
 *
 * <p>Dolphin Pro / UNA Community supports OAuth via built-in social login modules.
 * Authentication is primarily handled at the ALB layer via Cognito.</p>
 *
 * @since 3.2.0
 */
public class DolphinOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "UNA OAuth Module / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("DOLPHIN_OIDC_CLIENT_ID", config.getClientId());
        env.put("DOLPHIN_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("DOLPHIN_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("DOLPHIN_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("DOLPHIN_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# Dolphin/UNA uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/callback.php";
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
