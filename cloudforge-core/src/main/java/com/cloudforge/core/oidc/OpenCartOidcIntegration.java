package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for OpenCart via ALB-level authentication.
 *
 * <p>OpenCart lacks a mature native OIDC plugin. Authentication is handled at
 * the ALB layer via Cognito. These env vars expose endpoint info for any
 * custom OAuth extension that may be installed.</p>
 *
 * @since 3.2.0
 */
public class OpenCartOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "ALB OIDC / Custom OAuth Extension";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("OPENCART_OIDC_CLIENT_ID", config.getClientId());
        env.put("OPENCART_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("OPENCART_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("OPENCART_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("OPENCART_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# OpenCart uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/index.php?route=extension/module/oauth/callback";
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
