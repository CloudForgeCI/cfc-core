package com.cloudforge.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines the supported compliance frameworks for CloudForge deployments.
 *
 * <p>Each framework maps to industry standards and regulations:
 * <ul>
 *   <li>SOC2 - SOC 2 Trust Services Criteria</li>
 *   <li>PCI_DSS - Payment Card Industry Data Security Standard v3.2.1+</li>
 *   <li>HIPAA - Health Insurance Portability and Accountability Act Security Rule</li>
 *   <li>GDPR - General Data Protection Regulation (EU)</li>
 * </ul>
 */
public enum ComplianceFrameworkType {
    SOC2("soc2", "SOC 2 Trust Services Criteria"),
    PCI_DSS("pci-dss", "PCI-DSS v3.2.1+"),
    HIPAA("hipaa", "HIPAA Security Rule"),
    GDPR("gdpr", "General Data Protection Regulation");

    private final String jsonValue;
    private final String displayName;

    ComplianceFrameworkType(String jsonValue, String displayName) {
        this.jsonValue = jsonValue;
        this.displayName = displayName;
    }

    /**
     * Returns the JSON-serialized value (lowercase with hyphens).
     */
    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    /**
     * Returns the human-readable display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the key used in ComplianceMatrix requirements map.
     * Maps to existing string keys like "PCI-DSS", "HIPAA", "SOC2", "GDPR".
     */
    public String getMatrixKey() {
        return switch (this) {
            case SOC2 -> "SOC2";
            case PCI_DSS -> "PCI-DSS";
            case HIPAA -> "HIPAA";
            case GDPR -> "GDPR";
        };
    }

    /**
     * Creates a ComplianceFrameworkType from its JSON value.
     * Case-insensitive matching, supports both hyphenated and underscored versions.
     */
    @JsonCreator
    public static ComplianceFrameworkType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.toLowerCase().trim();

        for (ComplianceFrameworkType framework : values()) {
            if (framework.jsonValue.equalsIgnoreCase(normalized) ||
                framework.name().equalsIgnoreCase(normalized) ||
                framework.name().replace("_", "-").equalsIgnoreCase(normalized)) {
                return framework;
            }
        }

        throw new IllegalArgumentException("Unknown compliance framework: " + value +
            ". Valid values: " + Arrays.stream(values())
                .map(ComplianceFrameworkType::getJsonValue)
                .collect(Collectors.joining(", ")));
    }

    /**
     * Parses a delimited string of frameworks into a list.
     * Supports comma, space, and + as delimiters.
     * Empty/null strings return an empty list.
     *
     * @param delimited delimited framework values (e.g., "soc2,pci-dss,hipaa" or "PCI-DSS HIPAA" or "GDPR+HIPAA")
     * @return list of ComplianceFrameworkType values
     */
    public static List<ComplianceFrameworkType> parseCommaSeparated(String delimited) {
        if (delimited == null || delimited.isBlank()) {
            return Collections.emptyList();
        }

        // Support comma, space, and + as delimiters
        return Arrays.stream(delimited.split("[,\\s+]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(ComplianceFrameworkType::fromString)
            .collect(Collectors.toList());
    }

    /**
     * Converts a list of frameworks to a comma-separated string.
     *
     * @param frameworks list of frameworks
     * @return comma-separated string (e.g., "soc2,pci-dss,hipaa")
     */
    public static String toCommaSeparated(List<ComplianceFrameworkType> frameworks) {
        if (frameworks == null || frameworks.isEmpty()) {
            return "";
        }

        return frameworks.stream()
            .map(ComplianceFrameworkType::getJsonValue)
            .collect(Collectors.joining(","));
    }
}
