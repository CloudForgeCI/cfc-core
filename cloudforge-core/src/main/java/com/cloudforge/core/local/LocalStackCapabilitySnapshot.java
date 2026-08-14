package com.cloudforge.core.local;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of LocalStack health, edition, and service availability for adapt/deploy decisions.
 */
public record LocalStackCapabilitySnapshot(
        boolean healthy,
        URI endpoint,
        LocalStackTierProfile tierProfile,
        String edition,
        String version,
        Set<LocalStackServiceCapability> capabilities,
        Map<String, Object> details) {

    public LocalStackCapabilitySnapshot {
        capabilities = capabilities == null
            ? Set.of()
            : Set.copyOf(capabilities);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean supports(LocalStackServiceCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean supportsFargatePath() {
        return supports(LocalStackServiceCapability.ECS)
            && supports(LocalStackServiceCapability.ELBV2);
    }

    public boolean supportsRdsPath() {
        return supports(LocalStackServiceCapability.RDS);
    }

    public boolean supportsEc2RuntimePath() {
        return supports(LocalStackServiceCapability.EC2)
            && supports(LocalStackServiceCapability.AUTOSCALING);
    }

    public boolean keepEfsResources() {
        return tierProfile == LocalStackTierProfile.ULTIMATE
            && supports(LocalStackServiceCapability.EFS);
    }

    public boolean keepBackupResources() {
        return tierProfile == LocalStackTierProfile.ULTIMATE
            && supports(LocalStackServiceCapability.BACKUP);
    }

    public static LocalStackCapabilitySnapshot unavailable(URI endpoint, String reason) {
        return new LocalStackCapabilitySnapshot(
            false,
            endpoint,
            LocalStackTierProfile.BASE,
            "unknown",
            "unknown",
            EnumSet.noneOf(LocalStackServiceCapability.class),
            Map.of("error", reason));
    }
}
