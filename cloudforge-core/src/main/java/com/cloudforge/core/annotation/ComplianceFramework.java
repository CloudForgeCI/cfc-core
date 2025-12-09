package com.cloudforge.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a pluggable compliance framework validator.
 *
 * <p>Compliance frameworks annotated with this annotation are automatically discovered
 * and loaded by the CloudForge compliance validation system. This enables external
 * contributors to add new compliance frameworks without modifying core code.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ComplianceFramework(value = "FEDRAMP", priority = 15)
 * public final class FedRampRules implements FrameworkRules {
 *     @Override
 *     public void install(SystemContext ctx) {
 *         // FedRAMP-specific validation rules
 *     }
 * }
 * }</pre>
 *
 * <h2>Priority Ordering:</h2>
 * <ul>
 *   <li><strong>Negative priorities (-10, -5):</strong> Cross-framework rules (KeyManagement, DatabaseSecurity)</li>
 *   <li><strong>0-50:</strong> Core compliance frameworks (HIPAA, PCI-DSS, SOC2, GDPR)</li>
 *   <li><strong>50+:</strong> Extended/contributed frameworks (FedRAMP, ISO 27001, NIST 800-53)</li>
 * </ul>
 *
 * @since 3.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ComplianceFramework {
    /**
     * Framework identifier matching the value in {@code complianceFrameworks} configuration.
     *
     * <p>Examples: "HIPAA", "PCI-DSS", "SOC2", "GDPR", "FEDRAMP", "ISO-27001"</p>
     *
     * <p>This value is matched against the comma-separated list in the deployment context:
     * <pre>{@code
     * "complianceFrameworks": "HIPAA,SOC2,FEDRAMP"
     * }</pre>
     *
     * @return the framework identifier
     */
    String value();

    /**
     * Load priority for ordering framework installation (lower values load first).
     *
     * <p>Default priority is 100 for contributed frameworks.</p>
     *
     * <p><strong>Recommended priorities:</strong></p>
     * <ul>
     *   <li><strong>-10:</strong> Cross-framework infrastructure rules (KeyManagement)</li>
     *   <li><strong>-5:</strong> Cross-framework security rules (DatabaseSecurity, AdvancedMonitoring)</li>
     *   <li><strong>0:</strong> Threat protection (ThreatProtection, IncidentResponse)</li>
     *   <li><strong>10-20:</strong> Core compliance frameworks (HIPAA, PCI-DSS, SOC2, GDPR)</li>
     *   <li><strong>50+:</strong> Extended frameworks (FedRAMP, ISO 27001, NIST 800-53)</li>
     * </ul>
     *
     * @return the load priority
     */
    int priority() default 100;

    /**
     * Whether this framework should always be loaded regardless of {@code complianceFrameworks} config.
     *
     * <p>Use {@code true} for cross-framework validators that apply to all deployments
     * (e.g., KeyManagementRules, DatabaseSecurityRules).</p>
     *
     * <p>Use {@code false} for framework-specific validators that only load when explicitly
     * enabled (e.g., HipaaRules, PciDssRules).</p>
     *
     * @return true if this framework should always load
     */
    boolean alwaysLoad() default false;

    /**
     * Human-readable display name for logging and documentation.
     *
     * <p>If not specified, defaults to the value of {@link #value()}.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"HIPAA Security Rule (45 CFR §164.308-316)"</li>
     *   <li>"PCI-DSS v3.2.1"</li>
     *   <li>"SOC 2 Trust Services Criteria"</li>
     * </ul>
     *
     * @return the display name
     */
    String displayName() default "";

    /**
     * Description of the compliance framework for documentation purposes.
     *
     * @return the framework description
     */
    String description() default "";
}
