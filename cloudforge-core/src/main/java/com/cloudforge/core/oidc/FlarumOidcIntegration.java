package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Flarum via FoF OAuth extension / ALB OIDC.
 *
 * <p>Flarum supports OIDC through the FoF (Friends of Flarum) OAuth extension.
 * Authentication is primarily handled at the ALB layer via Cognito.</p>
 *
 * @since 3.2.0
 */
public class FlarumOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "FoF OAuth Extension / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("FLARUM_OIDC_CLIENT_ID", config.getClientId());
        env.put("FLARUM_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("FLARUM_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("FLARUM_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("FLARUM_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# Flarum uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/auth/callback";
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
