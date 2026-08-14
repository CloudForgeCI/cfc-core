package com.cloudforge.core.manager;

/**
 * Canonical environment / system-property keys for CloudForge Manager.
 *
 * <p>Deploy adapters ({@code CloudForgeManagerOidcIntegration}, {@code DatabaseSpec} wiring)
 * and the Manager runtime (Spring Boot or otherwise) share these names so
 * DeploymentConfig → container env → process config stays one contract.</p>
 */
public final class ManagerEnvKeys {

    public static final String PORT = "CFC_MANAGER_PORT";
    public static final String BIND = "CFC_MANAGER_BIND";
    public static final String PUBLIC_URL = "CFC_MANAGER_PUBLIC_URL";
    public static final String TARGET = "CFC_MANAGER_TARGET";
    public static final String AUTH_MODE = "CFC_MANAGER_AUTH_MODE";
    public static final String DB_MODE = "CFC_MANAGER_DB_MODE";
    public static final String VERSION = "CFC_MANAGER_VERSION";
    public static final String SETUP_TOKEN = "CFC_MANAGER_SETUP_TOKEN";
    public static final String TRUST_ALB_OIDC_HEADERS = "CFC_MANAGER_TRUST_ALB_OIDC_HEADERS";

    public static final String OIDC_ISSUER = "CFC_MANAGER_OIDC_ISSUER";
    public static final String OIDC_AUTHORIZATION_ENDPOINT = "CFC_MANAGER_OIDC_AUTHORIZATION_ENDPOINT";
    public static final String OIDC_TOKEN_ENDPOINT = "CFC_MANAGER_OIDC_TOKEN_ENDPOINT";
    public static final String OIDC_USERINFO_ENDPOINT = "CFC_MANAGER_OIDC_USERINFO_ENDPOINT";
    public static final String OIDC_JWKS_URI = "CFC_MANAGER_OIDC_JWKS_URI";
    public static final String OIDC_CLIENT_ID = "CFC_MANAGER_OIDC_CLIENT_ID";
    public static final String OIDC_CLIENT_SECRET = "CFC_MANAGER_OIDC_CLIENT_SECRET";
    public static final String OIDC_REDIRECT_URL = "CFC_MANAGER_OIDC_REDIRECT_URL";
    public static final String OIDC_SCOPES = "CFC_MANAGER_OIDC_SCOPES";
    public static final String OIDC_USERNAME_CLAIM = "CFC_MANAGER_OIDC_USERNAME_CLAIM";
    public static final String OIDC_EMAIL_CLAIM = "CFC_MANAGER_OIDC_EMAIL_CLAIM";
    public static final String OIDC_GROUPS_CLAIM = "CFC_MANAGER_OIDC_GROUPS_CLAIM";
    public static final String OIDC_ADMIN_GROUP = "CFC_MANAGER_OIDC_ADMIN_GROUP";
    public static final String OIDC_MANAGER_GROUP = "CFC_MANAGER_OIDC_MANAGER_GROUP";

    public static final String DB_HOST = "CFC_MANAGER_DB_HOST";
    public static final String DB_PORT = "CFC_MANAGER_DB_PORT";
    public static final String DB_NAME = "CFC_MANAGER_DB_NAME";
    public static final String DB_USER = "CFC_MANAGER_DB_USER";
    public static final String DB_ENGINE = "CFC_MANAGER_DB_ENGINE";
    public static final String DB_PASSWORD = "CFC_MANAGER_DATABASE_PASSWORD";
    public static final String DB_REPLICA_HOST = "CFC_MANAGER_DB_REPLICA_HOST";
    public static final String DB_REPLICA_PORT = "CFC_MANAGER_DB_REPLICA_PORT";

    /** {@code memory} (default) or {@code redis} — see {@code SessionStore}'s javadoc. */
    public static final String SESSION_MODE = "CFC_MANAGER_SESSION_MODE";
    public static final String REDIS_HOST = "CFC_MANAGER_REDIS_HOST";
    public static final String REDIS_PORT = "CFC_MANAGER_REDIS_PORT";
    public static final String REDIS_AUTH_TOKEN = "CFC_MANAGER_REDIS_AUTH_TOKEN";
    public static final String REDIS_TLS = "CFC_MANAGER_REDIS_TLS";

    /** AES key for {@code SecretCipher}'s {@code AesGcmSecretCipher} (cross-account
     *  connections' external IDs) — absent means {@code PlaintextSecretCipher} (local dev only).
     *  When Manager is deployed via CloudForge's own CDK pipeline, this is injected as an ECS
     *  {@code Secret} bound to a CDK-provisioned Secrets Manager entry, the same delivery
     *  mechanism {@link #DB_PASSWORD} already uses — never a literal env-var value in the task
     *  definition. */
    public static final String ACCOUNT_SECRET_KEY = "CFC_MANAGER_ACCOUNT_SECRET_KEY";
    /** Overrides the trust-policy principal {@code ManagerIdentityResolver} would otherwise
     *  derive from Manager's own {@code sts:GetCallerIdentity} — for deployments that front
     *  Manager with a different role than the one its own AWS calls run as. */
    public static final String TRUST_PRINCIPAL_ARN = "CFC_MANAGER_TRUST_PRINCIPAL_ARN";

    public static final String LOCALSTACK_ENDPOINT = "LOCALSTACK_ENDPOINT";
    public static final String AWS_ENDPOINT_URL = "AWS_ENDPOINT_URL";
    public static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
    public static final String MINISTACK_ENDPOINT = "CFC_MANAGER_MINISTACK_ENDPOINT";

    /** Spring property keys ({@code application-local.properties}). */
    public static final String PROP_TARGET = "cfc.manager.target";
    public static final String PROP_AUTH_MODE = "cfc.manager.auth-mode";
    public static final String PROP_PUBLIC_URL = "cfc.manager.public-url";
    public static final String PROP_BIND = "cfc.manager.bind";
    public static final String PROP_DB_MODE = "cfc.manager.db-mode";
    public static final String PROP_LOCALSTACK_ENDPOINT = "cfc.manager.localstack.endpoint";
    public static final String PROP_MINISTACK_ENDPOINT = "cfc.manager.ministack.endpoint";
    public static final String PROP_OIDC_REDIRECT_URL = "cfc.manager.oidc.redirect-url";
    public static final String PROP_AWS_ENDPOINT_URL = "aws.endpoint-url";
    public static final String PROP_AWS_REGION = "aws.region";

    private ManagerEnvKeys() {
    }
}
