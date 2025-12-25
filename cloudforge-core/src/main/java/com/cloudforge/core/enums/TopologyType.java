package com.cloudforge.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines the deployment topology patterns supported by CloudForge.
 *
 * CloudForge 3.0.0 Breaking Changes:
 * - JENKINS_SINGLE_NODE removed (use JENKINS_SERVICE instead)
 * - All new application topologies added
 *
 * <h2>JSON Serialization</h2>
 * Supports multiple input formats (case-insensitive):
 * <ul>
 *   <li>SCREAMING_SNAKE_CASE: "APPLICATION_SERVICE", "JENKINS_SERVICE", "S3_WEBSITE"</li>
 *   <li>kebab-case: "application-service", "jenkins-service", "s3-website"</li>
 *   <li>Aliases: "service" → JENKINS_SERVICE, "s3" → S3_WEBSITE, "application" → APPLICATION_SERVICE</li>
 * </ul>
 */
public enum TopologyType {
    // Preserved from 2.x
    JENKINS_SERVICE("jenkins-service"),
    S3_WEBSITE("s3-website"),

    // CloudForge 3.0.0: Universal application topology
    APPLICATION_SERVICE("application-service");  // Generic service topology for any ApplicationSpec

    private final String jsonValue;

    TopologyType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * Returns the JSON-serialized value (kebab-case).
     */
    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @Override
    public String toString() {
        return jsonValue;
    }

    /**
     * Parse topology type from string (case-insensitive).
     * Supports both enum names (SCREAMING_SNAKE_CASE) and JSON values (kebab-case).
     *
     * @param value String value from deployment context
     * @return TopologyType enum value
     * @throws IllegalArgumentException if value is not recognized
     */
    @JsonCreator
    public static TopologyType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return JENKINS_SERVICE; // Default
        }

        String normalized = value.trim().toLowerCase()
                .replace('_', '-')
                .replace(' ', '-');

        // Handle aliases
        return switch (normalized) {
            case "jenkins-service", "service" -> JENKINS_SERVICE;
            case "s3-website", "s3" -> S3_WEBSITE;
            case "application-service", "app-service", "application" -> APPLICATION_SERVICE;
            default -> {
                // Try matching by enum name (SCREAMING_SNAKE_CASE)
                try {
                    yield TopologyType.valueOf(value.trim().toUpperCase().replace('-', '_'));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Unknown topology '" + value + "'. Valid values: jenkins-service, s3-website, application-service. " +
                        "Note: JENKINS_SINGLE_NODE was removed in 3.0.0 - use jenkins-service instead."
                    );
                }
            }
        };
    }
}
