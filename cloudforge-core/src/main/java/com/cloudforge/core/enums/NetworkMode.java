package com.cloudforge.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Network topology mode for VPC configuration.
 *
 * <h2>Configuration</h2>
 * Set via deployment context (case-insensitive):
 * <pre>{@code
 * cfc.put("networkMode", "private-with-nat");  // Private subnets with NAT Gateway
 * cfc.put("networkMode", "public");            // Public subnets only
 * cfc.put("networkMode", "isolated");          // No internet access
 * }</pre>
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>PRIVATE_WITH_NAT</b> - Services in private subnets, outbound via NAT Gateway (recommended for production)</li>
 *   <li><b>PUBLIC</b> - Services in public subnets with public IPs (cost-effective for dev/testing)</li>
 *   <li><b>ISOLATED</b> - No internet access, requires VPC endpoints for AWS services</li>
 * </ul>
 *
 * <h2>Compliance Impact</h2>
 * <ul>
 *   <li><b>PCI-DSS</b>: Requires PRIVATE_WITH_NAT for cardholder data environments</li>
 *   <li><b>HIPAA</b>: PRIVATE_WITH_NAT recommended for PHI protection</li>
 *   <li><b>SOC2</b>: PRIVATE_WITH_NAT provides better boundary protection (CC6.1)</li>
 * </ul>
 *
 * <h2>Cost Considerations</h2>
 * <ul>
 *   <li><b>PRIVATE_WITH_NAT</b>: ~$45/month per NAT Gateway + data processing charges</li>
 *   <li><b>PUBLIC</b>: No NAT costs, minimal additional charges</li>
 *   <li><b>ISOLATED</b>: No NAT costs, but VPC endpoint costs if needed</li>
 * </ul>
 */
public enum NetworkMode {
    /**
     * Private subnets with NAT Gateway for outbound internet access.
     * Recommended for production workloads requiring compliance.
     * Serializes as "private-with-nat".
     */
    PRIVATE_WITH_NAT("private-with-nat"),

    /**
     * Public subnets with direct internet access.
     * Cost-effective for development and testing.
     * Legacy alias "public-no-nat" also supported.
     * Serializes as "public".
     */
    PUBLIC("public"),

    /**
     * Isolated subnets with no internet access.
     * Requires VPC endpoints for AWS service access.
     * Serializes as "isolated".
     */
    ISOLATED("isolated");

    private final String value;

    NetworkMode(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON/string value for this network mode.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Returns the string representation (same as getValue for consistency).
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * Parse network mode from string (case-insensitive).
     * Supports both enum names and JSON values.
     *
     * @param value String value from deployment context
     * @return NetworkMode enum value
     * @throws IllegalArgumentException if value is not recognized
     */
    @JsonCreator
    public static NetworkMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PUBLIC; // Default
        }

        String normalized = value.trim().toLowerCase();

        // Handle legacy alias
        if ("public-no-nat".equals(normalized)) {
            return PUBLIC;
        }

        // Try matching by JSON value
        for (NetworkMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }

        // Try matching by enum name
        try {
            return NetworkMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown network mode '" + value + "'. Valid values: " +
                "private-with-nat, public, isolated"
            );
        }
    }

    /**
     * Get default network mode for a security profile.
     * PRODUCTION defaults to PRIVATE_WITH_NAT, others default to PUBLIC.
     *
     * @param profile Security profile
     * @return Default network mode for the profile
     */
    public static NetworkMode defaultForProfile(SecurityProfile profile) {
        return switch (profile) {
            case PRODUCTION -> PRIVATE_WITH_NAT;
            case STAGING -> PRIVATE_WITH_NAT;
            case DEV -> PUBLIC;
        };
    }

    /**
     * Check if this mode uses private subnets (no public IPs on instances).
     */
    public boolean isPrivate() {
        return this == PRIVATE_WITH_NAT || this == ISOLATED;
    }

    /**
     * Check if this mode has outbound internet access.
     */
    public boolean hasInternetAccess() {
        return this != ISOLATED;
    }

    /**
     * Check if this mode requires NAT Gateway.
     */
    public boolean requiresNat() {
        return this == PRIVATE_WITH_NAT;
    }
}
