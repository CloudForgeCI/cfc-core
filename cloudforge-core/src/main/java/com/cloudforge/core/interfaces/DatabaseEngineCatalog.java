package com.cloudforge.core.interfaces;

import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseOption;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The RDS engine versions CloudForge can provision, by engine. Versions are major versions; this is
 * the one place to add a version when RDS offers a new one.
 */
public final class DatabaseEngineCatalog {
    private static final Map<String, List<String>> MAJOR_VERSIONS = Map.of(
        "postgres", List.of("11", "12", "13", "14", "15", "16", "17", "18"),
        "mysql", List.of("5.7", "8.0", "8.4"),
        "mariadb", List.of("10.6", "10.11", "11.4", "11.8"));

    private DatabaseEngineCatalog() {
    }

    /** The major versions CloudForge offers for an engine, lowest first; empty for an unknown engine. */
    public static List<String> versions(String engine) {
        return MAJOR_VERSIONS.getOrDefault(engine.toLowerCase(), List.of());
    }

    /** Engines CloudForge can provision. */
    public static List<String> engines() {
        return List.of("postgres", "mysql", "mariadb");
    }

    /**
     * The requirement's engine at every catalog version from its minimum upward. A version is
     * tested when it is at or below the requirement's tested maximum, or at or below its default
     * when no tested maximum is declared. The requirement's own minimum is included even when the
     * catalog does not list it.
     */
    public static List<DatabaseOption> optionsFor(DatabaseRequirement requirement) {
        List<String> versions = new ArrayList<>(
            MAJOR_VERSIONS.getOrDefault(requirement.engine().toLowerCase(), List.of()));
        if (!versions.contains(requirement.version())) {
            versions.add(requirement.version());
            versions.sort(DatabaseRequirement::compareVersions);
        }
        String testedLimit = requirement.testedMaxVersion() != null
            ? requirement.testedMaxVersion() : requirement.effectiveDefaultVersion();
        List<DatabaseOption> options = new ArrayList<>();
        for (String version : versions) {
            if (!requirement.meetsMinimum(version)) {
                continue;
            }
            options.add(new DatabaseOption(requirement.engine(), version,
                DatabaseRequirement.compareVersions(version, testedLimit) <= 0));
        }
        return List.copyOf(options);
    }
}
