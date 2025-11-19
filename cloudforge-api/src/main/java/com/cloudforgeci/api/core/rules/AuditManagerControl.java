package com.cloudforgeci.api.core.rules;

import java.util.List;
import java.util.Optional;

/**
 * Maps compliance controls to AWS infrastructure monitoring and Audit Manager evidence.
 *
 * <p>This record bridges the gap between:</p>
 * <ul>
 *   <li><b>Validation Rules</b> - CDK synthesis-time checks (Soc2Rules, PciDssRules, etc.)</li>
 *   <li><b>AWS Config Rules</b> - Runtime infrastructure compliance monitoring</li>
 *   <li><b>Audit Manager Controls</b> - Continuous evidence collection and audit reports</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Define control mapping for encryption at rest
 * AuditManagerControl ebsEncryption = new AuditManagerControl(
 *     "ENCRYPTION_AT_REST",
 *     "EBS volumes must be encrypted",
 *     List.of("EbsEncryptionRule"),  // Config rules monitoring this
 *     List.of(
 *         new FrameworkControl("SOC2", "CC6.1", "Logical and Physical Access Controls"),
 *         new FrameworkControl("PCI-DSS", "Req3.4", "Render PAN unreadable"),
 *         new FrameworkControl("HIPAA", "164.312(a)(2)(iv)", "Encryption and Decryption")
 *     ),
 *     List.of("cloudtrail", "config")  // Evidence sources
 * );
 * }</pre>
 *
 * @param controlId Unique identifier for this control (matches ComplianceMatrix enum)
 * @param description Human-readable description of what this control enforces
 * @param configRuleIds List of AWS Config rule IDs that monitor this control
 * @param frameworkMappings List of framework-specific control mappings
 * @param evidenceSources AWS services providing evidence (cloudtrail, config, securityhub, etc.)
 */
public record AuditManagerControl(
    String controlId,
    String description,
    List<String> configRuleIds,
    List<FrameworkControl> frameworkMappings,
    List<String> evidenceSources
) {
    /**
     * Framework-specific control mapping.
     *
     * @param framework Compliance framework name (SOC2, PCI-DSS, HIPAA, GDPR)
     * @param controlId Framework-specific control ID (e.g., "CC6.1", "Req3.4")
     * @param controlName Human-readable control name
     */
    public record FrameworkControl(
        String framework,
        String controlId,
        String controlName
    ) {}

    /**
     * Get Config rule IDs for a specific framework.
     * Returns all Config rules that apply to this control's framework mapping.
     */
    public List<String> getConfigRulesForFramework(String framework) {
        boolean appliesToFramework = frameworkMappings.stream()
            .anyMatch(fc -> fc.framework.equalsIgnoreCase(framework));

        return appliesToFramework ? configRuleIds : List.of();
    }

    /**
     * Get framework control by framework name.
     */
    public Optional<FrameworkControl> getFrameworkControl(String framework) {
        return frameworkMappings.stream()
            .filter(fc -> fc.framework.equalsIgnoreCase(framework))
            .findFirst();
    }

    /**
     * Check if this control applies to a specific framework.
     */
    public boolean appliesToFramework(String framework) {
        return frameworkMappings.stream()
            .anyMatch(fc -> fc.framework.equalsIgnoreCase(framework));
    }
}
