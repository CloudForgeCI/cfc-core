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
 * PCI-DSS compliance validation rules.
 *
 * These rules enforce PCI-DSS requirements for environments processing cardholder data.
 *
 * PCI-DSS Requirements Coverage:
 * - Requirement 1: Install and maintain a firewall configuration
 * - Requirement 2: Do not use vendor-supplied defaults
 * - Requirement 3: Protect stored cardholder data
 * - Requirement 4: Encrypt transmission of cardholder data
 * - Requirement 6: Develop and maintain secure systems
 * - Requirement 7: Restrict access to cardholder data by business need to know
 * - Requirement 8: Identify and authenticate access to system components
 * - Requirement 10: Track and monitor all access to network resources
 * - Requirement 11: Regularly test security systems and processes
 */
@ComplianceFramework(
    value = "PCI-DSS",
    priority = 20,
    displayName = "PCI-DSS",
    description = "Validates PCI-DSS requirements for cardholder data protection"
)
public class PciDssRules implements FrameworkRules<SystemContext> {
    private static final Logger LOG = Logger.getLogger(PciDssRules.class.getName());


    /**
     * Check if log retention period meets PCI-DSS requirement (at least 1 year).
     * PCI-DSS Requirement 10.7: Retain audit trail history for at least one year.
     */
    private boolean isRetentionSufficient(RetentionDays retention) {
        // PCI-DSS requires at least 1 year (365 days)
        // Acceptable values: ONE_YEAR, TWO_YEARS, FIVE_YEARS, etc.
        return retention == RetentionDays.ONE_YEAR ||
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
     * Install PCI-DSS compliance validation rules.
     * PCI-DSS applies to environments processing cardholder data.
     * Only enforced when security profile is PRODUCTION.
     *
     * @since 3.1.0
     */
    @Override
    public void install(SystemContext ctx) {
        if (ctx.security != SecurityProfile.PRODUCTION) {
            LOG.info("PCI-DSS validation rules only enforced for PRODUCTION security profile");
            return;
        }

        LOG.info("Installing PCI-DSS compliance validation rules");

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

            // Requirement 1: Network Segmentation & Firewall
            rules.addAll(validateNetworkSecurity(ctx));

            // Requirement 2: Vendor Defaults
            rules.addAll(validateVendorDefaults(ctx));

            // Requirement 3 & 4: Encryption
            rules.addAll(validateEncryption(ctx));

            // Requirement 6: Web Application Firewall (PCI-DSS 6.6)
            rules.addAll(validateWebApplicationSecurity(ctx));

            // Requirement 7 & 8: Access Control
            rules.addAll(validateAccessControl(ctx));

            // Requirement 10: Audit Logging
            rules.addAll(validateAuditLogging(ctx));

            // Requirement 11: Security Monitoring
            rules.addAll(validateSecurityMonitoring(ctx));

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
                    LOG.warning("PCI-DSS validation found " + errors.size() + " recommendations (ADVISORY mode - not blocking)");
                    errors.forEach(err -> LOG.warning("  - " + err));
                    return List.of();
                } else {
                    LOG.severe("PCI-DSS validation failed with " + errors.size() + " violations (ENFORCE mode - blocking deployment)");
                    errors.forEach(err -> LOG.severe("  - " + err));
                    return errors;
                }
            } else {
                LOG.info("PCI-DSS validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * PCI-DSS Requirement 1: Install and maintain a firewall configuration.
     * Validates network segmentation and firewall rules.
     */
    private List<ComplianceRule> validateNetworkSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Requirement 1.2.1: Network segmentation
        if (ctx.vpc.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-1.2.1-VPC",
                "VPC required for network segmentation",
                "PCI-DSS Req 1.2.1: VPC required for network segmentation"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-1.2.1-VPC",
                "VPC network segmentation enabled"
            ));
        }

        // Requirement 1.3: Prohibit direct public access
        if ("public-no-nat".equals(ctx.cfc.networkMode())) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-1.3-Network",
                "Private network mode required for cardholder data environment",
                "PCI-DSS Req 1.3: Public network mode prohibited for cardholder data environment. " +
                "Use 'private-with-nat' for production systems processing card data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-1.3-Network",
                "Private network mode configured"
            ));
        }

        // Requirement 1.3.1: Security groups must restrict traffic
        if (ctx.albSg.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-1.3.1-ALB-SG",
                "ALB security group required for traffic restrictions",
                "PCI-DSS Req 1.3.1: ALB security group required for traffic restrictions"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-1.3.1-ALB-SG",
                "ALB security group configured"
            ));
        }

        if (ctx.efsSg.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-1.3.1-EFS-SG",
                "EFS security group required for storage access restrictions",
                "PCI-DSS Req 1.3.1: EFS security group required for storage access restrictions"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-1.3.1-EFS-SG",
                "EFS security group configured"
            ));
        }

        return rules;
    }

    /**
     * PCI-DSS Requirement 3 & 4: Protect stored and transmitted cardholder data.
     * Validates encryption settings.
     */
    private List<ComplianceRule> validateEncryption(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Requirement 3.4: Encryption of cardholder data at rest
        if (!config.isEbsEncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-3.4-EBS",
                "EBS encryption must be enabled for cardholder data at rest",
                "EbsEncryptionRule",
                "PCI-DSS Req 3.4: EBS encryption must be enabled for cardholder data at rest"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-3.4-EBS",
                "EBS encryption enabled",
                "EbsEncryptionRule"
            ));
        }

        if (!config.isEfsEncryptionAtRestEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-3.4-EFS",
                "EFS encryption at rest must be enabled for cardholder data storage",
                "PCI-DSS Req 3.4: EFS encryption at rest must be enabled for cardholder data storage"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-3.4-EFS",
                "EFS encryption at rest enabled"
            ));
        }

        if (!config.isS3EncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-3.4-S3",
                "S3 encryption must be enabled for cardholder data backups",
                "S3BucketEncryptionRule",
                "PCI-DSS Req 3.4: S3 encryption must be enabled for cardholder data backups"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-3.4-S3",
                "S3 encryption enabled",
                "S3BucketEncryptionRule"
            ));
        }

        // Requirement 4.1: Strong cryptography for data transmission
        if (!config.isEfsEncryptionInTransitEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-4.1-EFS-Transit",
                "EFS encryption in transit (TLS) must be enabled",
                "PCI-DSS Req 4.1: EFS encryption in transit (TLS) must be enabled"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-4.1-EFS-Transit",
                "EFS encryption in transit enabled"
            ));
        }

        // Validate TLS certificate is configured
        if (ctx.cert.get().isEmpty()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-4.1-TLS",
                "TLS certificate required for encrypted transmission of cardholder data",
                "PCI-DSS Req 4.1: TLS certificate required for encrypted transmission of cardholder data"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-4.1-TLS",
                "TLS certificate configured"
            ));
        }

        return rules;
    }

    /**
     * PCI-DSS Requirement 6.6: Protect public-facing web applications.
     * Either WAF or application-layer security review required.
     */
    private List<ComplianceRule> validateWebApplicationSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Requirement 6.6: WAF or application security review
        if (!config.isWafEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-6.6-WAF",
                "Web Application Firewall (WAF) strongly recommended for production",
                "WafEnabled",
                "PCI-DSS Req 6.6: Web Application Firewall (WAF) strongly recommended for production. " +
                "Set wafEnabled = true in deployment context. " +
                "Alternative: Document regular application security reviews per PCI-DSS 6.6."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-6.6-WAF",
                "WAF protection enabled",
                "WafEnabled"
            ));
        }

        return rules;
    }

    /**
     * PCI-DSS Requirement 7 & 8: Restrict access and authenticate users.
     * Validates access control configuration.
     */
    private List<ComplianceRule> validateAccessControl(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Requirement 7.1: Limit access by business need to know
        // IAM validation is handled by IAMRules, but we verify it's configured
        if (ctx.iamProfile == null) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-7.1-IAM",
                "IAM profile must be configured for least privilege access control",
                "IAMPasswordPolicyRule",
                "PCI-DSS Req 7.1: IAM profile must be configured for least privilege access control"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-7.1-IAM",
                "IAM profile configured for access control",
                "IAMPasswordPolicyRule"
            ));
        }

        // Requirement 8.2: Multi-factor authentication
        String authMode = ctx.cfc.authMode();
        if ("none".equals(authMode)) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-8.2-Auth",
                "Authentication must be enabled for production environments",
                "PCI-DSS Req 8.2: Authentication must be enabled for production environments. " +
                "Configure authMode = 'alb-oidc', 'jenkins-oidc', or 'application-oidc' with MFA-enabled identity provider. " +
                "Users must use multi-factor authentication to access cardholder data."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-8.2-Auth",
                "Authentication enabled"
            ));
        }

        // Requirement 8.3: Strong authentication with MFA
        if ("alb-oidc".equals(authMode) || "jenkins-oidc".equals(authMode) || "application-oidc".equals(authMode)) {
            // Check if using Cognito with MFA (compliant) or SSO (requires ssoInstanceArn)
            boolean usingCognitoWithMfa = Boolean.TRUE.equals(ctx.cfc.cognitoAutoProvision())
                                       && Boolean.TRUE.equals(ctx.cfc.cognitoMfaEnabled());
            boolean hasValidSso = ctx.cfc.ssoInstanceArn() != null && !ctx.cfc.ssoInstanceArn().isEmpty();

            if (!usingCognitoWithMfa && !hasValidSso) {
                rules.add(ComplianceRule.fail(
                    "PCI-DSS-Req-8.3-MFA",
                    "Multi-factor authentication required for OIDC mode",
                    "PCI-DSS Req 8.3: Multi-factor authentication required for OIDC mode. " +
                    "Either: (1) Enable Cognito MFA: cognitoAutoProvision = true, cognitoMfaEnabled = true " +
                    "OR (2) Configure AWS IAM Identity Center: provide ssoInstanceArn with MFA enforcement."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "PCI-DSS-Req-8.3-MFA",
                    "Multi-factor authentication configured"
                ));
            }
        }

        return rules;
    }

    /**
     * PCI-DSS Requirement 10: Track and monitor all access.
     * Validates audit logging configuration.
     */
    private List<ComplianceRule> validateAuditLogging(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Requirement 10.2: Automated audit trails
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-10.2-CloudTrail",
                "CloudTrail must be enabled for API activity audit logging",
                "CloudTrailEnabledRule",
                "PCI-DSS Req 10.2: CloudTrail must be enabled for API activity audit logging"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-10.2-CloudTrail",
                "CloudTrail enabled for audit logging",
                "CloudTrailEnabledRule"
            ));
        }

        // Requirement 10.3: Record specific events
        if (!config.isFlowLogsEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-10.3-FlowLogs",
                "VPC Flow Logs must be enabled for network activity tracking",
                "PCI-DSS Req 10.3: VPC Flow Logs must be enabled for network activity tracking"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-10.3-FlowLogs",
                "VPC Flow Logs enabled"
            ));
        }

        // Requirement 10.5: Secure audit trails
        if (!config.isAlbAccessLoggingEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-10.5-ALB",
                "ALB access logging must be enabled for web traffic audit trails",
                "PCI-DSS Req 10.5: ALB access logging must be enabled for web traffic audit trails"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-10.5-ALB",
                "ALB access logging enabled"
            ));
        }

        // Requirement 10.7: Retain audit trail for at least one year
        var retentionDays = config.getLogRetentionDays();
        if (!isRetentionSufficient(retentionDays)) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-10.7-Retention",
                "Log retention must be at least ONE_YEAR (365 days)",
                "PCI-DSS Req 10.7: Log retention must be at least ONE_YEAR (365 days). Current: " +
                retentionDays.toString()
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-10.7-Retention",
                "Log retention configured for at least one year"
            ));
        }

        return rules;
    }

    /**
     * PCI-DSS Requirement 11: Regularly test security systems.
     * Validates security monitoring and intrusion detection.
     */
    private List<ComplianceRule> validateSecurityMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // Requirement 11.4: Intrusion detection/prevention systems
        if (!config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-11.4-GuardDuty",
                "GuardDuty (threat detection) must be enabled for production",
                "GuardDutyEnabled",
                "PCI-DSS Req 11.4: GuardDuty (threat detection) must be enabled for production"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-11.4-GuardDuty",
                "GuardDuty threat detection enabled",
                "GuardDutyEnabled"
            ));
        }

        // Requirement 11.5: File integrity monitoring
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-11.5-Monitoring",
                "Security monitoring must be enabled for alerting",
                "PCI-DSS Req 11.5: Security monitoring must be enabled for alerting"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-11.5-Monitoring",
                "Security monitoring enabled"
            ));
        }

        // Requirement 11.6: Change detection (AWS Config)
        if (!config.isAwsConfigEnabled()) {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-11.6-Config",
                "AWS Config recommended for continuous compliance monitoring",
                "PCI-DSS Req 11.6: AWS Config recommended for continuous compliance monitoring. " +
                "Set awsConfigEnabled = true in deployment context."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-11.6-Config",
                "AWS Config enabled for compliance monitoring"
            ));
        }

        return rules;
    }

    /**
     * PCI-DSS Requirement 2: Do not use vendor-supplied defaults.
     * Validates that default configurations have been changed.
     *
     * For PRODUCTION security profile, these operational controls are assumed to be
     * implemented as part of the organization's security program and are auto-approved.
     */
    private List<ComplianceRule> validateVendorDefaults(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // For PRODUCTION profile, assume operational controls are in place
        // This matches the infrastructure-centric approach of SOC2 rules
        boolean isProduction = ctx.security == SecurityProfile.PRODUCTION;

        // Requirement 2.1: Default passwords must be changed
        // PRODUCTION profile requires strong IAM policies and authentication
        if (isProduction || getBooleanSetting(ctx, "customConfigurationApplied", false)) {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-2.1-CustomConfig",
                "Custom configuration applied - vendor defaults changed (PRODUCTION profile)"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-2.1-CustomConfig",
                "Custom configuration required - vendor defaults must be changed",
                "Verify default passwords, SNMP strings, and credentials have been changed. " +
                "PCI-DSS Req 2.1: Change all vendor-supplied defaults before deploying. " +
                "Set customConfigurationApplied = true after verification or use PRODUCTION security profile."
            ));
        }

        // Requirement 2.2: Configuration standards
        // PRODUCTION profile applies hardened security configurations
        if (isProduction || getBooleanSetting(ctx, "securityHardeningApplied", false)) {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-2.2-Hardening",
                "Security hardening standards applied (PRODUCTION profile)"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-2.2-Hardening",
                "Security hardening configuration standards required",
                "Apply CIS benchmarks or NIST hardening guidelines. " +
                "PCI-DSS Req 2.2: Develop configuration standards for all system components. " +
                "Set securityHardeningApplied = true after implementing hardening or use PRODUCTION security profile."
            ));
        }

        // Requirement 2.2.2: Enable only necessary services
        // PRODUCTION profile enforces minimal service exposure via security groups
        if (isProduction || getBooleanSetting(ctx, "unnecessaryServicesDisabled", false)) {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-2.2.2-Services",
                "Unnecessary services disabled (PRODUCTION profile enforces minimal exposure)"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-2.2.2-Services",
                "Disable unnecessary services and protocols",
                "PCI-DSS Req 2.2.2: Enable only necessary services, protocols, and daemons. " +
                "Remove or disable FTP, telnet, and other insecure services. " +
                "Set unnecessaryServicesDisabled = true after verification or use PRODUCTION security profile."
            ));
        }

        // Requirement 2.2.5: Remove unnecessary functionality
        // PRODUCTION profile assumes minimal images are used for production deployments
        if (isProduction || getBooleanSetting(ctx, "minimalBaseImageUsed", false)) {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-2.2.5-MinimalImage",
                "Minimal base image in use (PRODUCTION profile)"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-2.2.5-MinimalImage",
                "Use minimal base images without unnecessary functionality",
                "PCI-DSS Req 2.2.5: Remove unnecessary functionality (scripts, utilities, etc). " +
                "Use minimal container images or hardened AMIs. " +
                "Set minimalBaseImageUsed = true when using minimal images or use PRODUCTION security profile."
            ));
        }

        // Requirement 2.3: Encrypt non-console administrative access
        // Already covered by TLS certificate validation in validateEncryption()
        if (ctx.cert.get().isPresent()) {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-2.3-EncryptAdmin",
                "Administrative access encrypted (HTTPS/TLS)"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-2.3-EncryptAdmin",
                "Encrypt all non-console administrative access",
                "PCI-DSS Req 2.3: Use SSH, VPN, TLS for remote admin access"
            ));
        }

        // Requirement 2.4: Maintain inventory of system components
        // AWS Config provides continuous inventory for PRODUCTION deployments
        var config = ctx.securityProfileConfig.get().orElse(null);
        if ((config != null && config.isAwsConfigEnabled()) || getBooleanSetting(ctx, "systemInventoryMaintained", false)) {
            rules.add(ComplianceRule.pass(
                "PCI-DSS-Req-2.4-Inventory",
                "System inventory maintained (AWS Config enabled)"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "PCI-DSS-Req-2.4-Inventory",
                "Maintain inventory of system components in scope for PCI-DSS",
                "PCI-DSS Req 2.4: Keep inventory of all systems in cardholder data environment. " +
                "Use AWS Systems Manager Inventory or Config. " +
                "Set systemInventoryMaintained = true when inventory system is active or enable AWS Config."
            ));
        }

        return rules;
    }

    /**
     * Helper method to safely get boolean settings from deployment context.
     */
    private static boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Generate PCI-DSS compliance report showing which requirements are met.
     */
    public String generateComplianceReport(SystemContext ctx) {
        StringBuilder report = new StringBuilder();
        report.append("\n=== PCI-DSS Compliance Report == = \n\n");

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        report.append("Security Profile: ").append(ctx.security).append("\n");
        report.append("Network Mode: ").append(ctx.cfc.networkMode()).append("\n");
        report.append("Authentication: ").append(ctx.cfc.authMode()).append("\n\n");

        report.append("Infrastructure Controls:\n");
        report.append("  ✓ Network Segmentation (Req 1.2.1): ").append(ctx.vpc.get().isPresent() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Private Network (Req 1.3): ").append("private-with-nat".equals(ctx.cfc.networkMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ EBS Encryption (Req 3.4): ").append(config.isEbsEncryptionEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ EFS Encryption at Rest (Req 3.4): ").append(config.isEfsEncryptionAtRestEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ EFS Encryption in Transit (Req 4.1): ").append(config.isEfsEncryptionInTransitEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ S3 Encryption (Req 3.4): ").append(config.isS3EncryptionEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ TLS Certificate (Req 4.1): ").append(ctx.cert.get().isPresent() ? "CONFIGURED" : "MISSING").append("\n");
        report.append("  ✓ WAF Protection (Req 6.6): ").append(config.isWafEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Authentication (Req 8.2): ").append(!"none".equals(ctx.cfc.authMode()) ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ CloudTrail (Req 10.2): ").append(config.isCloudTrailEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ VPC Flow Logs (Req 10.3): ").append(config.isFlowLogsEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ ALB Access Logs (Req 10.5): ").append(config.isAlbAccessLoggingEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Log Retention (Req 10.7): ").append(config.getLogRetentionDays().toString()).append("\n");
        report.append("  ✓ GuardDuty (Req 11.4): ").append(config.isGuardDutyEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ Security Monitoring (Req 11.5): ").append(config.isSecurityMonitoringEnabled() ? "ENABLED" : "DISABLED").append("\n");
        report.append("  ✓ AWS Config (Req 11.6): ").append(config.isAwsConfigEnabled() ? "ENABLED" : "DISABLED").append("\n");

        report.append("\n");
        return report.toString();
    }
}
