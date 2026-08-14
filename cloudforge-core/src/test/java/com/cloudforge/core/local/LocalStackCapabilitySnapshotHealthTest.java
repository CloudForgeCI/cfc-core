package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStackCapabilitySnapshotHealthTest {

    @Test
    void toHealthFieldsIncludesTierAndCatalogVersion() {
        var snapshot = new LocalStackCapabilitySnapshot(
            true,
            URI.create("http://localhost:4566"),
            LocalStackTierProfile.ULTIMATE,
            "pro",
            "4.0.0",
            EnumSet.of(LocalStackServiceCapability.ECS, LocalStackServiceCapability.EFS),
            Map.of("source", "test"));

        Map<String, Object> health = LocalStackCapabilitySnapshotHealth.toHealthFields(snapshot);

        assertEquals("ultimate", health.get("tierProfile"));
        assertTrue(((Iterable<?>) health.get("capabilities")).iterator().hasNext());
        assertEquals(com.cloudforge.core.manager.ManagerAwsCapabilityCatalog.CATALOG_VERSION,
            health.get("operatorIamCatalogVersion"));
        assertEquals(true, health.get("keepEfsResources"));
    }
}
