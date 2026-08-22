package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Sylius via Symfony Security Bundle / ALB OIDC.
 *
 * <p>Sylius (Symfony-based) supports OIDC through the Symfony Security Bundle
 * with a KnpU OAuth2 provider. Authentication is primarily handled at the ALB
 * layer via Cognito for simplicity.</p>
 *
 * @since 3.2.0
 */
public class SyliusOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "Symfony Security Bundle OAuth2 / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("SYLIUS_OIDC_CLIENT_ID", config.getClientId());
        env.put("SYLIUS_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("SYLIUS_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("SYLIUS_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("SYLIUS_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# Sylius uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/oauth2/callback";
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
