package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.manager.ManagerEnvKeys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deployment-time OIDC contract for CloudForge Manager.
 *
 * <p>The Manager server owns the authorization-code exchange and its cookie session;
 * the browser is never given a client secret. {@code ContainerFactory} supplies the
 * matching {@link ManagerEnvKeys#OIDC_CLIENT_SECRET} value from Secrets Manager.</p>
 */
public final class CloudForgeManagerOidcIntegration implements OidcIntegration {

    /** Callback handled by the Manager authorization-code endpoint. */
    public static final String CALLBACK_PATH = "/api/v1/auth/oidc/callback";
    public static final String START_PATH = "/api/v1/auth/oidc/start";

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "CloudForge Manager server-side authorization-code flow";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        Map<String, String> env = new LinkedHashMap<>();
        putIfPresent(env, ManagerEnvKeys.OIDC_ISSUER, config.getIssuerUrl());
        putIfPresent(env, ManagerEnvKeys.OIDC_AUTHORIZATION_ENDPOINT, config.getAuthorizationEndpoint());
        putIfPresent(env, ManagerEnvKeys.OIDC_TOKEN_ENDPOINT, config.getTokenEndpoint());
        putIfPresent(env, ManagerEnvKeys.OIDC_USERINFO_ENDPOINT, config.getUserInfoEndpoint());
        putIfPresent(env, ManagerEnvKeys.OIDC_JWKS_URI, config.getJwksUri());
        putIfPresent(env, ManagerEnvKeys.OIDC_CLIENT_ID, config.getClientId());
        putIfPresent(env, ManagerEnvKeys.OIDC_REDIRECT_URL, config.getRedirectUrl());
        putIfPresent(env, ManagerEnvKeys.OIDC_SCOPES, config.getScopes());
        putIfPresent(env, ManagerEnvKeys.OIDC_USERNAME_CLAIM, config.getUsernameClaim());
        putIfPresent(env, ManagerEnvKeys.OIDC_EMAIL_CLAIM, config.getEmailClaim());
        putIfPresent(env, ManagerEnvKeys.OIDC_GROUPS_CLAIM, config.getGroupsClaim());
        putIfPresent(env, ManagerEnvKeys.OIDC_ADMIN_GROUP, config.getAdminGroupName());
        putIfPresent(env, ManagerEnvKeys.OIDC_MANAGER_GROUP, config.getDeveloperGroupName());
        return env;
    }

    private static void putIfPresent(Map<String, String> environment, String key, String value) {
        if (value != null && !value.isBlank()) {
            environment.put(key, value);
        }
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        // EC2 container startup receives the same environment through its launch configuration.
        return List.of();
    }

    @Override
    public String getOidcCallbackPath() {
        return CALLBACK_PATH;
    }

    /**
     * The Manager image owns its Java entrypoint. OIDC is configured solely through
     * environment variables, so a generic shell startup override would break it.
     */
    @Override
    public String getContainerStartupCommand() {
        return null;
    }

    @Override
    public String getAuthenticationType() {
        return "OIDC";
    }
}
