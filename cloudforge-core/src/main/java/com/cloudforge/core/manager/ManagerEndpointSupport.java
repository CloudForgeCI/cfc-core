package com.cloudforge.core.manager;

import com.cloudforge.core.local.DeploymentTarget;

/**
 * Resolves LocalStack / MiniStack gateway URLs from env or system properties.
 * Defaults to {@code http://localhost:4566} when unset.
 */
public final class ManagerEndpointSupport {

    public static final String DEFAULT_LOCAL_GATEWAY = "http://localhost:4566";

    private ManagerEndpointSupport() {
    }

    /**
     * The one authoritative, target-gated answer to "should this call be redirected to a local
     * emulator instead of real AWS" — {@code null} immediately for {@link DeploymentTarget#AWS}
     * OR an unresolved/{@code null} target, without even reading the env vars, otherwise the same
     * {@code LOCALSTACK_ENDPOINT} then {@code AWS_ENDPOINT_URL} fallback chain {@link
     * #resolveLocalStackEndpoint} already used. {@code null} target fails closed to "real AWS"
     * rather than falling through to the env-var chain — an installation that never explicitly
     * chose a local target shouldn't have its calls silently redirected just because some env var
     * happens to be present, same fail-closed shape as this codebase's other target-based gates
     * (see {@code StripeConfiguration}).
     *
     * <p>Exists because, before this method, several call sites across cloudforge-api and
     * cloudforge-manager each independently re-derived "are we local" from env-var presence
     * alone, with no target check at all — so a real {@code target=aws} production install would
     * happily route Cognito/STS/Service Catalog SDK calls to whatever {@code AWS_ENDPOINT_URL}
     * happened to be set to, an SSRF-adjacent risk on the customer's own AWS credentials.
     * {@code target} should always be the caller's own already-known, validated {@link
     * DeploymentTarget} (the per-request target for a deploy, or the installation's own {@code
     * ManagerRuntimeConfiguration.Target} for Manager's own dev/test-loop calls) — never
     * re-derived from these same env vars, or this gate would just move one level up instead of
     * closing.
     */
    public static String resolveLocalEmulatorEndpoint(DeploymentTarget target) {
        if (target == null || target == DeploymentTarget.AWS) {
            return null;
        }
        String localstack = first(ManagerEnvKeys.LOCALSTACK_ENDPOINT);
        if (localstack != null) {
            return localstack;
        }
        return first(ManagerEnvKeys.AWS_ENDPOINT_URL);
    }

    public static String resolveLocalStackEndpoint() {
        String localstack = first(ManagerEnvKeys.LOCALSTACK_ENDPOINT);
        if (localstack != null) {
            return localstack;
        }
        String aws = first(ManagerEnvKeys.AWS_ENDPOINT_URL);
        return aws != null ? aws : DEFAULT_LOCAL_GATEWAY;
    }

    public static String resolveMiniStackEndpoint() {
        String dedicated = first(ManagerEnvKeys.MINISTACK_ENDPOINT);
        if (dedicated != null) {
            return dedicated;
        }
        String aws = first(ManagerEnvKeys.AWS_ENDPOINT_URL);
        return aws != null ? aws : DEFAULT_LOCAL_GATEWAY;
    }

    public static String resolveRegion() {
        String region = first(ManagerEnvKeys.AWS_DEFAULT_REGION);
        return region != null ? region : "us-east-1";
    }

    private static String first(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty(key);
        return prop == null || prop.isBlank() ? null : prop.trim();
    }
}
