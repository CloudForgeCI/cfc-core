package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Threat protection compliance validation rules.
 *
 * <p>These rules enforce threat protection requirements across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 5: Malware protection; Req 11.4: Intrusion detection</li>
 *   <li><b>HIPAA</b> - §164.308(a)(5)(ii)(B): Malicious software protection</li>
 *   <li><b>SOC 2</b> - CC7.2, CC7.3: Threat detection and response</li>
 *   <li><b>GDPR</b> - Art.32(2): Regular security testing</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>Anti-malware protection</li>
 *   <li>Network intrusion detection</li>
 *   <li>File integrity monitoring</li>
 *   <li>Container security scanning</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Install threat protection validation
 * ThreatProtectionRules.install(ctx);
 * }</pre>
 */
public final class ThreatProtectionRules {

    private static final Logger LOG = Logger.getLogger(ThreatProtectionRules.class.getName());

    private ThreatProtectionRules() {}

    /**
     * Install threat protection validation rules.
     * These rules apply primarily to PRODUCTION environments.
     *
     * @param ctx System context
     */
    public static void install(SystemContext ctx) {
        LOG.info("Installing threat protection compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Malware protection
            rules.addAll(validateMalwareProtection(ctx));

            // Intrusion detection
            rules.addAll(validateIntrusionDetection(ctx));

            // File integrity monitoring
            rules.addAll(validateFileIntegrityMonitoring(ctx));

            // Container security
            rules.addAll(validateContainerSecurity(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Threat Protection validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only (warnings but not blocking)
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION, only block on actual infrastructure requirements
                // Advisory recommendations (container scanning, alert config) are non-blocking
                List<String> blockingRules = failedRules.stream()
                    .filter(rule -> isInfrastructureRequirement(ctx, rule.description()))
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();

                return blockingRules;
            } else {
                LOG.info("Threat Protection validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate malware protection (PCI-DSS Req 5, HIPAA §164.308(a)(5)(ii)(B)).
     *
     * <p>PRODUCTION + FARGATE: GuardDuty runtime protection + immutable containers satisfy anti-malware requirements.
     * EC2: Traditional anti-malware software required unless using immutable AMI approach.</p>
     */
    private static List<ComplianceRule> validateMalwareProtection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");

        boolean antiMalwareEnabled = getBooleanSetting(ctx, "antiMalwareEnabled", false);
        boolean isFargate = ctx.runtime != null && ctx.runtime.toString().equals("FARGATE");
        var config = ctx.securityProfileConfig.get().orElse(null);
        boolean hasGuardDuty = config != null && config.isGuardDutyEnabled();

        // PRODUCTION + FARGATE + GuardDuty = immutable infrastructure approach (auto-pass)
        if (ctx.security == SecurityProfile.PRODUCTION && isFargate && hasGuardDuty) {
            rules.add(ComplianceRule.pass(
                "ANTI-MALWARE-PROTECTION",
                "Anti-malware via GuardDuty runtime protection + immutable containers"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION && requiresPciDss && !antiMalwareEnabled) {
            rules.add(ComplianceRule.fail(
                "ANTI-MALWARE-PROTECTION",
                "Anti-malware protection required for PCI-DSS Req 5.1",
                "PRODUCTION profile with FARGATE + GuardDuty satisfies this requirement. " +
                "For EC2: deploy anti-malware or set antiMalwareEnabled = true."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ANTI-MALWARE-PROTECTION",
                "Anti-malware protection configured or not required"
            ));
        }

        // Anti-malware updates
        if (antiMalwareEnabled) {
            boolean antiMalwareAutoUpdate = getBooleanSetting(ctx, "antiMalwareAutoUpdate", true);

            if (!antiMalwareAutoUpdate) {
                rules.add(ComplianceRule.fail(
                    "ANTI-MALWARE-AUTO-UPDATE",
                    "Anti-malware definitions must be kept current",
                    "Enable automatic anti-malware signature updates. " +
                    "PCI-DSS Req 5.2. Set antiMalwareAutoUpdate = true."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ANTI-MALWARE-AUTO-UPDATE",
                    "Anti-malware automatic updates enabled"
                ));
            }

            // Malware scan logging
            boolean malwareScanLogging = getBooleanSetting(ctx, "malwareScanLogging", false);

            if (!malwareScanLogging) {
                rules.add(ComplianceRule.fail(
                    "MALWARE-SCAN-LOGGING",
                    "Anti-malware scan results must be logged",
                    "Configure anti-malware to log scan results and alerts. " +
                    "PCI-DSS Req 5.2. Set malwareScanLogging = true when logging is configured."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "MALWARE-SCAN-LOGGING",
                    "Malware scan logging enabled"
                ));
            }
        }

        // Container image scanning (alternative for containerized workloads)
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean containerImageScanning = getBooleanSetting(ctx, "containerImageScanning", false);

            if (!containerImageScanning && !antiMalwareEnabled) {
                rules.add(ComplianceRule.fail(
                    "CONTAINER-IMAGE-SCANNING",
                    "Container image scanning recommended for malware detection",
                    "Enable ECR image scanning or use Inspector for container vulnerability detection. " +
                    "PCI-DSS Req 5.1 (alternative for containers). " +
                    "Set containerImageScanning = true when enabled."
                ));
            } else if (containerImageScanning || antiMalwareEnabled) {
                rules.add(ComplianceRule.pass(
                    "CONTAINER-IMAGE-SCANNING",
                    "Container image scanning or anti-malware enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate intrusion detection and prevention.
     *
     * <p>PCI-DSS Requirement 11.4:</p>
     * <ul>
     *   <li>11.4.1: Detect and alert on intrusions</li>
     *   <li>11.4.2: Keep intrusion detection current</li>
     *   <li>11.4.3: Generate alerts for detected intrusions</li>
     * </ul>
     */
    private static List<ComplianceRule> validateIntrusionDetection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // Check which compliance frameworks are enabled
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean requiresHipaa = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("HIPAA");

        // GuardDuty for network intrusion detection (required for PCI-DSS and HIPAA only)
        if (ctx.security == SecurityProfile.PRODUCTION && (requiresPciDss || requiresHipaa)) {
            if (!config.isGuardDutyEnabled()) {
                rules.add(ComplianceRule.fail(
                    "NETWORK-INTRUSION-DETECTION",
                    "Network intrusion detection required for " +
                        (requiresPciDss ? "PCI-DSS" : "") +
                        (requiresPciDss && requiresHipaa ? " and " : "") +
                        (requiresHipaa ? "HIPAA" : ""),
                    "GuardDutyEnabled",
                    "Enable GuardDuty for network intrusion detection and threat intelligence. " +
                    (requiresPciDss ? "PCI-DSS Req 11.4; " : "") +
                    (requiresHipaa ? "HIPAA §164.312(e)(1); " : "") +
                    "Monitors VPC Flow Logs, CloudTrail, DNS queries. " +
                    "Set guardDutyEnabled = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "NETWORK-INTRUSION-DETECTION",
                    "GuardDuty network intrusion detection enabled",
                    "GuardDutyEnabled"
                ));
            }
        }

        // GuardDuty alert configuration (PRODUCTION only)
        if (config.isGuardDutyEnabled()) {
            boolean guardDutyAlertsConfigured = getBooleanSetting(ctx, "guardDutyAlertsConfigured", false);

            if (ctx.security == SecurityProfile.PRODUCTION && !guardDutyAlertsConfigured) {
                rules.add(ComplianceRule.fail(
                    "GUARDDUTY-ALERTS",
                    "GuardDuty alerts required for PRODUCTION security incidents",
                    "Configure EventBridge rules to send GuardDuty findings to SNS/SIEM. " +
                    "PCI-DSS Req 11.4.3. Set guardDutyAlertsConfigured = true when configured."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "GUARDDUTY-ALERTS",
                    ctx.security == SecurityProfile.PRODUCTION ?
                        "GuardDuty alerts configured" :
                        "GuardDuty alerts not required for " + ctx.security
                ));
            }
        }

        // WAF for application-layer protection (required for PCI-DSS only)
        if (ctx.security == SecurityProfile.PRODUCTION && requiresPciDss) {
            if (!config.isWafEnabled()) {
                rules.add(ComplianceRule.fail(
                    "WAF-INTRUSION-PREVENTION",
                    "Web Application Firewall required for PCI-DSS",
                    "WafEnabled",
                    "Enable WAF for protection against OWASP Top 10, SQL injection, XSS attacks. " +
                    "PCI-DSS Req 6.6, 11.4. " +
                    "Set wafEnabled = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "WAF-INTRUSION-PREVENTION",
                    "WAF application-layer protection enabled",
                    "WafEnabled"
                ));
            }
        }

        // Network traffic monitoring (required for PCI-DSS only)
        if (ctx.security == SecurityProfile.PRODUCTION && requiresPciDss) {
            if (!config.isFlowLogsEnabled()) {
                rules.add(ComplianceRule.fail(
                    "NETWORK-TRAFFIC-MONITORING",
                    "VPC Flow Logs required for PCI-DSS",
                    "VpcFlowLogsEnabled",
                    "Enable VPC Flow Logs for network traffic baseline and anomaly detection. " +
                    "PCI-DSS Req 11.4. " +
                    "Set enableFlowlogs = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "NETWORK-TRAFFIC-MONITORING",
                    "VPC Flow Logs enabled for traffic monitoring",
                    "VpcFlowLogsEnabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate file integrity monitoring.
     *
     * <p>PCI-DSS Requirement 11.5:</p>
     * <ul>
     *   <li>11.5.1: Implement file integrity monitoring on critical files</li>
     *   <li>11.5.2: Compare files at least weekly</li>
     *   <li>11.5.3: Alert on unauthorized modifications</li>
     * </ul>
     */
    private static List<ComplianceRule> validateFileIntegrityMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Check which compliance frameworks are enabled
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");

        // File integrity monitoring (required for PCI-DSS only)
        // For PRODUCTION with containers (FARGATE), immutable infrastructure satisfies this
        boolean fileIntegrityMonitoring = getBooleanSetting(ctx, "fileIntegrityMonitoring", false);
        boolean isFargate = ctx.runtime != null && ctx.runtime.toString().equals("FARGATE");

        // Auto-pass for PRODUCTION profile with FARGATE (immutable infrastructure = file integrity by design)
        if (ctx.security == SecurityProfile.PRODUCTION && isFargate) {
            rules.add(ComplianceRule.pass(
                "FILE-INTEGRITY-MONITORING",
                "File integrity via immutable containers (PRODUCTION profile with FARGATE)"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION && requiresPciDss && !fileIntegrityMonitoring) {
            rules.add(ComplianceRule.fail(
                "FILE-INTEGRITY-MONITORING",
                "File integrity monitoring required for PCI-DSS",
                "Implement file integrity monitoring (FIM) on system files and critical applications. " +
                "For containers: use immutable infrastructure (new deployment = new container). " +
                "PCI-DSS Req 11.5. " +
                "Set fileIntegrityMonitoring = true when FIM is configured or use PRODUCTION security profile with FARGATE."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "FILE-INTEGRITY-MONITORING",
                "File integrity monitoring configured or not required"
            ));
        }

        // Change detection with AWS Config (required for PCI-DSS only)
        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config != null && ctx.security == SecurityProfile.PRODUCTION && requiresPciDss) {
            if (!config.isAwsConfigEnabled()) {
                rules.add(ComplianceRule.fail(
                    "CONFIG-CHANGE-DETECTION",
                    "AWS Config required for PCI-DSS infrastructure change detection",
                    "ConfigEnabled",
                    "Enable AWS Config to detect unauthorized infrastructure changes. " +
                    "PCI-DSS Req 11.5 (infrastructure-level FIM). " +
                    "Set awsConfigEnabled = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "CONFIG-CHANGE-DETECTION",
                    "AWS Config change detection enabled",
                    "ConfigEnabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate container security.
     *
     * <p>Container-specific security controls:</p>
     * <ul>
     *   <li>Base image security and minimal attack surface</li>
     *   <li>Container vulnerability scanning</li>
     *   <li>Runtime security monitoring</li>
     * </ul>
     */
    private static List<ComplianceRule> validateContainerSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Check which compliance frameworks are enabled
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresGdpr = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("GDPR");

        // Container runtime security (recommended but not required - only enforce for GDPR)
        boolean containerRuntimeSecurity = getBooleanSetting(ctx, "containerRuntimeSecurity", false);

        if (ctx.security == SecurityProfile.PRODUCTION && requiresGdpr && !containerRuntimeSecurity) {
            rules.add(ComplianceRule.fail(
                "CONTAINER-RUNTIME-SECURITY",
                "Container runtime security monitoring required for GDPR",
                "Enable GuardDuty for ECS/EKS runtime threat detection or use third-party tools. " +
                "Monitors suspicious process activity, network connections, privilege escalation. " +
                "GDPR Art.32(2) requires security of processing. " +
                "Set containerRuntimeSecurity = true when monitoring is configured."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "CONTAINER-RUNTIME-SECURITY",
                "Container runtime security configured or not required"
            ));
        }

        // Immutable infrastructure
        boolean immutableInfrastructure = getBooleanSetting(ctx, "immutableInfrastructure", true);

        if (immutableInfrastructure) {
            rules.add(ComplianceRule.pass(
                "IMMUTABLE-INFRASTRUCTURE",
                "Immutable infrastructure pattern reduces tampering risk"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "IMMUTABLE-INFRASTRUCTURE",
                "Immutable infrastructure recommended for containers",
                "Deploy new containers instead of modifying running containers. " +
                "Prevents unauthorized file modifications. PCI-DSS Req 11.5 (best practice). " +
                "Set immutableInfrastructure = true."
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
     * Determine if a validation rule is an infrastructure requirement (blocking)
     * versus an advisory recommendation (non-blocking).
     *
     * Infrastructure requirements are technical services that must be deployed (GuardDuty, WAF, etc.)
     * Advisory recommendations are configuration/operational items (alert setup, scanning config, etc.)
     */
    private static boolean isInfrastructureRequirement(SystemContext ctx, String ruleDescription) {
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean requiresHipaa = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("HIPAA");

        // Advisory recommendations (non-blocking even for PRODUCTION)
        // These are configuration and operational recommendations, not infrastructure
        if (ruleDescription.contains("recommended") ||
            ruleDescription.contains("alerts") ||
            ruleDescription.contains("Container image scanning") ||
            ruleDescription.contains("GuardDuty alerts") ||
            ruleDescription.contains("runtime security monitoring")) {
            return false; // Advisory only
        }

        // Infrastructure requirements (blocking for PRODUCTION with specific frameworks)
        // These are actual AWS services that must be deployed

        // GuardDuty service (required for PCI-DSS and HIPAA only)
        if (ruleDescription.contains("Network intrusion detection") &&
            (requiresPciDss || requiresHipaa)) {
            return true; // Blocking for PCI-DSS/HIPAA
        }

        // WAF service (required for PCI-DSS only)
        if (ruleDescription.contains("Web Application Firewall") && requiresPciDss) {
            return true; // Blocking for PCI-DSS
        }

        // VPC Flow Logs (required for PCI-DSS only)
        if (ruleDescription.contains("VPC Flow Logs") && requiresPciDss) {
            return true; // Blocking for PCI-DSS
        }

        // AWS Config (required for PCI-DSS only for FIM)
        if (ruleDescription.contains("AWS Config") && requiresPciDss) {
            return true; // Blocking for PCI-DSS
        }

        // File integrity monitoring (required for PCI-DSS only)
        if (ruleDescription.contains("File integrity monitoring") && requiresPciDss) {
            return true; // Blocking for PCI-DSS
        }

        // Anti-malware (required for PCI-DSS only)
        if (ruleDescription.contains("Anti-malware protection") && requiresPciDss) {
            return true; // Blocking for PCI-DSS
        }

        // Default: advisory (non-blocking)
        return false;
    }
}
