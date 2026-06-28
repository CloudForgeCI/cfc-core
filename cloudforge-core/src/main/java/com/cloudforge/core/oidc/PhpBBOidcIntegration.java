package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for phpBB via OAuth extension / ALB OIDC.
 *
 * <p>phpBB supports OAuth via the phpBB OAuth extension. Authentication is
 * primarily handled at the ALB layer via Cognito. These env vars expose
 * endpoint info for custom OAuth extension configuration.</p>
 *
 * @since 3.2.0
 */
public class PhpBBOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "phpBB OAuth Extension / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("PHPBB_OIDC_CLIENT_ID", config.getClientId());
        env.put("PHPBB_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("PHPBB_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("PHPBB_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("PHPBB_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# phpBB uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/app.php/oauth";
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
