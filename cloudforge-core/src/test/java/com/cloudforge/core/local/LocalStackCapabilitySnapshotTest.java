package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStackCapabilitySnapshotTest {

    @Test
    void ultimateKeepsEfsAndBackupWhenCapabilitiesPresent() {
        var snapshot = new LocalStackCapabilitySnapshot(
            true,
            URI.create("http://localhost:4566"),
            LocalStackTierProfile.ULTIMATE,
            "ultimate",
            "4.0",
            EnumSet.of(
                LocalStackServiceCapability.EFS,
                LocalStackServiceCapability.BACKUP),
            java.util.Map.of());

        assertTrue(snapshot.keepEfsResources());
        assertTrue(snapshot.keepBackupResources());
    }

    @Test
    void baseStripsEfsAndBackupEvenIfMislabeledUltimate() {
        var snapshot = new LocalStackCapabilitySnapshot(
            true,
            URI.create("http://localhost:4566"),
            LocalStackTierProfile.ULTIMATE,
            "ultimate",
            "4.0",
            EnumSet.of(LocalStackServiceCapability.ECS),
            java.util.Map.of());

        assertFalse(snapshot.keepEfsResources());
        assertFalse(snapshot.keepBackupResources());
    }
}
