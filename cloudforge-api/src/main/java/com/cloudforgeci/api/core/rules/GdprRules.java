package com.cloudforgeci.api.core.rules;


import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * GDPR (General Data Protection Regulation) compliance validation.
 *
 * GDPR (EU) 2016/679 requires technical and organizational measures to protect personal data.
 * This validator focuses on technical measures that can be implemented at the infrastructure level.
 *
 * GDPR Technical Requirements Coverage:
 * - Article 25: Data Protection by Design and by Default
 * - Article 30: Records of Processing Activities
 * - Article 32: Security of Processing
 * - Article 33: Notification of Personal Data Breach
 * - Article 35: Data Protection Impact Assessment
 *
 * Note: GDPR includes many organizational and legal requirements (consent, data subject rights,
 * privacy policies) that must be implemented at the application and business process level.
 * This validator covers infrastructure-level technical safeguards only.
 */
@ComplianceFramework(
    value = "GDPR",
    priority = 30,
    displayName = "GDPR",
    description = "Validates GDPR technical safeguards for personal data protection"
)
public class GdprRules implements FrameworkRules<SystemContext> {
    private static final Logger LOG = Logger.getLogger(GdprRules.class.getName());


    /**
     * Install GDPR compliance validation rules.
     * GDPR applies when processing personal data of EU residents.
     * Only enforced for PRODUCTION and STAGING environments.
     *
     * @since 3.0.0
     */
    @Override
    public void install(SystemContext ctx) {
        // Only enforce GDPR validation for production and staging
        if (ctx.security != SecurityProfile.PRODUCTION && ctx.security != SecurityProfile.STAGING) {
            LOG.info("GDPR validation rules only enforced for PRODUCTION and STAGING profiles");
            return;
        }

        LOG.info("Installing GDPR technical safeguards validation for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            // Read compliance mode from deployment context
            ComplianceMode complianceMode = ComplianceMode.fromString(
                ctx.cfc.complianceMode(),
                ComplianceMode.defaultForProfile(ctx.security)
            );

            // Collect all validation results
            List<ComplianceRule> rules = new ArrayList<>();

            // Article 25: Data Protection by Design and by Default
            rules.addAll(validateDataProtectionByDesign(ctx));

            // Article 30: Records of Processing Activities
            rules.addAll(validateProcessingRecords(ctx));

            // Article 32: Security of Processing
            rules.addAll(validateSecurityMeasures(ctx));

            // Article 33: Breach Detection and Notification
            rules.addAll(validateBreachDetection(ctx));

            // Filter to only failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            // Convert to error strings
            List<String> errors = failedRules.stream()
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();

            // Log results based on compliance mode
            if (!errors.isEmpty()) {
                if (complianceMode == ComplianceMode.ADVISORY) {
                    LOG.warning("GDPR validation found " + errors.size() + " recommendations (ADVISORY mode - not blocking):");
                    errors.forEach(error -> LOG.warning("  - " + error));
                    return List.of(); // No errors = synthesis proceeds
                } else {
                    LOG.severe("GDPR validation failed with " + errors.size() + " violations (ENFORCE mode - blocking deployment):");
                    errors.forEach(error -> LOG.severe("  - " + error));
                    return errors;
                }
            } else {
                LOG.info("GDPR technical safeguards validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Article 25: Data Protection by Design and by Default.
     * Implement appropriate technical and organizational measures to ensure that, by default,
     * only personal data necessary for each specific purpose is processed.
     */
    private List<ComplianceRule> validateDataProtectionByDesign(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Art. 25(1): Pseudonymization and encryption - EBS
        if (!config.isEbsEncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-EBS-ENCRYPTION",
                "EBS volumes must be encrypted (GDPR Art. 25(1) & 32(1)(a))",
                "EbsEncryptionRule",
                "EBS encryption is disabled. Enable encryption at rest to protect personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-EBS-ENCRYPTION",
                "EBS volumes must be encrypted (GDPR Art. 25(1) & 32(1)(a))",
                "EbsEncryptionRule"
            ));
        }

        // Art. 25(1): Pseudonymization and encryption - EFS
        if (!config.isEfsEncryptionAtRestEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-EFS-ENCRYPTION",
                "EFS encryption required for data protection by design (GDPR Art. 25(1) & 32(1)(a))",
                "EfsEncryptionRule",
                "EFS encryption at rest is disabled. Enable encryption for file storage containing personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-EFS-ENCRYPTION",
                "EFS encryption required for data protection by design (GDPR Art. 25(1) & 32(1)(a))",
                "EfsEncryptionRule"
            ));
        }

        // Art. 25(1): Pseudonymization and encryption - S3
        if (!config.isS3EncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-S3-ENCRYPTION",
                "S3 encryption required for data protection by design (GDPR Art. 25(1) & 32(1)(a))",
                "S3BucketEncryptionRule",
                "S3 encryption is disabled. Enable encryption for object storage containing personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-S3-ENCRYPTION",
                "S3 encryption required for data protection by design (GDPR Art. 25(1) & 32(1)(a))",
                "S3BucketEncryptionRule"
            ));
        }

        // Art. 25(2): Data minimization through access controls
        if (ctx.iamProfile == null) {
            rules.add(ComplianceRule.fail(
                "GDPR-ACCESS-CONTROL",
                "Access controls required for data minimization (GDPR Art. 25(2))",
                "IAMPasswordPolicyRule",
                "IAM profile not configured. Implement least-privilege access to ensure only necessary data is accessed."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-ACCESS-CONTROL",
                "Access controls required for data minimization (GDPR Art. 25(2))",
                "IAMPasswordPolicyRule"
            ));
        }

        // Art. 25: Network isolation for data protection
        if ("public-no-nat".equals(ctx.cfc.networkMode()) && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "GDPR-NETWORK-ISOLATION",
                "Private network mode recommended for production systems (GDPR Art. 25(1))",
                "Network mode is public. Use 'private-with-nat' to implement data protection by design for systems processing personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-NETWORK-ISOLATION",
                "Private network mode recommended for production systems (GDPR Art. 25(1))"
            ));
        }

        return rules;
    }

    /**
     * Article 30: Records of Processing Activities.
     * Maintain records of all processing activities including security measures.
     */
    private List<ComplianceRule> validateProcessingRecords(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Art. 30(1)(g): Technical and organizational measures - CloudTrail
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-CLOUDTRAIL",
                "CloudTrail required to maintain records of processing activities (GDPR Art. 30(1))",
                "CloudTrailEnabledRule",
                "CloudTrail is disabled. Enable CloudTrail to document all API operations on personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-CLOUDTRAIL",
                "CloudTrail required to maintain records of processing activities (GDPR Art. 30(1))",
                "CloudTrailEnabledRule"
            ));
        }

        // VPC Flow Logs document network processing
        if (!config.isFlowLogsEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-FLOW-LOGS",
                "VPC Flow Logs recommended to document data flows (GDPR Art. 30(1))",
                "VpcFlowLogsEnabled",
                "VPC Flow Logs are disabled. Enable Flow Logs to maintain records of network activity."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-FLOW-LOGS",
                "VPC Flow Logs recommended to document data flows (GDPR Art. 30(1))",
                "VpcFlowLogsEnabled"
            ));
        }

        // Access logs for data processing tracking
        if (!config.isAlbAccessLoggingEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-ALB-LOGGING",
                "ALB access logging recommended to record data access (GDPR Art. 30(1))",
                "AlbAccessLogsEnabled",
                "ALB access logging is disabled. Enable access logs to document requests processing personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-ALB-LOGGING",
                "ALB access logging recommended to record data access (GDPR Art. 30(1))",
                "AlbAccessLogsEnabled"
            ));
        }

        return rules;
    }

    /**
     * Article 32: Security of Processing.
     * Implement appropriate technical and organizational measures to ensure security appropriate
     * to the risk, including:
     * (a) pseudonymization and encryption
     * (b) ability to ensure ongoing confidentiality, integrity, availability, resilience
     * (c) ability to restore availability and access in timely manner
     * (d) process for regularly testing, assessing, evaluating effectiveness
     */
    private List<ComplianceRule> validateSecurityMeasures(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Art. 32(1)(a): SSL/TLS must be enabled for encrypted transmission of personal data
        if (!ctx.cfc.enableSsl()) {
            rules.add(ComplianceRule.fail(
                "GDPR-SSL-ENCRYPTION",
                "SSL/TLS must be enabled for data in transit (GDPR Art. 32(1)(a))",
                "ALBHttpsOnly",
                "Set enableSsl=true for production GDPR compliance to protect personal data in transit."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-SSL-ENCRYPTION",
                "SSL/TLS enabled for data in transit (GDPR Art. 32(1)(a))",
                "ALBHttpsOnly"
            ));
        }

        // Art. 32(1)(a): Encryption of personal data - TLS certificate
        if (ctx.cert.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "GDPR-TLS-ENCRYPTION",
                "TLS certificate required for data in transit (GDPR Art. 32(1)(a))",
                "ALBHttpsOnly",
                "TLS certificate not configured. Configure HTTPS to encrypt transmission of personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-TLS-ENCRYPTION",
                "TLS certificate configured for data in transit (GDPR Art. 32(1)(a))",
                "ALBHttpsOnly"
            ));
        }

        // Art. 32(1)(a): Encryption of personal data - EFS in transit
        if (!config.isEfsEncryptionInTransitEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-EFS-TRANSIT-ENCRYPTION",
                "EFS in-transit encryption required (GDPR Art. 32(1)(a))",
                "EfsEncryptionRule",
                "EFS in-transit encryption is disabled. Enable TLS for file system access to protect personal data during transmission."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-EFS-TRANSIT-ENCRYPTION",
                "EFS in-transit encryption required (GDPR Art. 32(1)(a))",
                "EfsEncryptionRule"
            ));
        }

        // Art. 32(1)(b): Confidentiality through access controls
        String authMode = ctx.cfc.authMode();
        if ("none".equals(authMode)) {
            rules.add(ComplianceRule.fail(
                "GDPR-AUTHENTICATION",
                "Authentication required to ensure confidentiality (GDPR Art. 32(1)(b))",
                "Authentication mode is 'none'. Configure authMode = 'alb-oidc', 'jenkins-oidc', or 'application-oidc' to control access to personal data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-AUTHENTICATION",
                "Authentication required to ensure confidentiality (GDPR Art. 32(1)(b))"
            ));
        }

        // Art. 32(1)(b): Integrity through monitoring
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-SECURITY-MONITORING",
                "Security monitoring required to detect integrity violations (GDPR Art. 32(1)(b))",
                "Security monitoring is disabled. Enable CloudWatch alarms to monitor for unauthorized access."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-SECURITY-MONITORING",
                "Security monitoring required to detect integrity violations (GDPR Art. 32(1)(b))"
            ));
        }

        // Art. 32(1)(c): Backup for availability and resilience
        if (!config.isAutomatedBackupEnabled() && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "GDPR-AUTOMATED-BACKUP",
                "Automated backups required to restore availability (GDPR Art. 32(1)(c))",
                "EfsBackupEnabled",
                "Automated backups are disabled for production. Enable automated backups to ensure personal data can be recovered."
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "GDPR-AUTOMATED-BACKUP",
                "Automated backups required to restore availability (GDPR Art. 32(1)(c))",
                "EfsBackupEnabled"
            ));
        }

        // Art. 32(1)(d): Testing and evaluation through Config
        if (!config.isAwsConfigEnabled() && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "GDPR-AWS-CONFIG",
                "AWS Config recommended for regularly assessing security measures (GDPR Art. 32(1)(d))",
                "ConfigEnabled",
                "AWS Config is disabled for production. Enable AWS Config to continuously evaluate security configurations."
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "GDPR-AWS-CONFIG",
                "AWS Config recommended for regularly assessing security measures (GDPR Art. 32(1)(d))",
                "ConfigEnabled"
            ));
        }

        return rules;
    }

    /**
     * Article 33: Notification of Personal Data Breach.
     * Ability to detect and respond to data breaches within 72 hours.
     */
    private List<ComplianceRule> validateBreachDetection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Art. 33(1): Breach detection capability
        if (!config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-GUARDDUTY",
                "GuardDuty required for breach detection (GDPR Art. 33(1))",
                "GuardDutyEnabled",
                "GuardDuty is disabled. Enable GuardDuty to detect potential data breaches within required timeframe."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-GUARDDUTY",
                "GuardDuty required for breach detection (GDPR Art. 33(1))",
                "GuardDutyEnabled"
            ));
        }

        // Art. 33(1): Security monitoring for breach detection
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-BREACH-MONITORING",
                "Security monitoring required for breach notification capability (GDPR Art. 33(1))",
                "Security monitoring is disabled. Enable CloudWatch alarms to detect security incidents promptly."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-BREACH-MONITORING",
                "Security monitoring required for breach notification capability (GDPR Art. 33(1))"
            ));
        }

        // Art. 33: CloudTrail for breach investigation
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "GDPR-BREACH-INVESTIGATION",
                "CloudTrail required for breach investigation and documentation (GDPR Art. 33(3))",
                "CloudTrailEnabledRule",
                "CloudTrail is disabled. Enable CloudTrail to document the nature of breach and affected data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-BREACH-INVESTIGATION",
                "CloudTrail required for breach investigation and documentation (GDPR Art. 33(3))",
                "CloudTrailEnabledRule"
            ));
        }

        // WAF for preventing breaches
        if (!config.isWafEnabled() && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "GDPR-WAF-PROTECTION",
                "WAF recommended to prevent web-based data breaches (GDPR Art. 32(1))",
                "WafEnabled",
                "WAF is disabled for production. Enable WAF to protect against common attack vectors."
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "GDPR-WAF-PROTECTION",
                "WAF recommended to prevent web-based data breaches (GDPR Art. 32(1))",
                "WafEnabled"
            ));
        }

        return rules;
    }

    /**
     * Generate GDPR technical safeguards compliance report.
     */
    public String generateComplianceReport(SystemContext ctx) {
        StringBuilder report = new StringBuilder();
        report.append("\n=== GDPR Technical Safeguards Compliance Report ===\n\n");

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        report.append("Security Profile: ").append(ctx.security).append("\n");
        report.append("Region: ").append(ctx.cfc.region()).append("\n");
        report.append("Data Residency: AWS ").append(ctx.cfc.region()).append(" (verify EU region for EU data)\n\n");

        report.append("Article 25 - Data Protection by Design:\n");
        report.append("  ✓ Encryption at Rest (Art. 25(1)): ").append(config.isEbsEncryptionEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Access Controls (Art. 25(2)): ").append(ctx.iamProfile != null ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Network Isolation (Art. 25(1)): ").append(!"public-no-nat".equals(ctx.cfc.networkMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Article 30 - Records of Processing:\n");
        report.append("  ✓ CloudTrail Logging (Art. 30(1)): ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ VPC Flow Logs (Art. 30(1)): ").append(config.isFlowLogsEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Access Logs (Art. 30(1)): ").append(config.isAlbAccessLoggingEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Article 32 - Security of Processing:\n");
        report.append("  ✓ Encryption in Transit (Art. 32(1)(a)): ").append(ctx.cert.get().isPresent() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Authentication (Art. 32(1)(b)): ").append(!"none".equals(ctx.cfc.authMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Security Monitoring (Art. 32(1)(b)): ").append(config.isSecurityMonitoringEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Backup & Recovery (Art. 32(1)(c)): ").append(config.isAutomatedBackupEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Security Assessment (Art. 32(1)(d)): ").append(config.isAwsConfigEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Article 33 - Breach Detection:\n");
        report.append("  ✓ Threat Detection (Art. 33(1)): ").append(config.isGuardDutyEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Incident Monitoring (Art. 33(1)): ").append(config.isSecurityMonitoringEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Audit Trail (Art. 33(3)): ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Important GDPR Considerations:\n");
        report.append("  ⚠ Data Residency: Ensure deployment region complies with data transfer restrictions\n");
        report.append("  ⚠ DPA Required: Data Processing Agreement with AWS (available in AWS Artifact)\n");
        report.append("  ⚠ Application Layer: Implement consent management, data subject rights, privacy policies\n");
        report.append("  ⚠ DPO: Appoint Data Protection Officer if required (Art. 37)\n");
        report.append("  ⚠ DPIA: Conduct Data Protection Impact Assessment for high-risk processing (Art. 35)\n");
        report.append("\n");

        return report.toString();
    }
}
