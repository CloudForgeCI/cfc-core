package com.cloudforgeci.api.core.rules;


import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.interfaces.FrameworkRules;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.Set;

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

    /** Controls that still block STAGING synthesis: authentication, network isolation,
     *  SSL/TLS in transit, and data residency. Data residency is a legal boundary on where
     *  data may live (Art. 44-50), not a soft finding, so it stays blocking alongside network
     *  isolation. Everything else in STAGING is a visible, non-blocking finding. */
    private static final Set<String> STAGING_BLOCKING_RULES = Set.of(
        "GDPR-AUTHENTICATION", "GDPR-ACCESS-CONTROL", "GDPR-NETWORK-ISOLATION",
        "GDPR-SSL-ENCRYPTION", "GDPR-TLS-ENCRYPTION", "GDPR-DATA-RESIDENCY"
    );


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
            // Get compliance mode (already resolved to enum with proper default)
            ComplianceMode complianceMode = ctx.cfc.complianceMode();

            // Collect all validation results
            List<ComplianceRule> rules = new ArrayList<>();

            // GDPR Chapter V: Data Residency and Cross-Border Transfers (Articles 44-50)
            rules.addAll(validateDataResidency(ctx));

            // Article 25: Data Protection by Design and by Default
            rules.addAll(validateDataProtectionByDesign(ctx));

            // Article 30: Records of Processing Activities
            rules.addAll(validateProcessingRecords(ctx));

            // Article 32: Security of Processing
            rules.addAll(validateSecurityMeasures(ctx));

            // Article 33: Breach Detection and Notification
            rules.addAll(validateBreachDetection(ctx));

            // Matrix-required controls not covered above
            rules.addAll(validateMatrixControls(ctx));

            // Advisory findings never block synthesis, logged regardless of complianceMode so they
            // show up in the compliance report and the matrix runner's log capture.
            List<ComplianceRule> advisoryRules = rules.stream().filter(ComplianceRule::isAdvisory).toList();
            if (!advisoryRules.isEmpty()) {
                LOG.info("GDPR validation found " + advisoryRules.size() + " advisory recommendations (non-blocking):");
                advisoryRules.forEach(r -> LOG.info("  [ADVISORY] " + r.ruleId() + ": " + r.description()));
            }

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
                    ComplianceFindingsCollector.record(failedRules);
                    return List.of(); // No errors = synthesis proceeds
                } else if (ctx.security == SecurityProfile.STAGING) {
                    // STAGING runs the same checks as PRODUCTION; only authentication, network
                    // isolation and SSL/TLS still block. Everything else is a visible finding.
                    List<String> blocking = failedRules.stream()
                        .filter(rule -> STAGING_BLOCKING_RULES.contains(rule.ruleId()))
                        .map(ComplianceRule::toErrorString)
                        .flatMap(Optional::stream)
                        .toList();
                    LOG.warning("GDPR validation found " + errors.size() + " violations (STAGING - "
                        + blocking.size() + " blocking):");
                    errors.forEach(error -> LOG.warning("  - " + error));
                    ComplianceFindingsCollector.record(failedRules);
                    return blocking;
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
     * GDPR Chapter V: Data Residency and Cross-Border Transfers (Articles 44-50).
     * GDPR requires that personal data of EU residents be processed in the EU or in countries
     * with adequate data protection unless proper transfer mechanisms are in place.
     */
    private List<ComplianceRule> validateDataResidency(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String region = ctx.cfc.region();
        boolean isEuRegion = isEuropeanUnionRegion(region);

        // Check if gdprDataTransferApproved flag is set
        boolean transferApproved = Optional.ofNullable(ctx.cfc.gdprDataTransferApproved())
            .orElse(false);

        if (!isEuRegion && !transferApproved) {
            rules.add(ComplianceRule.fail(
                "GDPR-DATA-RESIDENCY",
                "GDPR requires EU data residency or approved transfer mechanisms (Art. 44-50)",
                "DataResidencyRule",
                "Deploying to non-EU region '" + region + "' without approved data transfer mechanism. " +
                "Either deploy to an EU region (eu-west-1, eu-central-1, etc.) or set " +
                "gdprDataTransferApproved=true to confirm Standard Contractual Clauses (SCCs) or " +
                "other valid transfer mechanisms are in place."
            ));
        } else if (!isEuRegion && transferApproved) {
            rules.add(ComplianceRule.pass(
                "GDPR-DATA-RESIDENCY",
                "GDPR data transfer approved for non-EU region (Art. 44-50): " + region,
                "DataResidencyRule"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-DATA-RESIDENCY",
                "GDPR compliant EU region deployment (Art. 44-50): " + region,
                "DataResidencyRule"
            ));
        }

        return rules;
    }

    /**
     * Check if the given AWS region is in the European Union.
     */
    private boolean isEuropeanUnionRegion(String region) {
        return region != null && region.startsWith("eu-");
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
        if (ctx.cfc.networkMode() == NetworkMode.PUBLIC
                && (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING)) {
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

        // Art. 30(1) & Art. 32(1)(d): Log retention for audit trail and security assessment
        // GDPR doesn't specify exact retention period, but requires adequate retention
        // for maintaining records of processing activities and security monitoring
        // Industry standard: 90 days minimum for security logs (aligns with SOC2 CC7.2)
        var retentionDays = config.getLogRetentionDays();
        if (!isRetentionSufficient(retentionDays)) {
            rules.add(ComplianceRule.fail(
                "GDPR-LOG-RETENTION",
                "Log retention must be adequate for processing records and security assessment (GDPR Art. 30(1) & 32(1)(d))",
                "CloudWatchLogGroupRetention",
                "Log retention must be at least 90 days (THREE_MONTHS) to maintain adequate records of processing activities. Current: " +
                retentionDays.toString() + ". " +
                "GDPR Article 30(1) requires maintaining records of processing activities, and Article 32(1)(d) " +
                "requires ability to regularly test and evaluate security measures."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "GDPR-LOG-RETENTION",
                "Log retention adequate for processing records and security assessment (GDPR Art. 30(1) & 32(1)(d))",
                "CloudWatchLogGroupRetention"
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
        AuthMode authMode = ctx.cfc.authMode();
        if (authMode == AuthMode.NONE) {
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
        boolean isProdOrStaging = ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING;
        if (!config.isAutomatedBackupEnabled() && isProdOrStaging) {
            rules.add(ComplianceRule.fail(
                "GDPR-AUTOMATED-BACKUP",
                "Automated backups required to restore availability (GDPR Art. 32(1)(c))",
                "EfsBackupEnabled",
                "Automated backups are disabled for production. Enable automated backups to ensure personal data can be recovered."
            ));
        } else if (isProdOrStaging) {
            rules.add(ComplianceRule.pass(
                "GDPR-AUTOMATED-BACKUP",
                "Automated backups required to restore availability (GDPR Art. 32(1)(c))",
                "EfsBackupEnabled"
            ));
        }

        // Art. 32(1)(d): Testing and evaluation through Config
        if (!config.isAwsConfigEnabled() && isProdOrStaging) {
            rules.add(ComplianceRule.fail(
                "GDPR-AWS-CONFIG",
                "AWS Config recommended for regularly assessing security measures (GDPR Art. 32(1)(d))",
                "ConfigEnabled",
                "AWS Config is disabled for production. Enable AWS Config to continuously evaluate security configurations."
            ));
        } else if (isProdOrStaging) {
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
        // NOTE: GuardDuty validation is now handled by ThreatProtectionRules using ComplianceMatrix
        // which marks it as ADVISORY for GDPR (recommended but not required)
        if (config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.pass(
                "GDPR-GUARDDUTY",
                "GuardDuty enabled for breach detection (GDPR Art. 33(1))",
                "GuardDutyEnabled"
            ));
        } else {
            rules.add(ComplianceRule.advisory(
                "GDPR-GUARDDUTY",
                "GuardDuty is advisory for GDPR (recommended but not required, Art. 33(1))",
                "GuardDutyEnabled",
                "Enable guardDutyEnabled for automated breach detection."
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
        boolean isProdOrStaging = ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING;
        if (!config.isWafEnabled() && isProdOrStaging) {
            rules.add(ComplianceRule.fail(
                "GDPR-WAF-PROTECTION",
                "WAF recommended to prevent web-based data breaches (GDPR Art. 32(1))",
                "WafEnabled",
                "WAF is disabled for production. Enable WAF to protect against common attack vectors."
            ));
        } else if (isProdOrStaging) {
            rules.add(ComplianceRule.pass(
                "GDPR-WAF-PROTECTION",
                "WAF recommended to prevent web-based data breaches (GDPR Art. 32(1))",
                "WafEnabled"
            ));
        }

        return rules;
    }

    /**
     * Check if log retention meets GDPR requirement (90 days minimum).
     * GDPR Art. 30(1): Maintain records of processing activities.
     * GDPR Art. 32(1)(d): Regularly test, assess, and evaluate effectiveness of security measures.
     *
     * While GDPR doesn't specify exact retention periods, it requires adequate retention
     * to maintain records of processing activities and assess security measures.
     * Industry standard: 90 days minimum (THREE_MONTHS or longer).
     */
    private boolean isRetentionSufficient(RetentionDays retention) {
        // GDPR requires adequate retention for processing records and security assessment
        // Industry standard: 90 days minimum (THREE_MONTHS or longer) - aligns with SOC2 CC7.2
        return retention == RetentionDays.THREE_MONTHS ||
               retention == RetentionDays.FOUR_MONTHS ||
               retention == RetentionDays.FIVE_MONTHS ||
               retention == RetentionDays.SIX_MONTHS ||
               retention == RetentionDays.ONE_YEAR ||
               retention == RetentionDays.THIRTEEN_MONTHS ||
               retention == RetentionDays.EIGHTEEN_MONTHS ||
               retention == RetentionDays.TWO_YEARS ||
               retention == RetentionDays.THREE_YEARS ||
               retention == RetentionDays.FIVE_YEARS ||
               retention == RetentionDays.SIX_YEARS ||
               retention == RetentionDays.SEVEN_YEARS ||
               retention == RetentionDays.EIGHT_YEARS ||
               retention == RetentionDays.NINE_YEARS ||
               retention == RetentionDays.TEN_YEARS ||
               retention == RetentionDays.INFINITE;
    }

    /**
     * Controls that {@link ComplianceMatrix} marks REQUIRED for GDPR and that the checks above do
     * not already cover.
     */
    private List<ComplianceRule> validateMatrixControls(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Production-tier controls: STAGING leaves them off by design, matching the pattern used
        // for the equivalent production-only checks in Soc2Rules/HipaaRules.
        if (ctx.security == SecurityProfile.PRODUCTION) {
            // Art. 32(1)(a): CloudWatch Logs may contain personal data in application log output.
            if (!config.isCloudWatchLogsKmsEncryptionEnabled()) {
                rules.add(ComplianceRule.fail(
                    "GDPR-Art32-LogEncryption",
                    "CloudWatch log groups must be encrypted with a customer-managed KMS key (Art. 32(1)(a))",
                    "Enable cloudWatchLogsKmsEncryptionEnabled."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "GDPR-Art32-LogEncryption",
                    "CloudWatch log groups encrypted with a customer-managed KMS key (Art. 32(1)(a))"
                ));
            }

            // Art. 32(1)(d): change control -- CloudTrail plus AWS Config together give a record of
            // every change and detect drift from the deployed baseline.
            if (config.isCloudTrailEnabled() && config.isAwsConfigEnabled()) {
                rules.add(ComplianceRule.pass(
                    "GDPR-Art32-ChangeManagement",
                    "Change tracking enabled via CloudTrail and AWS Config (Art. 32(1)(d))"
                ));
            } else {
                rules.add(ComplianceRule.fail(
                    "GDPR-Art32-ChangeManagement",
                    "Change control tracking required (Art. 32(1)(d))",
                    "Enable both CloudTrail and AWS Config to track and detect infrastructure changes."
                ));
            }
        }

        // EC2_IMDSV2 is ADVISORY for GDPR (an access-security measure, not itself an Art. 32
        // requirement); only checked for EC2 -- Fargate has no instance metadata service.
        if (ctx.runtime == RuntimeType.EC2) {
            if (config.isImdsv2Required()) {
                rules.add(ComplianceRule.pass(
                    "GDPR-IMDSV2",
                    "EC2 instances require IMDSv2 tokens (Art. 32(1)(b) access security)"
                ));
            } else {
                rules.add(ComplianceRule.advisory(
                    "GDPR-IMDSV2",
                    "IMDSv2 is advisory for GDPR (recommended access-security measure, not required)",
                    "Set imdsv2Required = true to block the legacy IMDSv1 endpoint."
                ));
            }
        }

        return rules;
    }

    /**
     * Controls checked across every {@code validate*} method above -- see {@link
     * com.cloudforge.core.interfaces.FrameworkRules#claimedControls}. GuardDuty is read in
     * {@link #validateBreachDetection}, now producing an advisory finding (not just a pass) when
     * off, since it's ADVISORY for GDPR per ComplianceMatrix. HTTPS_STRICT and SECURITY_HUB are
     * ADVISORY for GDPR per the matrix and unchecked in this class. LAMBDA_SECURITY and
     * DATABASE_ACCESS_CONTROL are genuine gaps -- no class anywhere checks them.
     */
    @Override
    public Set<String> claimedControls() {
        return Set.of(
            "ENCRYPTION_AT_REST", "ACCESS_CONTROL", "NETWORK_SEGMENTATION", "AUDIT_LOGGING",
            "NETWORK_FLOW_LOGS", "LOG_RETENTION", "ENCRYPTION_IN_TRANSIT", "AUTHENTICATION",
            "SECURITY_MONITORING", "BACKUP_RECOVERY", "VULNERABILITY_MANAGEMENT", "WAF_PROTECTION",
            "CLOUDWATCH_LOGS_KMS_ENCRYPTION", "CHANGE_MANAGEMENT"
        );
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
        report.append("  ✓ Network Isolation (Art. 25(1)): ").append(ctx.cfc.networkMode() != NetworkMode.PUBLIC ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Article 30 - Records of Processing:\n");
        report.append("  ✓ CloudTrail Logging (Art. 30(1)): ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ VPC Flow Logs (Art. 30(1)): ").append(config.isFlowLogsEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Access Logs (Art. 30(1)): ").append(config.isAlbAccessLoggingEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Article 32 - Security of Processing:\n");
        report.append("  ✓ Encryption in Transit (Art. 32(1)(a)): ").append(ctx.cert.get().isPresent() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Authentication (Art. 32(1)(b)): ").append(ctx.cfc.authMode() != AuthMode.NONE ? "ENABLED" : "DISABLED").append("\n");
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

        appendAdvisorySection(report, ctx);

        return report.toString();
    }

    /**
     * Recommendations for controls that are ADVISORY-tier (not blocking) and currently off.
     * Runs the same checks {@link #install} does, so this reflects live findings, not a
     * separate hand-maintained list.
     */
    private void appendAdvisorySection(StringBuilder report, SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();
        rules.addAll(validateDataResidency(ctx));
        rules.addAll(validateDataProtectionByDesign(ctx));
        rules.addAll(validateProcessingRecords(ctx));
        rules.addAll(validateSecurityMeasures(ctx));
        rules.addAll(validateBreachDetection(ctx));
        rules.addAll(validateMatrixControls(ctx));

        List<ComplianceRule> advisories = rules.stream().filter(ComplianceRule::isAdvisory).toList();
        if (advisories.isEmpty()) {
            return;
        }
        report.append("Recommendations (advisory, non-blocking):\n");
        for (ComplianceRule rule : advisories) {
            report.append("  - ").append(rule.ruleId()).append(": ").append(rule.description()).append("\n");
            rule.recommendation().ifPresent(r -> report.append("      ").append(r).append("\n"));
        }
        report.append("\n");
    }
}
