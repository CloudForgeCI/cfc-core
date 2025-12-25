package com.cloudforge.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines the security profile levels for CloudForge deployments.
 * Higher profiles enable stricter security controls and compliance features.
 *
 * <h2>Security Profile Hierarchy</h2>
 * <ul>
 *   <li><b>DEV</b> - Development environment with minimal security controls</li>
 *   <li><b>STAGING</b> - Pre-production with moderate security controls</li>
 *   <li><b>PRODUCTION</b> - Full security controls and compliance enforcement</li>
 * </ul>
 *
 * <h2>JSON Serialization</h2>
 * Case-insensitive parsing: "dev", "DEV", "Dev" all map to DEV.
 */
public enum SecurityProfile {
    DEV("dev"),
    STAGING("staging"),
    PRODUCTION("production");

    private final String jsonValue;

    SecurityProfile(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * Returns the JSON-serialized value (lowercase).
     */
    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @Override
    public String toString() {
        return name(); // Keep enum name for backward compatibility in logging
    }

    /**
     * Parse security profile from string (case-insensitive).
     *
     * @param value String value from deployment context
     * @return SecurityProfile enum value, or DEV as default
     */
    @JsonCreator
    public static SecurityProfile fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEV; // Default
        }

        String normalized = value.trim().toLowerCase();

        return switch (normalized) {
            case "dev", "development" -> DEV;
            case "staging", "stage" -> STAGING;
            case "production", "prod" -> PRODUCTION;
            default -> {
                // Try matching by enum name
                try {
                    yield SecurityProfile.valueOf(value.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Unknown security profile '" + value + "'. Valid values: dev, staging, production"
                    );
                }
            }
        };
    }
}
