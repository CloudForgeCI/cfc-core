package com.cloudforge.core.local;

/**
 * Post-synthesis deployment target for CloudForge templates.
 *
 * <p>{@link #AWS} is not driven through the local pipeline — CDK deploys directly to AWS.
 * {@link #MINISTACK} and {@link #LOCALSTACK} use adapter modules that implement
 * {@link TemplateAdapter} and {@link LocalDeployer}.</p>
 */
public enum DeploymentTarget {
    AWS,
    MINISTACK,
    LOCALSTACK;

    /** Lowercase id used in deployment context and history ({@code aws}, {@code ministack}, {@code localstack}). */
    public String configKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Parses a wire-format target id ({@link #configKey()}'s inverse) — case-insensitive,
     * defaults to {@link #AWS} for blank input (matching every other target-selector default in
     * this codebase). Unknown non-blank values are a caller error, not silently coerced —
     * accidentally routing a real deploy to the wrong target is exactly the class of bug worth
     * failing loudly on.
     */
    public static DeploymentTarget fromConfigKey(String key) {
        if (key == null || key.isBlank()) {
            return AWS;
        }
        return switch (key.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "aws" -> AWS;
            case "ministack" -> MINISTACK;
            case "localstack" -> LOCALSTACK;
            default -> throw new IllegalArgumentException("Unknown deployment target: " + key);
        };
    }
}
