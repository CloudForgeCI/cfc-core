package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforgeci.api.core.SystemContext;

import java.util.*;
import java.util.logging.Logger;

/**
 * Multi-framework compliance control mapping matrix.
 *
 * Maps CloudForge CI security controls to requirements across multiple compliance frameworks:
 * - PCI-DSS v3.2.1
 * - HIPAA Security Rule
 * - SOC 2 Trust Services Criteria
 * - GDPR (General Data Protection Regulation)
 * - NIST SP 800-53
 * - FedRamp Moderate/High
 *
 * This matrix helps organizations understand which infrastructure controls satisfy
 * requirements across multiple frameworks and identify the controls mapped to each
 * framework.
 *
 * <p>Each control maps to framework requirements with enforcement levels:
 * <ul>
 *   <li>REQUIRED - Must be implemented for framework compliance</li>
 *   <li>ADVISORY - Recommended but alternative controls acceptable</li>
 *   <li>NOT_APPLICABLE - Not relevant to this framework</li>
 * </ul>
 *
 * <p>Validation behavior depends on complianceMode:
 * <ul>
 *   <li>ENFORCE - REQUIRED controls block deployment, ADVISORY controls warn</li>
 *   <li>ADVISORY - All violations logged as warnings only</li>
 *   <li>DISABLED - No validation performed</li>
 * </ul>
 */
public final class ComplianceMatrix {
    private static final Logger LOG = Logger.getLogger(ComplianceMatrix.class.getName());

    private ComplianceMatrix() {}

    /**
     * Requirement enforcement level for a control within a compliance framework.
     */
    public enum RequirementLevel {
        /** Must be implemented - enforced in ENFORCE mode, warnings in ADVISORY mode */
        REQUIRED,

        /** Recommended but not mandatory - always advisory regardless of mode */
        ADVISORY,

        /** Not applicable to this framework */
        NOT_APPLICABLE
    }

    /**
     * Framework-specific requirement with enforcement level.
     *
     * @param citation the framework control citation (e.g., "AC-2", "CIP-007-6 R2")
     * @param level the requirement enforcement level
     */
    public record FrameworkRequirement(
        String citation,
        RequirementLevel level
    ) {
        public static FrameworkRequirement required(String citation) {
            return new FrameworkRequirement(citation, RequirementLevel.REQUIRED);
        }

        public static FrameworkRequirement advisory(String citation) {
            return new FrameworkRequirement(citation, RequirementLevel.ADVISORY);
        }

        public static FrameworkRequirement notApplicable() {
            return new FrameworkRequirement("N/A", RequirementLevel.NOT_APPLICABLE);
        }
    }

    /**
     * Security control definitions mapped to framework requirements. The descriptions and
     * mappings are in {@code compliance-matrix.tsv}.
     */
    public enum SecurityControl {
        ENCRYPTION_AT_REST,
        ENCRYPTION_IN_TRANSIT,
        HTTPS_STRICT,
        NETWORK_SEGMENTATION,
        ACCESS_CONTROL,
        AUTHENTICATION,
        AUDIT_LOGGING,
        LOG_RETENTION,
        SECURITY_MONITORING,
        THREAT_DETECTION,
        SECURITY_HUB,
        VULNERABILITY_SCANNING,
        SENSITIVE_DATA_DISCOVERY,
        WAF_PROTECTION,
        BACKUP_RECOVERY,
        HIGH_AVAILABILITY,
        CHANGE_MANAGEMENT,
        VULNERABILITY_MANAGEMENT,
        KMS_KEY_ROTATION,
        CERTIFICATE_EXPIRATION_MONITORING,
        SECRETS_MANAGER,
        SECRETS_ROTATION,
        DATABASE_MULTI_AZ,
        DATABASE_PITR,
        NETWORK_FLOW_LOGS,
        AUDIT_MANAGER,
        CLOUDWATCH_LOGS_KMS_ENCRYPTION,
        CLOUDTRAIL_INSIGHTS,
        ROUTE53_QUERY_LOGGING,
        S3_OBJECT_LOCK,
        ROOT_ACCOUNT_PROTECTION,
        CREDENTIAL_ROTATION,
        DATABASE_ACCESS_CONTROL,
        CONTAINER_SECURITY,
        API_SECURITY,
        CDN_SECURITY,
        INSTANCE_METADATA_SECURITY,
        CERTIFICATE_MANAGEMENT,
        LAMBDA_SECURITY,
        DATABASE_LOGGING,
        DELETION_PROTECTION,
        SNS_KMS_ENCRYPTION,
        EC2_IMDSV2;

        public String getDescription() {
            return ComplianceMatrixData.description(this);
        }

        public Map<String, FrameworkRequirement> getFrameworkMappings() {
            return Collections.unmodifiableMap(ComplianceMatrixData.mappings(this));
        }

        public FrameworkRequirement getRequirement(String framework) {
            return ComplianceMatrixData.mappings(this).getOrDefault(framework, FrameworkRequirement.notApplicable());
        }

        public RequirementLevel getRequirementLevel(String framework) {
            return getRequirement(framework).level();
        }

        public boolean isRequired(String framework) {
            return getRequirementLevel(framework) == RequirementLevel.REQUIRED;
        }

        public boolean isAdvisory(String framework) {
            return getRequirementLevel(framework) == RequirementLevel.ADVISORY;
        }
    }

    /**
     * Generate a compliance matrix report showing every defined control and its
     * mappings across frameworks.
     */
    public static String generateMatrixReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        report.append("║  CloudForge CI Multi-Framework Compliance Control Matrix       ║\n");
        report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

        report.append("This matrix shows how CloudForge CI security controls map to\n");
        report.append("requirements across multiple compliance frameworks.\n\n");

        for (SecurityControl control : SecurityControl.values()) {
            report.append("─────────────────────────────────────────────────────────────────\n");
            report.append("Control: ").append(control.name()).append("\n");
            report.append("Description: ").append(control.getDescription()).append("\n\n");

            report.append("Framework Mappings:\n");
            for (Map.Entry<String, FrameworkRequirement> entry : control.getFrameworkMappings().entrySet()) {
                String levelBadge = switch (entry.getValue().level()) {
                    case REQUIRED -> "[REQUIRED]";
                    case ADVISORY -> "[ADVISORY]";
                    case NOT_APPLICABLE -> "[N/A]";
                };
                report.append("  ").append(String.format("%-10s", entry.getKey())).append(" │ ");
                report.append(String.format("%-12s", levelBadge)).append(" ");
                report.append(entry.getValue().citation());
                report.append("\n");
            }
            report.append("\n");
        }

        report.append("═════════════════════════════════════════════════════════════════\n");
        report.append("Total Controls: ").append(SecurityControl.values().length).append("\n");
        report.append("Frameworks Covered: PCI-DSS, HIPAA, SOC 2, GDPR, NIST SP 800-53\n");
        report.append("═════════════════════════════════════════════════════════════════\n\n");

        return report.toString();
    }

    /**
     * Generate a framework-specific requirements checklist.
     */
    public static String generateFrameworkChecklist(String framework) {
        StringBuilder report = new StringBuilder();
        report.append("\n").append(framework).append(" Requirements Coverage\n");
        report.append("═".repeat(50)).append("\n\n");

        Map<String, Set<SecurityControl>> requirementToControls = new HashMap<>();

        for (SecurityControl control : SecurityControl.values()) {
            FrameworkRequirement req = control.getRequirement(framework);
            if (req.level() != RequirementLevel.NOT_APPLICABLE) {
                requirementToControls.computeIfAbsent(req.citation(), k -> new HashSet<>()).add(control);
            }
        }

        List<String> sortedReqs = new ArrayList<>(requirementToControls.keySet());
        Collections.sort(sortedReqs);

        for (String requirement : sortedReqs) {
            report.append("✓ ").append(requirement).append("\n");
            report.append("  Implemented by:\n");
            for (SecurityControl control : requirementToControls.get(requirement)) {
                String levelBadge = control.getRequirement(framework).level() == RequirementLevel.REQUIRED
                    ? "[REQUIRED]" : "[ADVISORY]";
                report.append("    • ").append(control.name()).append(" ").append(levelBadge).append("\n");
                report.append("      ").append(control.getDescription()).append("\n");
            }
            report.append("\n");
        }

        report.append("Total requirements covered: ").append(sortedReqs.size()).append("\n\n");

        return report.toString();
    }

    /**
     * Validation result for a control check.
     */
    public enum ValidationResult {
        PASS,      // Control is compliant
        FAIL,      // Control violation - blocks deployment
        WARN       // Control violation - warning only
    }

    /**
     * Validates a control against framework requirements with complianceMode consideration.
     *
     * @param control Security control to validate
     * @param framework Compliance framework (e.g., "SOC2", "PCI-DSS")
     * @param isEnabled Whether the control is currently enabled
     * @param complianceMode Compliance enforcement mode
     * @return Validation result (PASS, FAIL, or WARN)
     */
    public static ValidationResult validateControl(
        SecurityControl control,
        String framework,
        boolean isEnabled,
        ComplianceMode complianceMode
    ) {
        if (complianceMode == ComplianceMode.DISABLED) {
            return ValidationResult.PASS;
        }

        RequirementLevel level = control.getRequirementLevel(framework);

        if (level == RequirementLevel.NOT_APPLICABLE) {
            return ValidationResult.PASS;
        }

        if (isEnabled) {
            return ValidationResult.PASS;
        }

        // Control is not enabled - determine enforcement based on mode and requirement level
        if (complianceMode == ComplianceMode.ADVISORY) {
            return ValidationResult.WARN;
        }

        // ENFORCE mode
        if (level == RequirementLevel.REQUIRED) {
            return ValidationResult.FAIL;
        } else {
            return ValidationResult.WARN;
        }
    }

    /**
     * Validates multiple frameworks against a control.
     *
     * @param control Security control to validate
     * @param frameworksStr Comma-separated list of frameworks (e.g., "SOC2,PCI-DSS")
     * @param isEnabled Whether the control is currently enabled
     * @param complianceMode Compliance enforcement mode
     * @return Worst validation result across all frameworks (FAIL > WARN > PASS)
     */
    public static ValidationResult validateControlMultiFramework(
        SecurityControl control,
        String frameworksStr,
        boolean isEnabled,
        ComplianceMode complianceMode
    ) {
        if (frameworksStr == null || frameworksStr.isEmpty()) {
            return ValidationResult.PASS;
        }

        ValidationResult worst = ValidationResult.PASS;

        // Support comma, space, and + as delimiters (consistent with ComplianceFrameworkType)
        for (String framework : frameworksStr.split("[,\\s+]+")) {
            // Normalize: uppercase + replace underscores with hyphens (PCI_DSS → PCI-DSS)
            // This handles both enum.name() (PCI_DSS) and enum.getMatrixKey() (PCI-DSS) inputs
            String normalized = framework.trim().toUpperCase().replace("_", "-");
            if (normalized.isEmpty()) continue;
            ValidationResult result = validateControl(control, normalized, isEnabled, complianceMode);

            if (result == ValidationResult.FAIL) {
                return ValidationResult.FAIL;
            } else if (result == ValidationResult.WARN && worst == ValidationResult.PASS) {
                worst = ValidationResult.WARN;
            }
        }

        return worst;
    }

    /**
     * Generate a deployment-specific compliance report showing which controls are enabled.
     */
    public static String generateDeploymentReport(SystemContext ctx) {
        StringBuilder report = new StringBuilder();
        report.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        report.append("║       Deployment Compliance Status Report                      ║\n");
        report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        report.append("Environment: ").append(ctx.security).append("\n");
        report.append("Region: ").append(ctx.cfc.region()).append("\n");
        report.append("Network Mode: ").append(ctx.cfc.networkMode()).append("\n");
        report.append("Authentication: ").append(ctx.cfc.authMode()).append("\n\n");

        report.append("Control Implementation Status:\n");
        report.append("─".repeat(60)).append("\n\n");

        // Check each control
        report.append(formatControlStatus("ENCRYPTION_AT_REST",
            config.isEbsEncryptionEnabled() && config.isEfsEncryptionAtRestEnabled() && config.isS3EncryptionEnabled()));

        report.append(formatControlStatus("ENCRYPTION_IN_TRANSIT",
            ctx.cert.get().isPresent() && config.isEfsEncryptionInTransitEnabled()));

        report.append(formatControlStatus("NETWORK_SEGMENTATION",
            ctx.vpc.get().isPresent() && ctx.cfc.networkMode() != NetworkMode.PUBLIC));

        report.append(formatControlStatus("ACCESS_CONTROL",
            ctx.iamProfile != null));

        report.append(formatControlStatus("AUTHENTICATION",
            ctx.cfc.authMode() != AuthMode.NONE));

        report.append(formatControlStatus("AUDIT_LOGGING",
            config.isCloudTrailEnabled() && config.isFlowLogsEnabled()));

        report.append(formatControlStatus("LOG_RETENTION",
            config.getLogRetentionDays() == software.amazon.awscdk.services.logs.RetentionDays.TWO_YEARS));

        report.append(formatControlStatus("SECURITY_MONITORING",
            config.isSecurityMonitoringEnabled() && config.isAwsConfigEnabled()));

        report.append(formatControlStatus("THREAT_DETECTION",
            config.isGuardDutyEnabled()));

        report.append(formatControlStatus("WAF_PROTECTION",
            config.isWafEnabled()));

        report.append(formatControlStatus("BACKUP_RECOVERY",
            config.isAutomatedBackupEnabled()));

        report.append(formatControlStatus("HIGH_AVAILABILITY",
            config.isMultiAzEnforced() && config.isAutoScalingEnabled()));

        report.append(formatControlStatus("CHANGE_MANAGEMENT",
            config.isCloudTrailEnabled())); // IaC + CloudTrail provides change management

        report.append(formatControlStatus("VULNERABILITY_MANAGEMENT",
            config.isAwsConfigEnabled()));

        report.append("\n");
        report.append("═".repeat(60)).append("\n");

        // Count enabled controls
        int enabledCount = 0;
        int totalCount = SecurityControl.values().length;

        if (config.isEbsEncryptionEnabled()) enabledCount++;
        if (ctx.cert.get().isPresent()) enabledCount++;
        if (ctx.vpc.get().isPresent()) enabledCount++;
        if (ctx.iamProfile != null) enabledCount++;
        if (ctx.cfc.authMode() != AuthMode.NONE) enabledCount++;
        if (config.isCloudTrailEnabled()) enabledCount++;
        if (config.getLogRetentionDays() == software.amazon.awscdk.services.logs.RetentionDays.TWO_YEARS) enabledCount++;
        if (config.isSecurityMonitoringEnabled()) enabledCount++;
        if (config.isGuardDutyEnabled()) enabledCount++;
        if (config.isWafEnabled()) enabledCount++;
        if (config.isAutomatedBackupEnabled()) enabledCount++;
        if (config.isMultiAzEnforced()) enabledCount++;
        if (config.isCloudTrailEnabled()) enabledCount++;
        if (config.isAwsConfigEnabled()) enabledCount++;

        report.append("Controls Enabled: ").append(enabledCount).append(" / ").append(totalCount);
        report.append(" (").append(String.format("%.1f", (enabledCount * 100.0 / totalCount))).append("%)\n");
        report.append("═".repeat(60)).append("\n\n");

        return report.toString();
    }

    private static String formatControlStatus(String controlName, boolean enabled) {
        String status = enabled ? "✓ ENABLED " : "✗ DISABLED";
        String color = enabled ? "" : " [ACTION REQUIRED]";
        return String.format("  %-30s %s%s\n", controlName, status, color);
    }

    /**
     * Get frameworks satisfied by current deployment configuration.
     */
    public static List<String> getSatisfiedFrameworks(SystemContext ctx) {
        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        List<String> satisfied = new ArrayList<>();

        // Check if deployment meets minimum requirements for each framework
        boolean hasEncryption = config.isEbsEncryptionEnabled() && config.isEfsEncryptionAtRestEnabled();
        boolean hasNetworkSecurity = ctx.vpc.get().isPresent() && ctx.cfc.networkMode() != NetworkMode.PUBLIC;
        boolean hasAuthentication = ctx.cfc.authMode() != AuthMode.NONE;
        boolean hasAuditLogging = config.isCloudTrailEnabled() && config.isFlowLogsEnabled();
        boolean hasMonitoring = config.isSecurityMonitoringEnabled() && config.isGuardDutyEnabled();

        if (hasEncryption && hasNetworkSecurity && hasAuthentication && hasAuditLogging && hasMonitoring) {
            satisfied.add("SOC2-Security-Common-Criteria");
            satisfied.add("GDPR-Technical-Safeguards");
        }

        if (hasEncryption && hasAuthentication && hasAuditLogging &&
            config.getLogRetentionDays() == software.amazon.awscdk.services.logs.RetentionDays.TWO_YEARS) {
            satisfied.add("HIPAA-Technical-Safeguards");
        }

        if (hasEncryption && hasNetworkSecurity && hasAuthentication && hasAuditLogging &&
            hasMonitoring && config.isWafEnabled()) {
            satisfied.add("PCI-DSS-Infrastructure");
        }

        return satisfied;
    }

    /**
     * Check if a security control should be enforced based on compliance requirements.
     *
     * <p>This method determines whether a control must be enabled based on:
     * <ul>
     *   <li>The selected compliance frameworks (e.g., "PCI-DSS,HIPAA")</li>
     *   <li>The compliance mode (ENFORCE, ADVISORY, DISABLED)</li>
     *   <li>The requirement level of the control in each framework</li>
     * </ul>
     *
     * <p>Enforcement logic:
     * <ul>
     *   <li>DISABLED mode → never require (return false)</li>
     *   <li>ENFORCE or ADVISORY mode + control is REQUIRED in any framework → require (return true)</li>
     *   <li>ENFORCE or ADVISORY mode + control is only ADVISORY → don't require (return false)</li>
     *   <li>No frameworks selected → never require (return false)</li>
     * </ul>
     *
     * <p>ADVISORY vs ENFORCE affects validation blocking and warnings, not whether
     * framework-required controls are provisioned in infrastructure.</p>
     *
     * @param frameworksStr Comma-separated list of frameworks (e.g., "PCI-DSS,HIPAA,SOC2")
     * @param mode Compliance mode (ENFORCE, ADVISORY, or DISABLED)
     * @param control Security control to check
     * @return true if the control should be enforced (must be enabled)
     */
    public static boolean isControlRequired(
        String frameworksStr,
        ComplianceMode mode,
        SecurityControl control
    ) {
        if (mode == ComplianceMode.DISABLED) {
            return false;
        }

        return isRequiredByAnyFramework(frameworksStr, control);
    }

    /**
     * Whether a framework overrides the user's setting for a control. Only ENFORCE overrides: a
     * control the selected frameworks require is then applied whatever the setting says.
     * ADVISORY reports the gap without changing the setting, and DISABLED, like selecting no
     * framework, applies nothing.
     *
     * <p>{@link #isControlRequired} answers a different question, whether a framework asks for the
     * control, which also decides what monitoring is deployed.</p>
     *
     * @param frameworksStr Comma-separated list of frameworks (e.g., "PCI-DSS,HIPAA,SOC2")
     * @param mode Compliance mode (ENFORCE, ADVISORY, or DISABLED)
     * @param control Security control to check
     * @return true if the control must be applied regardless of the user's setting
     */
    public static boolean isControlEnforced(
        String frameworksStr,
        ComplianceMode mode,
        SecurityControl control
    ) {
        return mode == ComplianceMode.ENFORCE && isRequiredByAnyFramework(frameworksStr, control);
    }

    /**
     * Returns true when any selected framework marks the control as REQUIRED.
     */
    static boolean isRequiredByAnyFramework(String frameworksStr, SecurityControl control) {
        if (frameworksStr == null || frameworksStr.isEmpty()) {
            return false;
        }

        for (String framework : frameworksStr.split("[,\\s+]+")) {
            // Normalize: uppercase + replace underscores with hyphens (PCI_DSS → PCI-DSS)
            // This handles both enum.name() (PCI_DSS) and enum.getMatrixKey() (PCI-DSS) inputs
            String normalized = framework.trim().toUpperCase().replace("_", "-");
            if (normalized.isEmpty()) {
                continue;
            }
            if (control.isRequired(normalized)) {
                LOG.fine("Control " + control.name() + " REQUIRED by framework: " + normalized);
                return true;
            }
        }

        return false;
    }

    /**
     * Check if warnings should be logged for a disabled control.
     *
     * <p>Warnings are generated when:
     * <ul>
     *   <li>Control is disabled (isEnabled = false)</li>
     *   <li>Mode is ADVISORY or ENFORCE</li>
     *   <li>At least one framework has requirements (REQUIRED or ADVISORY)</li>
     * </ul>
     *
     * @param frameworksStr Comma-separated list of frameworks
     * @param mode Compliance mode
     * @param control Security control to check
     * @param isEnabled Whether the control is currently enabled
     * @return true if warnings should be logged
     */
    public static boolean shouldWarnForControl(
        String frameworksStr,
        ComplianceMode mode,
        SecurityControl control,
        boolean isEnabled
    ) {
        // No warnings if mode is DISABLED or control is already enabled
        if (mode == ComplianceMode.DISABLED || isEnabled) {
            return false;
        }

        // No warnings if no frameworks selected
        if (frameworksStr == null || frameworksStr.isEmpty()) {
            return false;
        }

        // Check if any framework has requirements (REQUIRED or ADVISORY)
        // Support comma, space, and + as delimiters (consistent with ComplianceFrameworkType)
        for (String framework : frameworksStr.split("[,\\s+]+")) {
            // Normalize: uppercase + replace underscores with hyphens (PCI_DSS → PCI-DSS)
            // This handles both enum.name() (PCI_DSS) and enum.getMatrixKey() (PCI-DSS) inputs
            String normalized = framework.trim().toUpperCase().replace("_", "-");
            if (normalized.isEmpty()) continue;
            RequirementLevel level = control.getRequirementLevel(normalized);
            if (level != RequirementLevel.NOT_APPLICABLE) {
                return true; // Found a framework with requirements
            }
        }

        return false;
    }

    /**
     * All matrix keys with at least one control mapping for them -- i.e. every framework the
     * matrix actually knows about, independent of which {@code FrameworkRules} plugins exist.
     * A sync-enforcement test uses this to know which frameworks it should even check: a plugin
     * whose {@code frameworkId()} isn't in this set (a framework the matrix has no opinion on
     * yet) is out of scope for matrix-sync checking, not a violation.
     */
    public static Set<String> knownFrameworkKeys() {
        Set<String> keys = new TreeSet<>();
        for (SecurityControl control : SecurityControl.values()) {
            keys.addAll(control.getFrameworkMappings().keySet());
        }
        return keys;
    }

    /**
     * Finds controls the matrix marks REQUIRED for a framework that a plugin doesn't claim to
     * check (see {@code FrameworkRules#claimedControls}). An empty result means the plugin's
     * declared coverage is a superset of what the matrix requires -- it's in sync.
     *
     * @param frameworkKey the matrix's key for this framework (e.g. "SOC2", "FEDRAMP")
     * @param claimedControls control names the plugin declares via {@code claimedControls()}
     * @return REQUIRED controls not present in {@code claimedControls}, in matrix declaration order
     */
    public static List<SecurityControl> findUnclaimedRequiredControls(
        String frameworkKey,
        Set<String> claimedControls
    ) {
        List<SecurityControl> gaps = new ArrayList<>();
        for (SecurityControl control : SecurityControl.values()) {
            if (control.isRequired(frameworkKey) && !claimedControls.contains(control.name())) {
                gaps.add(control);
            }
        }
        return gaps;
    }
}
