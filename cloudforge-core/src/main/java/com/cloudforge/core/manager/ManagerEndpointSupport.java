package com.cloudforge.core.manager;

/**
 * Resolves LocalStack / MiniStack gateway URLs from env or system properties.
 * Defaults to {@code http://localhost:4566} when unset.
 */
public final class ManagerEndpointSupport {

    public static final String DEFAULT_LOCAL_GATEWAY = "http://localhost:4566";

    private ManagerEndpointSupport() {
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
