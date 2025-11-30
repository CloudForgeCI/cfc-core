package com.cloudforge.core.enums;

/**
 * Compliance validation mode controlling how validation failures are handled.
 *
 * <h2>Configuration</h2>
 * Set via deployment context (case-insensitive):
 * <pre>{@code
 * cfc.put("complianceMode", "advisory");  // Warnings only
 * cfc.put("complianceMode", "enforce");   // Fail on violations (default)
 * }</pre>
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>ENFORCE</b> - Validation failures block CDK synthesis (default for PRODUCTION)</li>
 *   <li><b>ADVISORY</b> - Validation failures logged as warnings only (default for DEV/STAGING)</li>
 * </ul>
 *
 * <h2>Use Cases</h2>
 *
 * <h3>ENFORCE Mode</h3>
 * <p>Use when you need strict compliance enforcement:</p>
 * <ul>
 *   <li>Production environments handling sensitive data</li>
 *   <li>Regulated industries (healthcare, finance, government)</li>
 *   <li>Formal compliance audits (SOC2 Type II, PCI-DSS validation)</li>
 *   <li>Preventing deployment of non-compliant infrastructure</li>
 * </ul>
 *
 * <h3>ADVISORY Mode</h3>
 * <p>Use when you want compliance guidance without blocking deployment:</p>
 * <ul>
 *   <li>Development and testing environments</li>
 *   <li>Proof-of-concept or demo deployments</li>
 *   <li>Learning about compliance requirements</li>
 *   <li>Phased compliance implementation</li>
 *   <li>Cost-optimized non-production environments</li>
 * </ul>
 *
 * <h2>Examples</h2>
 *
 * <h3>Production with Strict Enforcement</h3>
 * <pre>{@code
 * cfc.put("securityProfile", "production");
 * cfc.put("complianceMode", "enforce");
 * cfc.put("complianceFrameworks", "PCI-DSS,SOC2");
 * // WAF disabled → CDK synthesis FAILS
 * cfc.put("wafEnabled", false);
 * }</pre>
 *
 * <h3>Development with Advisory Warnings</h3>
 * <pre>{@code
 * cfc.put("securityProfile", "dev");
 * cfc.put("complianceMode", "advisory");
 * cfc.put("complianceFrameworks", "SOC2");
 * // WAF disabled → CDK synthesis SUCCEEDS with warnings
 * cfc.put("wafEnabled", false);
 * }</pre>
 *
 * <h3>Staging - Test Compliance Configuration</h3>
 * <pre>{@code
 * cfc.put("securityProfile", "staging");
 * cfc.put("complianceMode", "advisory");  // Test without blocking
 * cfc.put("complianceFrameworks", "PCI-DSS,HIPAA");
 * // See all compliance violations as warnings
 * // Fix issues before promoting to production
 * }</pre>
 */
public enum ComplianceMode {
    /**
     * Validation failures block CDK synthesis.
     * Non-compliant deployments cannot be deployed.
     * Use for production environments requiring strict compliance.
     */
    ENFORCE,

    /**
     * Validation failures logged as warnings only.
     * Deployments proceed with compliance violations documented.
     * Use for development, testing, or phased compliance implementation.
     */
    ADVISORY;

    /**
     * Parse compliance mode from string (case-insensitive).
     * Returns default if input is null or invalid.
     *
     * @param value String value from deployment context
     * @param defaultMode Default mode if parsing fails
     * @return Parsed ComplianceMode or default
     */
    public static ComplianceMode fromString(String value, ComplianceMode defaultMode) {
        if (value == null || value.trim().isEmpty()) {
            return defaultMode;
        }

        try {
            return ComplianceMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultMode;
        }
    }

    /**
     * Get default compliance mode for a security profile.
     * PRODUCTION defaults to ENFORCE, others default to ADVISORY.
     *
     * @param profile Security profile
     * @return Default compliance mode for the profile
     */
    public static ComplianceMode defaultForProfile(SecurityProfile profile) {
        return switch (profile) {
            case PRODUCTION -> ENFORCE;
            case STAGING, DEV -> ADVISORY;
        };
    }
}
