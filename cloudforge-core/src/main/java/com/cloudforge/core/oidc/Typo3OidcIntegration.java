package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for TYPO3 via typo3/cms-openid extension / ALB OIDC.
 *
 * <p>TYPO3 supports OpenID Connect via the OIDC extension (typo3/cms-openid or
 * waldhacker/typo3-oidc). These env vars configure the extension. Authentication
 * may also be handled at the ALB layer via Cognito.</p>
 *
 * @since 3.2.0
 */
public class Typo3OidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "TYPO3 OIDC Extension / ALB OIDC";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new HashMap<>();
        env.put("TYPO3_OIDC_CLIENT_ID", config.getClientId());
        env.put("TYPO3_OIDC_AUTHORIZE_URL", config.getAuthorizationEndpoint());
        env.put("TYPO3_OIDC_TOKEN_URL", config.getTokenEndpoint());
        env.put("TYPO3_OIDC_USERINFO_URL", config.getUserInfoEndpoint());
        env.put("TYPO3_OIDC_SCOPE", config.getScopes() != null ? config.getScopes() : "openid email profile");
        return env;
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        return List.of("# TYPO3 uses ALB-level OIDC — no in-app configuration required");
    }

    @Override
    public String getOidcCallbackPath() {
        return "/?type=1404";
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
