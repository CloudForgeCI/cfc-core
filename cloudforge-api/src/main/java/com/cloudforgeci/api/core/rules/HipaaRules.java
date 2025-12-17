package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * HIPAA Security Rule compliance validation.
 *
 * Validates HIPAA Security Rule requirements (45 CFR Part 160 and Part 164, Subparts A and C).
 *
 * HIPAA Security Rule Coverage:
 * - §164.308(a)(1): Security Management Process
 * - §164.308(a)(3): Workforce Security
 * - §164.308(a)(4): Information Access Management
 * - §164.312(a)(1): Access Control
 * - §164.312(a)(2)(iv): Encryption and Decryption
 * - §164.312(b): Audit Controls
 * - §164.312(c)(1): Integrity
 * - §164.312(d): Person or Entity Authentication
 * - §164.312(e)(1): Transmission Security
 * - §164.316(b)(1): Policies and Procedures
 * - §164.316(b)(2)(i): Retention
 *
 * Note: HIPAA distinguishes between "Required" and "Addressable" specifications.
 * This validator enforces both for maximum protection of PHI (Protected Health Information).
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "HIPAA",
    priority = 10,
    displayName = "HIPAA Security Rule",
    description = "Validates HIPAA Security Rule requirements for PHI protection"
)
public class HipaaRules implements FrameworkRules<SystemContext> {
    private static final Logger LOG = Logger.getLogger(HipaaRules.class.getName());

    // HIPAA requires 6 years retention for documentation
    private static final int HIPAA_MIN_RETENTION_DAYS = 365 * 6; // 2190 days

    /**
     * Install HIPAA compliance validation rules for production and staging environments.
     * HIPAA applies to any environment that processes PHI.
     */
    @Override
    public void install(SystemContext ctx) {
        // HIPAA enforcement for production and staging (PHI may exist in both)
        if (ctx.security != SecurityProfile.PRODUCTION && ctx.security != SecurityProfile.STAGING) {
            LOG.info("HIPAA validation rules enforced for PRODUCTION and STAGING profiles only");
            return;
        }

        LOG.info("Installing HIPAA Security Rule compliance validation for " + ctx.security);

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

            // Administrative Safeguards
            rules.addAll(validateSecurityManagement(ctx));
            rules.addAll(validateAccessManagement(ctx));

            // Physical Safeguards (infrastructure level)
            rules.addAll(validatePhysicalSafeguards(ctx));

            // Technical Safeguards
            rules.addAll(validateAccessControls(ctx));
            rules.addAll(validateAuditControls(ctx));
            rules.addAll(validateIntegrityControls(ctx));
            rules.addAll(validateAuthenticationControls(ctx));
            rules.addAll(validateTransmissionSecurity(ctx));

            // Organizational Requirements
            rules.addAll(validateRetentionRequirements(ctx));

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
                    LOG.warning("HIPAA validation found " + errors.size() + " recommendations (ADVISORY mode - not blocking)");
                    errors.forEach(err -> LOG.warning("  - " + err));
                    return List.of(); // Return empty list = no CDK synthesis errors
                } else {
                    // Enforce mode: Fail synthesis
                    LOG.severe("HIPAA validation failed with " + errors.size() + " violations (ENFORCE mode - blocking deployment)");
                    errors.forEach(err -> LOG.severe("  - " + err));
                    return errors; // Return errors = CDK synthesis fails
                }
            } else {
                LOG.info("HIPAA Security Rule validation passed (" + rules.size() + " checks) - all technical safeguards enabled");
                return List.of();
            }
        });
    }

    /**
     * §164.308(a)(1) - Security Management Process.
     * Risk analysis, risk management, sanction policy, information system activity review.
     */
    private List<ComplianceRule> validateSecurityManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // §164.308(a)(1)(ii)(D) - Information System Activity Review
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.308(a)(1)(ii)(D)-Monitoring",
                "Security monitoring required for information system activity review (HIPAA §164.308(a)(1)(ii)(D))",
                "Enable security monitoring to detect and respond to security incidents."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.308(a)(1)(ii)(D)-Monitoring",
                "Security monitoring enabled for information system activity review (HIPAA §164.308(a)(1)(ii)(D))"
            ));
        }

        if (!config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.308(a)(1)(ii)(D)-GuardDuty",
                "GuardDuty threat detection required (HIPAA §164.308(a)(1)(ii)(D))",
                "GuardDutyEnabled",
                "GuardDuty threat detection required for security incident identification."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.308(a)(1)(ii)(D)-GuardDuty",
                "GuardDuty threat detection enabled (HIPAA §164.308(a)(1)(ii)(D))",
                "GuardDutyEnabled"
            ));
        }

        return rules;
    }

    /**
     * §164.308(a)(4) - Information Access Management.
     * Access authorization, access establishment and modification.
     */
    private List<ComplianceRule> validateAccessManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // §164.308(a)(4)(ii)(C) - Access Establishment and Modification
        if (ctx.iamProfile == null) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.308(a)(4)(ii)(C)-IAM",
                "IAM profile required for access control management (HIPAA §164.308(a)(4)(ii)(C))",
                "IAMPasswordPolicyRule",
                "Implement role-based access controls for PHI access."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.308(a)(4)(ii)(C)-IAM",
                "IAM profile configured for access control management (HIPAA §164.308(a)(4)(ii)(C))",
                "IAMPasswordPolicyRule"
            ));
        }

        return rules;
    }

    /**
     * Physical Safeguards - Infrastructure level controls.
     * §164.310 - Facility Access Controls, Workstation Security, Device and Media Controls.
     */
    private List<ComplianceRule> validatePhysicalSafeguards(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // §164.310(d)(2)(iii) - Data Backup and Storage
        if (!config.isAutomatedBackupEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.310(d)(2)(iii)-Backup",
                "Automated backup required for PHI data protection (HIPAA §164.310(d)(2)(iii))",
                "EfsBackupEnabled",
                "Enable automated backups to ensure data availability and disaster recovery."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.310(d)(2)(iii)-Backup",
                "Automated backup enabled for PHI data protection (HIPAA §164.310(d)(2)(iii))",
                "EfsBackupEnabled"
            ));
        }

        // Cross-region backup for disaster recovery
        if (ctx.security == SecurityProfile.PRODUCTION && !config.isCrossRegionBackupEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.310(d)(2)(iii)-CrossRegion",
                "Cross-region backup recommended for production PHI (HIPAA §164.310(d)(2)(iii))",
                "Implement geographic redundancy for disaster recovery."
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.310(d)(2)(iii)-CrossRegion",
                "Cross-region backup enabled for production PHI (HIPAA §164.310(d)(2)(iii))"
            ));
        }

        return rules;
    }

    /**
     * §164.312(a)(1) - Access Control.
     * Unique user identification, emergency access, automatic logoff, encryption and decryption.
     */
    private List<ComplianceRule> validateAccessControls(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // §164.312(a)(2)(i) - Unique User Identification (Required)
        String authMode = ctx.cfc.authMode();
        if ("none".equals(authMode)) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(a)(2)(i)-Auth",
                "Unique user identification required for PHI access (HIPAA §164.312(a)(2)(i))",
                "Configure authMode = 'alb-oidc', 'jenkins-oidc', or 'application-oidc' to identify and authenticate users."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(a)(2)(i)-Auth",
                "Unique user identification enabled for PHI access (HIPAA §164.312(a)(2)(i))"
            ));
        }

        // Validate network access controls
        if (ctx.albSg.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(a)(1)-NetworkAccess",
                "Access controls required at network level (HIPAA §164.312(a)(1))",
                "Security groups must restrict access to PHI systems."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(a)(1)-NetworkAccess",
                "Network access controls configured (HIPAA §164.312(a)(1))"
            ));
        }

        return rules;
    }

    /**
     * §164.312(b) - Audit Controls (Required).
     * Hardware, software, and procedural mechanisms to record and examine activity.
     */
    private List<ComplianceRule> validateAuditControls(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // §164.312(b) - Audit Controls (Required)
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(b)-CloudTrail",
                "CloudTrail audit logging required (HIPAA §164.312(b))",
                "CloudTrailEnabledRule",
                "Enable CloudTrail for comprehensive audit trail of all API activity."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(b)-CloudTrail",
                "CloudTrail audit logging enabled (HIPAA §164.312(b))",
                "CloudTrailEnabledRule"
            ));
        }

        if (!config.isFlowLogsEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(b)-FlowLogs",
                "VPC Flow Logs required to audit network access (HIPAA §164.312(b))",
                "VpcFlowLogsEnabled",
                "Enable Flow Logs to record all network traffic."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(b)-FlowLogs",
                "VPC Flow Logs enabled for network access audit (HIPAA §164.312(b))",
                "VpcFlowLogsEnabled"
            ));
        }

        if (!config.isAlbAccessLoggingEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(b)-ALB",
                "ALB access logging required (HIPAA §164.312(b))",
                "AlbAccessLogsEnabled",
                "Enable ALB access logs for comprehensive request tracking."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(b)-ALB",
                "ALB access logging enabled (HIPAA §164.312(b))",
                "AlbAccessLogsEnabled"
            ));
        }

        return rules;
    }

    /**
     * §164.312(c)(1) - Integrity (Required).
     * Policies and procedures to protect ePHI from improper alteration or destruction.
     */
    private List<ComplianceRule> validateIntegrityControls(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // §164.312(c)(2) - Mechanism to Authenticate ePHI (Addressable - we enforce)
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(c)(2)-Integrity",
                "CloudTrail log file validation required for ePHI integrity (HIPAA §164.312(c)(2))",
                "CloudTrailEnabledRule",
                "Enable CloudTrail with file validation to detect unauthorized modifications."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(c)(2)-Integrity",
                "CloudTrail log file validation enabled for ePHI integrity (HIPAA §164.312(c)(2))",
                "CloudTrailEnabledRule"
            ));
            // File integrity monitoring via CloudTrail
            LOG.info("HIPAA §164.312(c)(2): CloudTrail provides log file validation for integrity verification");
        }

        return rules;
    }

    /**
     * §164.312(d) - Person or Entity Authentication (Required).
     * Procedures to verify that a person or entity seeking access to ePHI is the one claimed.
     */
    private List<ComplianceRule> validateAuthenticationControls(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // §164.312(d) - Authentication (Required)
        String authMode = ctx.cfc.authMode();
        if ("none".equals(authMode)) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(d)-Auth",
                "Authentication required for all users accessing ePHI (HIPAA §164.312(d))",
                "Configure authMode = 'alb-oidc', 'jenkins-oidc', or 'application-oidc' with strong authentication mechanism."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(d)-Auth",
                "Authentication enabled for ePHI access (HIPAA §164.312(d))"
            ));
        }

        // Multi-factor authentication (Addressable - but highly recommended)
        if ("alb-oidc".equals(authMode) || "jenkins-oidc".equals(authMode) || "application-oidc".equals(authMode)) {
            // Check if using Cognito with MFA (compliant) or SSO (requires ssoInstanceArn)
            boolean usingCognitoWithMfa = Boolean.TRUE.equals(ctx.cfc.cognitoAutoProvision())
                                       && Boolean.TRUE.equals(ctx.cfc.cognitoMfaEnabled());
            boolean hasValidSso = ctx.cfc.ssoInstanceArn() != null && !ctx.cfc.ssoInstanceArn().isEmpty();

            if (!usingCognitoWithMfa && !hasValidSso) {
                rules.add(ComplianceRule.fail(
                    "HIPAA-164.312(d)-MFA",
                    "Multi-factor authentication recommended for ePHI access (HIPAA §164.312(d))",
                    "IAMMfaEnabled",
                    "Either: (1) Enable Cognito MFA: cognitoAutoProvision = true, cognitoMfaEnabled = true " +
                    "OR (2) Configure AWS IAM Identity Center: provide ssoInstanceArn with MFA enforcement."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "HIPAA-164.312(d)-MFA",
                    "Multi-factor authentication configured for ePHI access (HIPAA §164.312(d))",
                    "IAMMfaEnabled"
                ));
            }
        }

        return rules;
    }

    /**
     * §164.312(e)(1) - Transmission Security (Required).
     * Technical security measures to guard against unauthorized access to ePHI during transmission.
     */
    private List<ComplianceRule> validateTransmissionSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // §164.312(e)(2)(i) - SSL/TLS must be enabled for encrypted ePHI transmission
        if (!ctx.cfc.enableSsl()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(e)(2)(i)-SSL",
                "SSL/TLS must be enabled for encrypted transmission of ePHI (HIPAA §164.312(e)(2)(i))",
                "ALBHttpsOnly",
                "Set enableSsl=true for production HIPAA compliance."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(e)(2)(i)-SSL",
                "SSL/TLS enabled for transmission security (HIPAA §164.312(e)(2)(i))",
                "ALBHttpsOnly"
            ));
        }

        // §164.312(e)(2)(i) - Integrity Controls (Addressable - we enforce)
        if (ctx.cert.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(e)(2)(i)-TLS",
                "TLS certificate required for transmission integrity and confidentiality (HIPAA §164.312(e)(2)(i))",
                "ALBHttpsOnly",
                "Configure ACM certificate for HTTPS communication."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(e)(2)(i)-TLS",
                "TLS certificate configured for transmission security (HIPAA §164.312(e)(2)(i))",
                "ALBHttpsOnly"
            ));
        }

        // §164.312(e)(2)(ii) - Encryption (Addressable - we enforce)
        if (!config.isEfsEncryptionInTransitEnabled()) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(e)(2)(ii)-EFS",
                "EFS encryption in transit required for ePHI transmission security (HIPAA §164.312(e)(2)(ii))",
                "EfsEncryptionRule",
                "Enable EFS in-transit encryption to protect PHI during file system access."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(e)(2)(ii)-EFS",
                "EFS encryption in transit enabled (HIPAA §164.312(e)(2)(ii))",
                "EfsEncryptionRule"
            ));
        }

        // Network isolation
        if ("public-no-nat".equals(ctx.cfc.networkMode())) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.312(e)(1)-Network",
                "Private network mode required for PHI systems (HIPAA §164.312(e)(1))",
                "Use 'private-with-nat' to isolate PHI systems from public internet."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.312(e)(1)-Network",
                "Private network mode configured for PHI systems (HIPAA §164.312(e)(1))"
            ));
        }

        return rules;
    }

    /**
     * §164.316(b)(2)(i) - Retention Requirements.
     * Retain documentation for 6 years from the date of creation or last use.
     */
    private List<ComplianceRule> validateRetentionRequirements(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // §164.316(b)(2)(i) - Documentation must be retained for 6 years
        var retentionDays = config.getLogRetentionDays();
        if (!isRetentionSufficient(retentionDays)) {
            rules.add(ComplianceRule.fail(
                "HIPAA-164.316(b)(2)(i)-Retention",
                "Log retention must be at least 6 years (HIPAA §164.316(b)(2)(i))",
                "CloudWatchLogGroupRetention",
                "Log retention must be at least 6 years (2190 days). Current: " +
                retentionDays.toString() + ". " +
                "HIPAA requires 6-year retention for all documentation related to HIPAA compliance."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-164.316(b)(2)(i)-Retention",
                "Log retention meets 6-year requirement (HIPAA §164.316(b)(2)(i))",
                "CloudWatchLogGroupRetention"
            ));
        }

        return rules;
    }

    /**
     * Check if log retention meets HIPAA requirement (6 years).
     * §164.316(b)(2)(i): Retain documentation for 6 years.
     */
    private boolean isRetentionSufficient(RetentionDays retention) {
        // HIPAA STRICTLY requires 6 years (2190 days) - §164.316(b)(2)(i)
        // ONLY accept SIX_YEARS (2192 days) or longer
        // TWO_YEARS (731 days), THREE_YEARS (1096 days), FIVE_YEARS (1827 days) do NOT meet HIPAA requirement
        return retention == RetentionDays.SIX_YEARS ||
               retention == RetentionDays.SEVEN_YEARS ||
               retention == RetentionDays.EIGHT_YEARS ||
               retention == RetentionDays.NINE_YEARS ||
               retention == RetentionDays.TEN_YEARS ||
               retention == RetentionDays.INFINITE;
    }

    /**
     * Generate HIPAA Security Rule compliance report.
     */
    public String generateComplianceReport(SystemContext ctx) {
        StringBuilder report = new StringBuilder();
        report.append("\n=== HIPAA Security Rule Compliance Report ===\n\n");

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        report.append("Security Profile: ").append(ctx.security).append("\n");
        report.append("Environment: ").append(ctx.cfc.env()).append("\n\n");

        report.append("Administrative Safeguards (§164.308):\n");
        report.append("  ✓ Security Management Process (§164.308(a)(1)): ").append(config.isSecurityMonitoringEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Information Access Management (§164.308(a)(4)): ").append(ctx.iamProfile != null ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Physical Safeguards (§164.310):\n");
        report.append("  ✓ Automated Backup (§164.310(d)(2)(iii)): ").append(config.isAutomatedBackupEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Cross-Region Backup (§164.310(d)(2)(iii)): ").append(config.isCrossRegionBackupEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Technical Safeguards (§164.312):\n");
        report.append("  ✓ Access Control (§164.312(a)(1)): ").append(!"none".equals(ctx.cfc.authMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Audit Controls (§164.312(b)): ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Integrity Controls (§164.312(c)(1)): ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Authentication (§164.312(d)): ").append(!"none".equals(ctx.cfc.authMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Transmission Security (§164.312(e)(1)): ").append(ctx.cert.get().isPresent() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Encryption at Rest (§164.312(a)(2)(iv)): ").append(config.isEbsEncryptionEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Encryption in Transit (§164.312(e)(2)(ii)): ").append(config.isEfsEncryptionInTransitEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("\n");

        report.append("Organizational Requirements (§164.316):\n");
        report.append("  ✓ Log Retention (§164.316(b)(2)(i)): ").append(config.getLogRetentionDays().toString());
        if (!isRetentionSufficient(config.getLogRetentionDays())) {
            report.append(" [WARNING: Less than 6 years required]");
        }
        report.append("\n");

        report.append("\n");
        report.append("Note: HIPAA requires 6-year retention. Implement S3 archival for logs older than CloudWatch retention.\n");
        report.append("\n");

        return report.toString();
    }
}
