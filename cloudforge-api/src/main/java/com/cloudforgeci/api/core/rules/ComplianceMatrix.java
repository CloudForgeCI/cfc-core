package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;

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
 * requirements across multiple frameworks, reducing audit burden and demonstrating
 * comprehensive security coverage.
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
     * Security control definitions mapped to framework requirements.
     */
    public enum SecurityControl {
        ENCRYPTION_AT_REST(
            "Encryption of data at rest (EBS, EFS, S3)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 3.4 - Render PAN unreadable"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(2)(iv) - Encryption and Decryption"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Logical and Physical Access Controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Pseudonymization and Encryption"),
                "NIST", FrameworkRequirement.required("SC-28 - Protection of Information at Rest")
            )
        ),

        ENCRYPTION_IN_TRANSIT(
            "Encryption of data in transit (TLS/SSL, EFS)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 4.1 - Encrypt transmission of cardholder data"),
                "HIPAA", FrameworkRequirement.required("§164.312(e)(1) - Transmission Security"),
                "SOC2", FrameworkRequirement.required("CC6.7 - Data Transmission Security"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Encryption of Personal Data"),
                "NIST", FrameworkRequirement.required("SC-8 - Transmission Confidentiality and Integrity")
            )
        ),

        NETWORK_SEGMENTATION(
            "Network segmentation (VPC, private subnets, security groups)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 1.3 - Prohibit direct public access to cardholder data"),
                "HIPAA", FrameworkRequirement.required("§164.312(e)(1) - Network Controls"),
                "SOC2", FrameworkRequirement.required("CC6.6 - Network Segmentation"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Confidentiality"),
                "NIST", FrameworkRequirement.required("SC-7 - Boundary Protection")
            )
        ),

        ACCESS_CONTROL(
            "Role-based access control (IAM, least privilege)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 7.1 - Limit access by business need to know"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(1) - Access Control"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Logical Access Controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Confidentiality"),
                "NIST", FrameworkRequirement.required("AC-3 - Access Enforcement")
            )
        ),

        AUTHENTICATION(
            "User authentication (SSO, OIDC, MFA)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.3 - Multi-factor authentication"),
                "HIPAA", FrameworkRequirement.required("§164.312(d) - Person or Entity Authentication"),
                "SOC2", FrameworkRequirement.required("CC6.2 - User Authentication"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Ability to ensure confidentiality"),
                "NIST", FrameworkRequirement.required("IA-2 - Identification and Authentication")
            )
        ),

        AUDIT_LOGGING(
            "Comprehensive audit logging (CloudTrail, Flow Logs, ALB logs)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.2 - Automated audit trails"),
                "HIPAA", FrameworkRequirement.required("§164.312(b) - Audit Controls"),
                "SOC2", FrameworkRequirement.required("CC7.2 - System Monitoring"),
                "GDPR", FrameworkRequirement.required("Art. 30 - Records of Processing Activities"),
                "NIST", FrameworkRequirement.required("AU-2 - Audit Events")
            )
        ),

        LOG_RETENTION(
            "Long-term log retention (2-6 years)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.7 - Retain audit trail for at least one year"),
                "HIPAA", FrameworkRequirement.required("§164.316(b)(2)(i) - Retain documentation for 6 years"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Log retention for forensic analysis"),
                "GDPR", FrameworkRequirement.advisory("Art. 30 - Maintain processing records"),
                "NIST", FrameworkRequirement.required("AU-11 - Audit Record Retention")
            )
        ),

        SECURITY_MONITORING(
            "Continuous security monitoring (CloudWatch, AWS Config)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.5 - File integrity monitoring"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(1)(ii)(D) - Information System Activity Review"),
                "SOC2", FrameworkRequirement.advisory("CC7.2 - System Monitoring for Anomalies"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Regular testing and evaluation"),
                "NIST", FrameworkRequirement.required("SI-4 - Information System Monitoring")
            )
        ),

        THREAT_DETECTION(
            "Threat detection system (GuardDuty)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.4 - Use intrusion detection/prevention systems"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(1)(ii)(D) - Security incident procedures"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Threat Detection"),
                "GDPR", FrameworkRequirement.required("Art. 33(1) - Breach Detection"),
                "NIST", FrameworkRequirement.required("SI-4 - Information System Monitoring")
            )
        ),

        SECURITY_HUB(
            "Centralized security findings (AWS Security Hub)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 11.4 - Centralized monitoring"),
                "HIPAA", FrameworkRequirement.advisory("§164.308(a)(1)(ii)(D) - Centralized security monitoring"),
                "SOC2", FrameworkRequirement.advisory("CC7.3 - Centralized security monitoring"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(b) - Centralized security posture"),
                "NIST", FrameworkRequirement.advisory("SI-4 - Information System Monitoring")
            )
        ),

        VULNERABILITY_SCANNING(
            "Vulnerability scanning (AWS Inspector)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.2 - Run internal and external vulnerability scans"),
                "HIPAA", FrameworkRequirement.advisory("§164.308(a)(8) - Periodic evaluation"),
                "SOC2", FrameworkRequirement.advisory("CC7.1 - Vulnerability detection"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(d) - Regular testing"),
                "NIST", FrameworkRequirement.required("RA-5 - Vulnerability Scanning")
            )
        ),

        SENSITIVE_DATA_DISCOVERY(
            "Sensitive data discovery (AWS Macie)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 3 - Cardholder data discovery"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(1)(ii)(A) - PHI identification"),
                "SOC2", FrameworkRequirement.notApplicable(),
                "GDPR", FrameworkRequirement.required("Art. 30 - Personal data inventory"),
                "NIST", FrameworkRequirement.advisory("SI-4 - Information System Monitoring")
            )
        ),

        WAF_PROTECTION(
            "Web Application Firewall (AWS WAF)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 6.6 - Public-facing web applications protected"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(e)(1) - Transmission security mechanisms"),
                "SOC2", FrameworkRequirement.required("CC6.6 - Web application protection"),
                "GDPR", FrameworkRequirement.required("Art. 32(1) - Appropriate security measures"),
                "NIST", FrameworkRequirement.required("SC-7(11) - Boundary Protection")
            )
        ),

        BACKUP_RECOVERY(
            "Automated backup and disaster recovery",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 9.5.1 - Store backup media in secure location"),
                "HIPAA", FrameworkRequirement.required("§164.310(d)(2)(iii) - Data Backup and Storage"),
                "SOC2", FrameworkRequirement.required("A1.3 - Recovery capabilities"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(c) - Restore availability and access"),
                "NIST", FrameworkRequirement.required("CP-9 - Information System Backup")
            )
        ),

        HIGH_AVAILABILITY(
            "High availability configuration (Multi-AZ, auto-scaling)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 12.10.4 - Provide coverage for critical systems"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(7)(ii)(B) - Disaster recovery plan"),
                "SOC2", FrameworkRequirement.required("A1.2 - Maintain system availability"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Ensure resilience of systems"),
                "NIST", FrameworkRequirement.required("CP-2 - Contingency Plan")
            )
        ),

        CHANGE_MANAGEMENT(
            "Infrastructure as Code and change tracking",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 6.4.5 - Implement change control procedures"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(8) - Evaluation of security measures"),
                "SOC2", FrameworkRequirement.required("CC8.1 - Change Management Process"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Process for regular testing"),
                "NIST", FrameworkRequirement.required("CM-3 - Configuration Change Control")
            )
        ),

        VULNERABILITY_MANAGEMENT(
            "Configuration compliance monitoring (AWS Config)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.2 - Run internal and external scans"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(8) - Periodic evaluation"),
                "SOC2", FrameworkRequirement.required("CC7.1 - Vulnerability detection and remediation"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Regular testing and evaluating effectiveness"),
                "NIST", FrameworkRequirement.required("RA-5 - Vulnerability Scanning")
            )
        );

        private final String description;
        private final Map<String, FrameworkRequirement> frameworkMappings;

        SecurityControl(String description, Map<String, FrameworkRequirement> frameworkMappings) {
            this.description = description;
            this.frameworkMappings = frameworkMappings;
        }

        public String getDescription() {
            return description;
        }

        public Map<String, FrameworkRequirement> getFrameworkMappings() {
            return frameworkMappings;
        }

        public FrameworkRequirement getRequirement(String framework) {
            return frameworkMappings.getOrDefault(framework, FrameworkRequirement.notApplicable());
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
     * Generate a comprehensive compliance matrix report showing all controls
     * and their mappings across frameworks.
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

        for (String framework : frameworksStr.split(",")) {
            String normalized = framework.trim().toUpperCase().replace("-", "");
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
            ctx.vpc.get().isPresent() && !"public-no-nat".equals(ctx.cfc.networkMode())));

        report.append(formatControlStatus("ACCESS_CONTROL",
            ctx.iamProfile != null));

        report.append(formatControlStatus("AUTHENTICATION",
            !"none".equals(ctx.cfc.authMode())));

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
        if (!"none".equals(ctx.cfc.authMode())) enabledCount++;
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
        boolean hasNetworkSecurity = ctx.vpc.get().isPresent() && !"public-no-nat".equals(ctx.cfc.networkMode());
        boolean hasAuthentication = !"none".equals(ctx.cfc.authMode());
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
}
