package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for MyBB via OAuth plugin / ALB OIDC.
 *
 * <p>MyBB supports OAuth through community plugins. Authentication is
 * primarily handled at the ALB layer via Cognito for production deployments.</p>
 *
 * @since 3.2.0
 */
public class MyBBOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "MyBB OAuth Plugin / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("MYBB_OIDC_CLIENT_ID", config.getClientId());
        env.put("MYBB_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("MYBB_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("MYBB_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("MYBB_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# MyBB uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/index.php?action=login_callback";
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
