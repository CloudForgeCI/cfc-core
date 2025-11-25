package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.ComplianceMode;
import com.cloudforgeci.api.interfaces.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * SOC 2 (Service Organization Control 2) Trust Services Criteria compliance validation.
 *
 * SOC 2 is based on five Trust Services Criteria (TSC):
 * - Security (Common Criteria - CC)
 * - Availability (A)
 * - Processing Integrity (PI)
 * - Confidentiality (C)
 * - Privacy (P)
 *
 * This validator focuses on the Security criteria (Common Criteria) which apply to all SOC 2 reports.
 * Organizations can choose additional criteria based on their services.
 *
 * Trust Services Criteria Coverage:
 * - CC6.1: Logical and Physical Access Controls
 * - CC6.2: Access Management
 * - CC6.6: Network Segmentation
 * - CC6.7: Data Transmission
 * - CC7.2: System Monitoring
 * - CC7.3: Environmental Protections (Availability)
 * - CC8.1: Change Management
 */
public final class Soc2Rules {
    private static final Logger LOG = Logger.getLogger(Soc2Rules.class.getName());

    private Soc2Rules() {}

    /**
     * Install SOC 2 compliance validation rules.
     * SOC 2 applies to production and staging environments serving customers.
     */
    public static void install(SystemContext ctx) {
        // SOC 2 typically applies to production and staging
        if (ctx.security != SecurityProfile.PRODUCTION && ctx.security != SecurityProfile.STAGING) {
            LOG.info("SOC 2 validation rules typically apply to PRODUCTION and STAGING profiles");
            return;
        }

        LOG.info("Installing SOC 2 Trust Services Criteria compliance validation for " + ctx.security);

        // Determine compliance mode
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        LOG.info("  Compliance mode: " + complianceMode +
                 (complianceModeStr == null ? " (default for " + ctx.security + ")" : " (explicit)"));

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Common Criteria - Security
            rules.addAll(validateAccessControls(ctx));
            rules.addAll(validateNetworkSecurity(ctx));
            rules.addAll(validateSystemMonitoring(ctx));
            rules.addAll(validateChangeManagement(ctx));

            // Availability Criteria
            rules.addAll(validateAvailability(ctx));

            // Confidentiality Criteria
            rules.addAll(validateConfidentiality(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            // Convert to error strings
            List<String> errors = failedRules.stream()
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();

            if (!errors.isEmpty()) {
                if (complianceMode == ComplianceMode.ADVISORY) {
                    // Advisory mode: Log warnings but don't fail synthesis
                    LOG.warning("SOC 2 validation found " + errors.size() + " recommendations (ADVISORY mode - not blocking)");
                    errors.forEach(err -> LOG.warning("  - " + err));
                    return List.of(); // Return empty list = no CDK synthesis errors
                } else {
                    // Enforce mode: Fail synthesis
                    LOG.severe("SOC 2 validation failed with " + errors.size() + " violations (ENFORCE mode - blocking deployment)");
                    errors.forEach(err -> LOG.severe("  - " + err));
                    return errors; // Return errors = CDK synthesis fails
                }
            } else {
                LOG.info("SOC 2 Trust Services Criteria validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * CC6.1 & CC6.2: Logical and Physical Access Controls.
     * The entity implements logical access security software, infrastructure, and architectures
     * over protected information assets to protect them from security events.
     */
    private static List<ComplianceRule> validateAccessControls(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // CC6.1: Access controls must be implemented
        if (ctx.iamProfile == null) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.1-IAM",
                "IAM access controls required",
                "IAMPasswordPolicyRule",
                "Implement role-based access control (RBAC) to restrict access to system components."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.1-IAM", "IAM access controls enabled", "IAMPasswordPolicyRule"));
        }

        // CC6.2: Authentication required
        String authMode = ctx.cfc.authMode();
        if ("none".equals(authMode)) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.2-Auth",
                "User authentication required for customer-facing systems",
                "Configure authMode = 'alb-oidc' or 'jenkins-oidc' to authenticate users."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.2-Auth", "User authentication enabled"));
        }

        // CC6.1: Encryption for data protection
        if (!config.isEbsEncryptionEnabled() || !config.isEfsEncryptionAtRestEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.1-Encryption",
                "Encryption at rest required to protect confidential data",
                "EbsEncryptionRule",
                "Enable encryption for all storage volumes."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.1-Encryption", "Encryption at rest enabled", "EbsEncryptionRule"));
        }

        return rules;
    }

    /**
     * CC6.6 & CC6.7: Network Segmentation and Data Transmission.
     * The entity implements network segmentation and encrypts data in transmission.
     */
    private static List<ComplianceRule> validateNetworkSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // CC6.6: Network segmentation
        if (ctx.vpc.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.6-VPC",
                "Network segmentation required",
                "Implement VPC with proper subnet isolation."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.6-VPC", "VPC network segmentation enabled"));
        }

        if (ctx.albSg.get().isEmpty() || ctx.efsSg.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.6-SG",
                "Security groups required for network access control",
                "Configure security groups to restrict traffic between components."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.6-SG", "Security groups configured"));
        }

        // CC6.7: Encryption in transit
        if (ctx.cert.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.7-TLS",
                "TLS certificate required for encrypted data transmission",
                "Configure HTTPS for all customer-facing endpoints."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.7-TLS", "TLS certificate configured"));
        }

        if (!config.isEfsEncryptionInTransitEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.7-EFS",
                "Encryption in transit required for internal data transfer",
                "Enable EFS in-transit encryption."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.7-EFS", "EFS in-transit encryption enabled"));
        }

        // CC6.6: WAF for web application protection (recommended)
        if (!config.isWafEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC6.6-WAF",
                "Web Application Firewall recommended for production systems",
                "Enable WAF to protect against common web attacks."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC6.6-WAF", "WAF protection enabled"));
        }

        return rules;
    }

    /**
     * CC7.2: System Monitoring.
     * The entity monitors system components and the operation of those components
     * for anomalies indicative of malicious acts, natural disasters, and errors.
     */
    private static List<ComplianceRule> validateSystemMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // CC7.2: Security monitoring required
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC7.2-Monitoring",
                "Security monitoring required for anomaly detection",
                "Enable CloudWatch alarms for security events."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC7.2-Monitoring", "Security monitoring enabled"));
        }

        // CC7.2: Threat detection
        if (!config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC7.2-GuardDuty",
                "Threat detection system required",
                "Enable GuardDuty for continuous threat monitoring."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC7.2-GuardDuty", "GuardDuty threat detection enabled"));
        }

        // CC7.2: Log collection for monitoring
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC7.2-CloudTrail",
                "Audit logging required for system monitoring",
                "CloudTrailEnabledRule",
                "Enable CloudTrail to record all API activity."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC7.2-CloudTrail", "CloudTrail audit logging enabled", "CloudTrailEnabledRule"));
        }

        if (!config.isFlowLogsEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC7.2-FlowLogs",
                "Network monitoring required",
                "Enable VPC Flow Logs to monitor network traffic."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC7.2-FlowLogs", "VPC Flow Logs enabled"));
        }

        // CC7.2: Configuration compliance monitoring
        if (!config.isAwsConfigEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC7.2-Config",
                "Configuration compliance monitoring recommended",
                "Enable AWS Config to detect configuration drift and non-compliant resources."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC7.2-Config", "AWS Config compliance monitoring enabled"));
        }

        return rules;
    }

    /**
     * CC8.1: Change Management.
     * The entity authorizes, designs, develops or acquires, configures, documents,
     * tests, approves, and implements changes to infrastructure, data, software,
     * and procedures to meet its objectives.
     */
    private static List<ComplianceRule> validateChangeManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // CC8.1: Change tracking through CloudTrail
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC8.1-CloudTrail",
                "CloudTrail required for change tracking and audit",
                "CloudTrailEnabledRule",
                "Enable CloudTrail to record all infrastructure changes."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC8.1-CloudTrail", "CloudTrail change tracking enabled", "CloudTrailEnabledRule"));
        }

        // CC8.1: Configuration change detection
        if (!config.isAwsConfigEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-CC8.1-Config",
                "AWS Config recommended for change detection",
                "Enable AWS Config to track configuration changes and maintain compliance."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-CC8.1-Config", "AWS Config change detection enabled"));
        }

        // CC8.1: Infrastructure as Code provides change control
        rules.add(ComplianceRule.pass("SOC2-CC8.1-IaC", "Infrastructure as Code (CDK) provides version-controlled change management"));
        LOG.info("SOC 2 CC8.1: Infrastructure as Code (CDK) provides version-controlled change management");

        return rules;
    }

    /**
     * A1.1, A1.2, A1.3: System Availability.
     * The entity maintains, monitors, and evaluates system availability.
     * (Only validated if organization claims Availability criteria)
     */
    private static List<ComplianceRule> validateAvailability(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Skip availability checks for non-production
        if (ctx.security != SecurityProfile.PRODUCTION) {
            return rules;
        }

        // A1.2: High availability configuration
        if (!config.isMultiAzEnforced()) {
            rules.add(ComplianceRule.fail(
                "SOC2-A1.2-MultiAZ",
                "Multi-AZ deployment required for high availability in production",
                "Deploy across multiple availability zones."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-A1.2-MultiAZ", "Multi-AZ high availability enabled"));
        }

        // A1.2: Auto-scaling for availability
        if (!config.isAutoScalingEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-A1.2-AutoScaling",
                "Auto-scaling recommended for handling demand and maintaining availability",
                "Enable auto-scaling to handle traffic spikes."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-A1.2-AutoScaling", "Auto-scaling enabled"));
        }

        // A1.3: Backup and recovery
        if (!config.isAutomatedBackupEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-A1.3-Backup",
                "Automated backups required for disaster recovery",
                "Enable automated backups to ensure data can be recovered."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-A1.3-Backup", "Automated backups enabled"));
        }

        if (!config.isCrossRegionBackupEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-A1.3-CrossRegion",
                "Cross-region backup recommended for disaster recovery",
                "Implement geographic redundancy for business continuity."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-A1.3-CrossRegion", "Cross-region backup enabled"));
        }

        return rules;
    }

    /**
     * C1.1, C1.2: Confidentiality.
     * The entity protects confidential information to meet commitments and system requirements.
     * (Only validated if organization claims Confidentiality criteria)
     */
    private static List<ComplianceRule> validateConfidentiality(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // C1.1: Encryption for confidential data
        if (!config.isEbsEncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-C1.1-EBS",
                "EBS encryption required for confidential data at rest",
                "EbsEncryptionRule",
                "Enable EBS encryption."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-C1.1-EBS", "EBS encryption enabled", "EbsEncryptionRule"));
        }

        if (!config.isEfsEncryptionAtRestEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-C1.1-EFS",
                "EFS encryption required for confidential file storage",
                "Enable EFS encryption at rest."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-C1.1-EFS", "EFS encryption enabled"));
        }

        if (!config.isS3EncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "SOC2-C1.1-S3",
                "S3 encryption required for confidential data in object storage",
                "S3BucketEncryptionRule",
                "Enable S3 encryption."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-C1.1-S3", "S3 encryption enabled", "S3BucketEncryptionRule"));
        }

        // C1.2: Access restrictions for confidential data
        if ("public-no-nat".equals(ctx.cfc.networkMode())) {
            rules.add(ComplianceRule.fail(
                "SOC2-C1.2-Network",
                "Private network mode required for confidential data",
                "Use 'private-with-nat' to restrict access to confidential systems."
            ));
        } else {
            rules.add(ComplianceRule.pass("SOC2-C1.2-Network", "Private network mode configured"));
        }

        return rules;
    }

    /**
     * Generate SOC 2 Trust Services Criteria compliance report.
     */
    public static String generateComplianceReport(SystemContext ctx) {
        StringBuilder report = new StringBuilder();
        report.append("\n=== SOC 2 Trust Services Criteria Compliance Report == = \n\n");

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        report.append("Security Profile: ").append(ctx.security).append("\n");
        report.append("Service Type: ").append(ctx.cfc.tier()).append("\n\n");

        report.append("Common Criteria - Security (CC):\n");
        report.append("  ✓ CC6.1 - Access Controls: ").append(ctx.iamProfile != null ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ CC6.2 - Authentication: ").append(!"none".equals(ctx.cfc.authMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ CC6.6 - Network Segmentation: ").append(ctx.vpc.get().isPresent() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ CC6.7 - Encryption in Transit: ").append(ctx.cert.get().isPresent() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ CC7.2 - System Monitoring: ").append(config.isSecurityMonitoringEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ CC8.1 - Change Management: ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        if (ctx.security == SecurityProfile.PRODUCTION) {
            report.append("Availability Criteria (A):\n");
            report.append("  ✓ A1.2 - High Availability: ").append(config.isMultiAzEnforced() ? "ENABLED" : "DISABLED").append("\n");
            report.append("  ✓ A1.2 - Auto-Scaling: ").append(config.isAutoScalingEnabled() ? "ENABLED" : "DISABLED").append("\n");
            report.append("  ✓ A1.3 - Backup & Recovery: ").append(config.isAutomatedBackupEnabled() ? "ENABLED" : "DISABLED").append("\n");
            report.append("\n");
        }

        report.append("Confidentiality Criteria (C):\n");
        report.append("  ✓ C1.1 - Encryption at Rest: ").append(config.isEbsEncryptionEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ C1.2 - Access Restrictions: ").append(!"public-no-nat".equals(ctx.cfc.networkMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Note: SOC 2 audit requires independent CPA firm examination.\n");
        report.append("Type I: Design of controls | Type II: Operating effectiveness over time\n");
        report.append("\n");

        return report.toString();
    }
}
