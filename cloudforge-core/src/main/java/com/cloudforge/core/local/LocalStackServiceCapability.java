package com.cloudforge.core.local;

/**
 * AWS service areas CloudForge probes on LocalStack before adapt/deploy.
 */
public enum LocalStackServiceCapability {
    ECS,
    ELBV2,
    EC2,
    AUTOSCALING,
    RDS,
    EFS,
    BACKUP,
    COGNITO;

    public static LocalStackServiceCapability fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("capability key required");
        }
        return valueOf(key.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
