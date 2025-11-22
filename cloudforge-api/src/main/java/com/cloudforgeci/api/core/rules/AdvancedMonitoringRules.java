package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Advanced security monitoring and compliance dashboard validation rules.
 *
 * <p>These rules enforce advanced monitoring capabilities across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 10, 11: Monitoring and testing security systems</li>
 *   <li><b>HIPAA</b> - §164.308(a)(1)(ii)(D): Information system activity review</li>
 *   <li><b>SOC 2</b> - CC7.2, CC7.3: System monitoring and threat detection</li>
 *   <li><b>GDPR</b> - Art.32(1)(d), Art.33: Regular testing and breach detection</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>AWS Security Hub compliance dashboard</li>
 *   <li>Amazon Inspector vulnerability scanning</li>
 *   <li>Amazon Macie data discovery (GDPR, HIPAA)</li>
 *   <li>Centralized findings aggregation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Install advanced monitoring validation
 * AdvancedMonitoringRules.install(ctx);
 * }</pre>
 */
public final class AdvancedMonitoringRules {

    private static final Logger LOG = Logger.getLogger(AdvancedMonitoringRules.class.getName());

    private AdvancedMonitoringRules() {}

    /**
     * Install advanced monitoring validation rules.
     * These rules apply primarily to PRODUCTION and STAGING environments.
     *
     * @param ctx System context
     */
    public static void install(SystemContext ctx) {
        LOG.info("Installing advanced monitoring compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Security Hub integration
            rules.addAll(validateSecurityHub(ctx));

            // Amazon Inspector vulnerability scanning
            rules.addAll(validateInspector(ctx));

            // Amazon Macie data discovery (GDPR, HIPAA)
            rules.addAll(validateMacie(ctx));

            // Centralized monitoring
            rules.addAll(validateCentralizedMonitoring(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Advanced Monitoring validation found " + failedRules.size() + " issues");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // Return blocking errors for missing advanced monitoring features
                return failedRules.stream()
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
            } else {
                LOG.info("Advanced Monitoring validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate AWS Security Hub integration.
     *
     * <p>Security Hub provides:</p>
     * <ul>
     *   <li>Centralized compliance dashboard</li>
     *   <li>Multi-framework compliance reporting (PCI-DSS, CIS, AWS Foundational)</li>
     *   <li>Automated security findings aggregation</li>
     *   <li>Compliance status tracking over time</li>
     * </ul>
     */
    private static List<ComplianceRule> validateSecurityHub(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring which includes Security Hub
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();
        boolean securityHubEnabled = getBooleanSetting(ctx, "securityHubEnabled", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (!securityHubEnabled) {
                rules.add(ComplianceRule.fail(
                    "SECURITYHUB-ENABLED",
                    "AWS Security Hub required for production compliance dashboard",
                    "SecurityHubEnabled",
                    "Enable Security Hub for centralized compliance monitoring and reporting. " +
                    "Provides PCI-DSS, CIS AWS Foundations, and AWS Foundational Security Best Practices. " +
                    "PCI-DSS Req 10, 11; HIPAA §164.308(a)(1)(ii)(D); SOC2 CC7.2; GDPR Art.32(1)(d). " +
                    "Note: PRODUCTION security profile enables security monitoring by default. " +
                    "Set securityHubEnabled=true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECURITYHUB-ENABLED",
                    "AWS Security Hub enabled for compliance dashboard" +
                    (securityMonitoringEnabled ? " (via security profile)" : ""),
                    "SecurityHubEnabled"
                ));
            }

            // Security Hub standards enabled
            if (securityHubEnabled) {
                boolean pciDssStandardEnabled = getBooleanSetting(ctx, "securityHubPciDssEnabled", false);
                boolean cisStandardEnabled = getBooleanSetting(ctx, "securityHubCisEnabled", false);
                boolean awsFoundationalEnabled = getBooleanSetting(ctx, "securityHubAwsFoundationalEnabled", true);

                // Check if at least one standard is enabled
                if (!pciDssStandardEnabled && !cisStandardEnabled && !awsFoundationalEnabled) {
                    rules.add(ComplianceRule.fail(
                        "SECURITYHUB-STANDARDS",
                        "At least one Security Hub standard must be enabled",
                        "Enable PCI-DSS, CIS, or AWS Foundational Security Best Practices standard. " +
                        "Set securityHubPciDssEnabled=true or securityHubCisEnabled=true."
                    ));
                } else {
                    rules.add(ComplianceRule.pass(
                        "SECURITYHUB-STANDARDS",
                        "Security Hub compliance standards enabled"
                    ));
                }

                // Automated response actions (advisory only)
                boolean securityHubAutoRemediation = getBooleanSetting(ctx, "securityHubAutoRemediation", securityMonitoringEnabled);

                if (securityHubAutoRemediation) {
                    rules.add(ComplianceRule.pass(
                        "SECURITYHUB-AUTO-REMEDIATION",
                        "Security Hub automated remediation enabled" +
                        (securityMonitoringEnabled ? " (via security profile)" : "")
                    ));
                } else {
                    rules.add(ComplianceRule.fail(
                        "SECURITYHUB-AUTO-REMEDIATION",
                        "Automated remediation recommended for Security Hub findings",
                        "Configure EventBridge rules for automatic remediation of common findings. " +
                        "Set securityHubAutoRemediation=true when implemented."
                    ));
                }
            }
        } else {
            // For non-production, advisory
            if (securityHubEnabled) {
                rules.add(ComplianceRule.pass(
                    "SECURITYHUB-ENABLED",
                    "AWS Security Hub enabled",
                    "SecurityHubEnabled"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECURITYHUB-ENABLED",
                    "Security Hub not required for " + ctx.security + " environment"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate Amazon Inspector integration.
     *
     * <p>Inspector provides:</p>
     * <ul>
     *   <li>Automated vulnerability scanning for EC2 and containers</li>
     *   <li>Software composition analysis (SCA)</li>
     *   <li>Network reachability analysis</li>
     *   <li>CVE detection and remediation guidance</li>
     * </ul>
     */
    private static List<ComplianceRule> validateInspector(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring which includes Inspector
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();
        boolean inspectorEnabled = getBooleanSetting(ctx, "inspectorEnabled", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (!inspectorEnabled) {
                rules.add(ComplianceRule.fail(
                    "INSPECTOR-ENABLED",
                    "Amazon Inspector required for production vulnerability scanning",
                    "InspectorEnabled",
                    "Enable Inspector for automated vulnerability scanning of EC2 instances and containers. " +
                    "PCI-DSS Req 6.2, 11.2; HIPAA §164.308(a)(8); SOC2 CC7.1; GDPR Art.32(1)(d). " +
                    "Note: PRODUCTION security profile enables security monitoring by default. " +
                    "Set inspectorEnabled=true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "INSPECTOR-ENABLED",
                    "Amazon Inspector enabled for vulnerability scanning" +
                    (securityMonitoringEnabled ? " (via security profile)" : ""),
                    "InspectorEnabled"
                ));
            }

            // Inspector scan types
            if (inspectorEnabled) {
                boolean inspectorEc2Scanning = getBooleanSetting(ctx, "inspectorEc2Scanning", true);
                boolean inspectorEcrScanning = getBooleanSetting(ctx, "inspectorEcrScanning", true);

                if (inspectorEc2Scanning || inspectorEcrScanning) {
                    rules.add(ComplianceRule.pass(
                        "INSPECTOR-SCAN-TYPES",
                        "Inspector scanning enabled for EC2/ECR"
                    ));
                }

                // Continuous scanning (default to true for PRODUCTION)
                boolean inspectorContinuousScanning = getBooleanSetting(ctx, "inspectorContinuousScanning", securityMonitoringEnabled);

                if (inspectorContinuousScanning) {
                    rules.add(ComplianceRule.pass(
                        "INSPECTOR-CONTINUOUS",
                        "Inspector continuous scanning enabled" +
                        (securityMonitoringEnabled ? " (via security profile)" : "")
                    ));
                } else {
                    rules.add(ComplianceRule.fail(
                        "INSPECTOR-CONTINUOUS",
                        "Continuous scanning recommended for production",
                        "Enable continuous vulnerability scanning in Inspector. " +
                        "Set inspectorContinuousScanning=true."
                    ));
                }
            }
        } else {
            // Advisory for non-production
            if (inspectorEnabled) {
                rules.add(ComplianceRule.pass(
                    "INSPECTOR-ENABLED",
                    "Amazon Inspector enabled",
                    "InspectorEnabled"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "INSPECTOR-ENABLED",
                    "Inspector not required for " + ctx.security + " environment"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate Amazon Macie integration.
     *
     * <p>Macie provides:</p>
     * <ul>
     *   <li>Sensitive data discovery (PII, PHI, payment card data)</li>
     *   <li>S3 bucket security analysis</li>
     *   <li>Data classification and inventory</li>
     *   <li>GDPR and HIPAA compliance support</li>
     * </ul>
     */
    private static List<ComplianceRule> validateMacie(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        boolean macieEnabled = getBooleanSetting(ctx, "macieEnabled", false);

        // Macie is critical for GDPR and HIPAA compliance
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresMacie = complianceFrameworks != null &&
            (complianceFrameworks.toUpperCase().contains("GDPR") ||
             complianceFrameworks.toUpperCase().contains("HIPAA"));

        if (ctx.security == SecurityProfile.PRODUCTION && requiresMacie) {
            if (!macieEnabled) {
                rules.add(ComplianceRule.fail(
                    "MACIE-ENABLED",
                    "Amazon Macie required for GDPR/HIPAA data discovery",
                    "MacieEnabled",
                    "Enable Macie to discover and protect sensitive data (PII, PHI) in S3. " +
                    "GDPR Art.25 (data protection by design), Art.30 (records of processing). " +
                    "HIPAA §164.308(a)(1)(ii)(A) (risk analysis). " +
                    "Set macieEnabled=true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "MACIE-ENABLED",
                    "Amazon Macie enabled for sensitive data discovery",
                    "MacieEnabled"
                ));
            }

            // Automated discovery jobs
            if (macieEnabled) {
                boolean macieAutomatedDiscovery = getBooleanSetting(ctx, "macieAutomatedDiscovery", false);

                if (!macieAutomatedDiscovery) {
                    rules.add(ComplianceRule.fail(
                        "MACIE-AUTOMATED-DISCOVERY",
                        "Automated sensitive data discovery jobs required",
                        "Configure automated Macie discovery jobs for all S3 buckets. " +
                        "Set macieAutomatedDiscovery=true when jobs are scheduled."
                    ));
                } else {
                    rules.add(ComplianceRule.pass(
                        "MACIE-AUTOMATED-DISCOVERY",
                        "Macie automated discovery jobs enabled"
                    ));
                }
            }
        } else if (macieEnabled) {
            rules.add(ComplianceRule.pass(
                "MACIE-ENABLED",
                "Amazon Macie enabled for sensitive data discovery",
                "MacieEnabled"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "MACIE-ENABLED",
                "Macie not required (no GDPR/HIPAA or non-production)"
            ));
        }

        return rules;
    }

    /**
     * Validate centralized monitoring and alerting.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>CloudWatch dashboards for compliance metrics</li>
     *   <li>SNS topics for security alerts</li>
     *   <li>Event-driven security response</li>
     * </ul>
     */
    private static List<ComplianceRule> validateCentralizedMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();

        // CloudWatch dashboard for compliance
        boolean complianceDashboardEnabled = getBooleanSetting(ctx, "complianceDashboardEnabled", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (!complianceDashboardEnabled) {
                rules.add(ComplianceRule.fail(
                    "COMPLIANCE-DASHBOARD",
                    "CloudWatch compliance dashboard recommended for production",
                    "Create CloudWatch dashboard to visualize compliance metrics. " +
                    "Note: PRODUCTION security profile enables security monitoring by default. " +
                    "Set complianceDashboardEnabled=true when dashboard is created."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "COMPLIANCE-DASHBOARD",
                    "Compliance monitoring dashboard enabled" +
                    (securityMonitoringEnabled ? " (via security profile)" : "")
                ));
            }
        }

        // Security alerting (SNS topics)
        boolean securityAlertingEnabled = getBooleanSetting(ctx, "securityAlertingEnabled", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (!securityAlertingEnabled) {
                rules.add(ComplianceRule.fail(
                    "SECURITY-ALERTING",
                    "Security alerting (SNS) required for production incidents",
                    "Configure SNS topics for security alerts from GuardDuty, Security Hub, Config. " +
                    "PCI-DSS Req 10, 12.10; HIPAA §164.308(a)(6); SOC2 CC7.3. " +
                    "Note: PRODUCTION security profile enables security monitoring by default. " +
                    "Set securityAlertingEnabled=true when SNS topics are configured."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECURITY-ALERTING",
                    "Security alerting (SNS) enabled" +
                    (securityMonitoringEnabled ? " (via security profile)" : "")
                ));
            }
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
}
