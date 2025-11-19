package com.cloudforgeci.api.core.rules;

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
 *
 * This matrix helps organizations understand which infrastructure controls satisfy
 * requirements across multiple frameworks, reducing audit burden and demonstrating
 * comprehensive security coverage.
 */
public final class ComplianceMatrix {
    private static final Logger LOG = Logger.getLogger(ComplianceMatrix.class.getName());

    private ComplianceMatrix() {}

    /**
     * Security control definitions mapped to framework requirements.
     */
    public enum SecurityControl {
        ENCRYPTION_AT_REST(
            "Encryption of data at rest (EBS, EFS, S3)",
            Map.of(
                "PCI-DSS", List.of("Req 3.4 - Render PAN unreadable"),
                "HIPAA", List.of("§164.312(a)(2)(iv) - Encryption and Decryption"),
                "SOC2", List.of("CC6.1 - Logical and Physical Access Controls"),
                "GDPR", List.of("Art. 25(1) - Data Protection by Design", "Art. 32(1)(a) - Pseudonymization and Encryption"),
                "NIST", List.of("SC-28 - Protection of Information at Rest")
            )
        ),

        ENCRYPTION_IN_TRANSIT(
            "Encryption of data in transit (TLS/SSL, EFS)",
            Map.of(
                "PCI-DSS", List.of("Req 4.1 - Encrypt transmission of cardholder data"),
                "HIPAA", List.of("§164.312(e)(1) - Transmission Security", "§164.312(e)(2)(ii) - Encryption"),
                "SOC2", List.of("CC6.7 - Data Transmission Security"),
                "GDPR", List.of("Art. 32(1)(a) - Encryption of Personal Data"),
                "NIST", List.of("SC-8 - Transmission Confidentiality and Integrity")
            )
        ),

        NETWORK_SEGMENTATION(
            "Network segmentation (VPC, private subnets, security groups)",
            Map.of(
                "PCI-DSS", List.of("Req 1.2.1 - Restrict inbound/outbound traffic", "Req 1.3 - Prohibit direct public access"),
                "HIPAA", List.of("§164.312(e)(1) - Network Controls"),
                "SOC2", List.of("CC6.6 - Network Segmentation"),
                "GDPR", List.of("Art. 25(1) - Data Protection by Design", "Art. 32(1)(b) - Confidentiality"),
                "NIST", List.of("SC-7 - Boundary Protection")
            )
        ),

        ACCESS_CONTROL(
            "Role-based access control (IAM, least privilege)",
            Map.of(
                "PCI-DSS", List.of("Req 7.1 - Limit access by business need to know", "Req 7.2 - Access control system"),
                "HIPAA", List.of("§164.312(a)(1) - Access Control", "§164.308(a)(4) - Information Access Management"),
                "SOC2", List.of("CC6.1 - Logical Access Controls", "CC6.2 - Access Management"),
                "GDPR", List.of("Art. 25(2) - Data Minimization", "Art. 32(1)(b) - Confidentiality"),
                "NIST", List.of("AC-3 - Access Enforcement", "AC-6 - Least Privilege")
            )
        ),

        AUTHENTICATION(
            "User authentication (SSO, OIDC, MFA)",
            Map.of(
                "PCI-DSS", List.of("Req 8.2 - Ensure proper user authentication", "Req 8.3 - Multi-factor authentication"),
                "HIPAA", List.of("§164.312(d) - Person or Entity Authentication", "§164.312(a)(2)(i) - Unique User Identification"),
                "SOC2", List.of("CC6.2 - User Authentication"),
                "GDPR", List.of("Art. 32(1)(b) - Ability to ensure confidentiality"),
                "NIST", List.of("IA-2 - Identification and Authentication", "IA-2(1) - Multi-Factor Authentication")
            )
        ),

        AUDIT_LOGGING(
            "Comprehensive audit logging (CloudTrail, Flow Logs, ALB logs)",
            Map.of(
                "PCI-DSS", List.of("Req 10.1 - Implement audit trails", "Req 10.2 - Automated audit trails", "Req 10.3 - Record audit trail entries"),
                "HIPAA", List.of("§164.312(b) - Audit Controls"),
                "SOC2", List.of("CC7.2 - System Monitoring"),
                "GDPR", List.of("Art. 30 - Records of Processing Activities"),
                "NIST", List.of("AU-2 - Audit Events", "AU-3 - Content of Audit Records")
            )
        ),

        LOG_RETENTION(
            "Long-term log retention (2-6 years)",
            Map.of(
                "PCI-DSS", List.of("Req 10.7 - Retain audit trail for at least one year"),
                "HIPAA", List.of("§164.316(b)(2)(i) - Retain documentation for 6 years"),
                "SOC2", List.of("CC7.2 - Log retention for forensic analysis"),
                "GDPR", List.of("Art. 30 - Maintain processing records"),
                "NIST", List.of("AU-11 - Audit Record Retention")
            )
        ),

        SECURITY_MONITORING(
            "Continuous security monitoring (GuardDuty, CloudWatch, AWS Config)",
            Map.of(
                "PCI-DSS", List.of("Req 11.4 - Intrusion detection/prevention", "Req 11.5 - File integrity monitoring"),
                "HIPAA", List.of("§164.308(a)(1)(ii)(D) - Information System Activity Review"),
                "SOC2", List.of("CC7.2 - System Monitoring for Anomalies"),
                "GDPR", List.of("Art. 32(1)(b) - Ensure ongoing integrity", "Art. 32(1)(d) - Regular testing and evaluation"),
                "NIST", List.of("SI-4 - Information System Monitoring", "SI-7 - Software and Information Integrity")
            )
        ),

        THREAT_DETECTION(
            "Threat detection system (GuardDuty)",
            Map.of(
                "PCI-DSS", List.of("Req 11.4 - Use intrusion detection/prevention systems"),
                "HIPAA", List.of("§164.308(a)(1)(ii)(D) - Security incident procedures"),
                "SOC2", List.of("CC7.2 - Threat Detection"),
                "GDPR", List.of("Art. 33(1) - Breach Detection"),
                "NIST", List.of("SI-4 - Information System Monitoring")
            )
        ),

        WAF_PROTECTION(
            "Web Application Firewall (AWS WAF)",
            Map.of(
                "PCI-DSS", List.of("Req 6.6 - Public-facing web applications protected"),
                "HIPAA", List.of("§164.312(e)(1) - Transmission security mechanisms"),
                "SOC2", List.of("CC6.6 - Web application protection"),
                "GDPR", List.of("Art. 32(1) - Appropriate security measures"),
                "NIST", List.of("SC-7(11) - Boundary Protection - Restrict Incoming Communications")
            )
        ),

        BACKUP_RECOVERY(
            "Automated backup and disaster recovery",
            Map.of(
                "PCI-DSS", List.of("Req 9.5.1 - Store backup media in secure location"),
                "HIPAA", List.of("§164.310(d)(2)(iii) - Data Backup and Storage"),
                "SOC2", List.of("A1.3 - Recovery capabilities", "CC7.3 - Environmental protections"),
                "GDPR", List.of("Art. 32(1)(c) - Restore availability and access"),
                "NIST", List.of("CP-9 - Information System Backup", "CP-10 - Information System Recovery")
            )
        ),

        HIGH_AVAILABILITY(
            "High availability configuration (Multi-AZ, auto-scaling)",
            Map.of(
                "PCI-DSS", List.of("Req 12.10.4 - Provide coverage for critical systems"),
                "HIPAA", List.of("§164.308(a)(7)(ii)(B) - Disaster recovery plan"),
                "SOC2", List.of("A1.2 - Maintain system availability", "CC7.3 - Environmental protections"),
                "GDPR", List.of("Art. 32(1)(b) - Ensure resilience of systems"),
                "NIST", List.of("CP-2 - Contingency Plan")
            )
        ),

        CHANGE_MANAGEMENT(
            "Infrastructure as Code and change tracking",
            Map.of(
                "PCI-DSS", List.of("Req 6.4.5 - Implement change control procedures"),
                "HIPAA", List.of("§164.308(a)(8) - Evaluation of security measures"),
                "SOC2", List.of("CC8.1 - Change Management Process"),
                "GDPR", List.of("Art. 32(1)(d) - Process for regular testing"),
                "NIST", List.of("CM-3 - Configuration Change Control")
            )
        ),

        VULNERABILITY_MANAGEMENT(
            "Configuration compliance monitoring (AWS Config)",
            Map.of(
                "PCI-DSS", List.of("Req 6.2 - Ensure systems protected from known vulnerabilities", "Req 11.2 - Run internal and external scans"),
                "HIPAA", List.of("§164.308(a)(8) - Periodic evaluation"),
                "SOC2", List.of("CC7.1 - Vulnerability detection and remediation"),
                "GDPR", List.of("Art. 32(1)(d) - Regular testing and evaluating effectiveness"),
                "NIST", List.of("RA-5 - Vulnerability Scanning", "SI-2 - Flaw Remediation")
            )
        );

        private final String description;
        private final Map<String, List<String>> frameworkMappings;

        SecurityControl(String description, Map<String, List<String>> frameworkMappings) {
            this.description = description;
            this.frameworkMappings = frameworkMappings;
        }

        public String getDescription() {
            return description;
        }

        public Map<String, List<String>> getFrameworkMappings() {
            return frameworkMappings;
        }

        public List<String> getRequirements(String framework) {
            return frameworkMappings.getOrDefault(framework, Collections.emptyList());
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
            for (Map.Entry<String, List<String>> entry : control.getFrameworkMappings().entrySet()) {
                report.append("  ").append(String.format("%-10s", entry.getKey())).append(" │ ");
                report.append(String.join("\n" + " ".repeat(15) + "│ ", entry.getValue()));
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
            List<String> requirements = control.getRequirements(framework);
            for (String req : requirements) {
                requirementToControls.computeIfAbsent(req, k -> new HashSet<>()).add(control);
            }
        }

        List<String> sortedReqs = new ArrayList<>(requirementToControls.keySet());
        Collections.sort(sortedReqs);

        for (String requirement : sortedReqs) {
            report.append("✓ ").append(requirement).append("\n");
            report.append("  Implemented by:\n");
            for (SecurityControl control : requirementToControls.get(requirement)) {
                report.append("    • ").append(control.name()).append("\n");
                report.append("      ").append(control.getDescription()).append("\n");
            }
            report.append("\n");
        }

        report.append("Total requirements covered: ").append(sortedReqs.size()).append("\n\n");

        return report.toString();
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
