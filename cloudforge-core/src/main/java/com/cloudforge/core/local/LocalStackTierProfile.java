package com.cloudforge.core.local;

/**
 * LocalStack product tier profile used by the template adapter.
 *
 * <p>Base covers ECS/ELB/RDS/EC2 for CloudForge Fargate smoke paths. Ultimate adds
 * native EFS and AWS Backup resources (not stripped during adaptation).</p>
 */
public enum LocalStackTierProfile {
    BASE,
    ULTIMATE;

    public static LocalStackTierProfile fromEnvOverride() {
        String raw = System.getenv("LOCALSTACK_TIER_PROFILE");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
