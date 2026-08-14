package com.cloudforge.core.local;

import com.cloudforge.core.manager.ManagerAwsCapabilityCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** JSON-friendly projection of {@link LocalStackCapabilitySnapshot} for health APIs. */
public final class LocalStackCapabilitySnapshotHealth {

    private LocalStackCapabilitySnapshotHealth() {
    }

    public static Map<String, Object> toHealthFields(LocalStackCapabilitySnapshot snapshot) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("healthy", snapshot.healthy());
        fields.put("tierProfile", snapshot.tierProfile().name().toLowerCase(Locale.ROOT));
        fields.put("edition", snapshot.edition());
        fields.put("version", snapshot.version());
        List<String> capabilities = new ArrayList<>();
        snapshot.capabilities().stream()
            .map(cap -> cap.name().toLowerCase(Locale.ROOT))
            .sorted()
            .forEach(capabilities::add);
        fields.put("capabilities", capabilities);
        fields.put("supportsFargatePath", snapshot.supportsFargatePath());
        fields.put("supportsRdsPath", snapshot.supportsRdsPath());
        fields.put("supportsEc2RuntimePath", snapshot.supportsEc2RuntimePath());
        fields.put("keepEfsResources", snapshot.keepEfsResources());
        fields.put("keepBackupResources", snapshot.keepBackupResources());
        fields.put("operatorIamCatalogVersion", ManagerAwsCapabilityCatalog.CATALOG_VERSION);
        if (!snapshot.details().isEmpty()) {
            fields.put("details", snapshot.details());
        }
        return fields;
    }
}
