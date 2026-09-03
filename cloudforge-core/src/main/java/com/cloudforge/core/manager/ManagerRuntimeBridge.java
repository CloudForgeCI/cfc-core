package com.cloudforge.core.manager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Framework-neutral bridge that materializes Manager settings into system properties
 * when the process environment does not already define them.
 *
 * <p>Spring Boot, tests, and local launchers supply values through {@code lookup};
 * deploy-time container env still wins when present.</p>
 */
public final class ManagerRuntimeBridge {

    private ManagerRuntimeBridge() {
    }

    /**
     * Apply resolved settings. {@code lookup} is typically Spring Environment or a Map.
     */
    public static void apply(Function<String, String> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        String target = first(lookup, ManagerEnvKeys.PROP_TARGET, ManagerEnvKeys.TARGET);
        setIfAbsent(ManagerEnvKeys.TARGET, target);
        setIfAbsent(ManagerEnvKeys.AUTH_MODE, first(lookup, ManagerEnvKeys.PROP_AUTH_MODE, ManagerEnvKeys.AUTH_MODE));
        setIfAbsent(ManagerEnvKeys.PUBLIC_URL, first(lookup, ManagerEnvKeys.PROP_PUBLIC_URL, ManagerEnvKeys.PUBLIC_URL));
        setIfAbsent(ManagerEnvKeys.BIND, first(lookup, ManagerEnvKeys.PROP_BIND, "server.address", ManagerEnvKeys.BIND));
        setIfAbsent(ManagerEnvKeys.PORT, first(lookup, "server.port", ManagerEnvKeys.PORT));
        setIfAbsent(ManagerEnvKeys.DB_MODE, first(lookup, ManagerEnvKeys.PROP_DB_MODE, ManagerEnvKeys.DB_MODE));

        String awsEndpoint = first(lookup, ManagerEnvKeys.PROP_AWS_ENDPOINT_URL, ManagerEnvKeys.AWS_ENDPOINT_URL);
        String localstack = first(lookup, ManagerEnvKeys.PROP_LOCALSTACK_ENDPOINT, ManagerEnvKeys.LOCALSTACK_ENDPOINT);
        if (localstack == null) {
            localstack = awsEndpoint;
        }
        String ministack = first(lookup, ManagerEnvKeys.PROP_MINISTACK_ENDPOINT, ManagerEnvKeys.MINISTACK_ENDPOINT);
        if (ministack == null) {
            ministack = awsEndpoint != null ? awsEndpoint : localstack;
        }

        // ManagerProperties.Localstack#endpoint defaults to "http://localhost:4566" unconditionally
        // (a Java field default, there for LocalStack-target convenience) — that default is NOT the
        // same thing as a genuine "run against a local emulator" instruction, but folding it into
        // `localstack` above (via the `if (localstack == null)` fallback, itself only reached when
        // nothing else set it either) made it look like one by the time it got here. Both
        // LOCALSTACK_ENDPOINT and AWS_ENDPOINT_URL (the AWS SDK's own globally-recognized endpoint
        // override — every AWS client built anywhere in this process, Cognito's included, see
        // ApplicationOidcAuthenticator, honors the latter) have to be gated on genuinely targeting a
        // local emulator (target=localstack/ministack), not just AWS_ENDPOINT_URL alone —
        // ApplicationOidcAuthenticator#resolveLocalEmulatorEndpoint checks LOCALSTACK_ENDPOINT
        // FIRST, so gating only AWS_ENDPOINT_URL left that check-order completely unprotected:
        // confirmed live, Cognito sign-in kept silently trying to reach localhost:4566 in a
        // production ECS task with no LocalStack anywhere in sight even after the first attempt at
        // this fix, because LOCALSTACK_ENDPOINT itself was still being bridged unconditionally here.
        boolean targetingLocalEmulator = "localstack".equalsIgnoreCase(target) || "ministack".equalsIgnoreCase(target);
        if (targetingLocalEmulator) {
            setIfAbsent(ManagerEnvKeys.LOCALSTACK_ENDPOINT, localstack);
            setIfAbsent(ManagerEnvKeys.AWS_ENDPOINT_URL, firstNonBlank(awsEndpoint, localstack));
        } else {
            // awsEndpoint only (never `localstack`, which may carry the inert convenience default)
            // — a genuine, explicitly-set AWS_ENDPOINT_URL/its PROP_ counterpart still bridges
            // normally outside a local-emulator target (e.g. a real custom VPC endpoint override).
            setIfAbsent(ManagerEnvKeys.LOCALSTACK_ENDPOINT, awsEndpoint);
            setIfAbsent(ManagerEnvKeys.AWS_ENDPOINT_URL, awsEndpoint);
        }
        setIfAbsent(ManagerEnvKeys.AWS_DEFAULT_REGION,
            first(lookup, ManagerEnvKeys.PROP_AWS_REGION, ManagerEnvKeys.AWS_DEFAULT_REGION));
        if (System.getProperty(ManagerEnvKeys.AWS_DEFAULT_REGION) == null
                && (System.getenv(ManagerEnvKeys.AWS_DEFAULT_REGION) == null
                || System.getenv(ManagerEnvKeys.AWS_DEFAULT_REGION).isBlank())) {
            System.setProperty(ManagerEnvKeys.AWS_DEFAULT_REGION, "us-east-1");
        }
        setIfAbsent(ManagerEnvKeys.MINISTACK_ENDPOINT, ministack);

        for (String oidcKey : oidcKeys()) {
            setIfAbsent(oidcKey, first(lookup, oidcKey));
        }
        setIfAbsent(ManagerEnvKeys.OIDC_REDIRECT_URL,
            first(lookup, ManagerEnvKeys.PROP_OIDC_REDIRECT_URL, ManagerEnvKeys.OIDC_REDIRECT_URL));
    }

    /** Snapshot of OIDC-related keys for tests and docs. */
    public static Map<String, String> oidcKeyCatalog() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : oidcKeys()) {
            map.put(key, key);
        }
        return map;
    }

    private static String[] oidcKeys() {
        return new String[] {
            ManagerEnvKeys.OIDC_ISSUER,
            ManagerEnvKeys.OIDC_AUTHORIZATION_ENDPOINT,
            ManagerEnvKeys.OIDC_TOKEN_ENDPOINT,
            ManagerEnvKeys.OIDC_USERINFO_ENDPOINT,
            ManagerEnvKeys.OIDC_JWKS_URI,
            ManagerEnvKeys.OIDC_CLIENT_ID,
            ManagerEnvKeys.OIDC_CLIENT_SECRET,
            ManagerEnvKeys.OIDC_REDIRECT_URL,
            ManagerEnvKeys.OIDC_SCOPES,
            ManagerEnvKeys.OIDC_USERNAME_CLAIM,
            ManagerEnvKeys.OIDC_EMAIL_CLAIM,
            ManagerEnvKeys.OIDC_GROUPS_CLAIM,
            ManagerEnvKeys.OIDC_ADMIN_GROUP,
            ManagerEnvKeys.OIDC_MANAGER_GROUP
        };
    }

    private static String first(Function<String, String> lookup, String... keys) {
        for (String key : keys) {
            String value = lookup.apply(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
            String env = System.getenv(key);
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
            String prop = System.getProperty(key);
            if (prop != null && !prop.isBlank()) {
                return prop.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static void setIfAbsent(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return;
        }
        System.setProperty(key, value.trim());
    }
}
