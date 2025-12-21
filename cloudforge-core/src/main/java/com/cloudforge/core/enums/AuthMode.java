package com.cloudforge.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Authentication mode for application access control.
 *
 * <h2>Configuration</h2>
 * Set via deployment context:
 * <pre>{@code
 * cfc.put("authMode", "none");             // No authentication
 * cfc.put("authMode", "alb-oidc");         // ALB handles OIDC (recommended)
 * cfc.put("authMode", "application-oidc"); // Application handles OIDC
 * }</pre>
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>NONE</b> - No authentication, application handles its own auth</li>
 *   <li><b>ALB_OIDC</b> - ALB enforces OIDC before traffic reaches application</li>
 *   <li><b>APPLICATION_OIDC</b> - Application handles OIDC internally</li>
 * </ul>
 *
 * <h2>ALB-OIDC Benefits</h2>
 * <ul>
 *   <li>Zero code changes - authentication at infrastructure level</li>
 *   <li>Consistent auth across all applications</li>
 *   <li>Automatic token validation and refresh</li>
 *   <li>Works with Cognito, IAM Identity Center, Okta, Auth0, etc.</li>
 * </ul>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>ALB-OIDC requires ALB load balancer (lbType=alb)</li>
 *   <li>Both OIDC modes require SSL enabled (enableSsl=true)</li>
 * </ul>
 */
public enum AuthMode {
    /**
     * No CloudForge-managed authentication.
     * Application may handle its own authentication internally.
     */
    NONE("none"),

    /**
     * ALB handles OIDC authentication.
     * Unauthenticated requests are redirected to IdP login.
     * Authenticated requests include user claims in headers.
     * Requires ALB (not NLB) and SSL enabled.
     */
    ALB_OIDC("alb-oidc"),

    /**
     * Application handles OIDC internally.
     * ALB passes all traffic through, application validates tokens.
     * Useful for applications with built-in OIDC support.
     */
    APPLICATION_OIDC("application-oidc");

    private final String value;

    AuthMode(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON/string value for this auth mode.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Returns the string representation.
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * Parse auth mode from string (case-insensitive).
     *
     * @param value String value from deployment context
     * @return AuthMode enum value
     * @throws IllegalArgumentException if value is not recognized
     */
    @JsonCreator
    public static AuthMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NONE; // Default
        }

        String normalized = value.trim().toLowerCase();

        // Handle legacy alias
        if ("jenkins-oidc".equals(normalized)) {
            return APPLICATION_OIDC;
        }

        for (AuthMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }

        // Try enum name (with underscore)
        try {
            return AuthMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown auth mode '" + value + "'. Valid values: none, alb-oidc, application-oidc"
            );
        }
    }

    /**
     * Check if this mode uses OIDC authentication.
     */
    public boolean usesOidc() {
        return this == ALB_OIDC || this == APPLICATION_OIDC;
    }

    /**
     * Check if this mode requires ALB (not NLB).
     */
    public boolean requiresAlb() {
        return this == ALB_OIDC;
    }

    /**
     * Check if this mode requires SSL.
     */
    public boolean requiresSsl() {
        return usesOidc();
    }

    /**
     * Check if authentication is handled at ALB level.
     */
    public boolean isAlbAuthenticated() {
        return this == ALB_OIDC;
    }
}
