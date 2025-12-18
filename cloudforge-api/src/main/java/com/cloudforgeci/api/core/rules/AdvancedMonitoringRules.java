package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

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
 * // Automatically loaded via FrameworkLoader (v2.0 pattern)
 * // Or manually: new AdvancedMonitoringRules().install(ctx);
 * }</pre>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "AdvancedMonitoring",
    priority = -5,
    alwaysLoad = true,
    displayName = "Advanced Security Monitoring",
    description = "Cross-framework advanced monitoring and compliance dashboard validation"
)
public class AdvancedMonitoringRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(AdvancedMonitoringRules.class.getName());

    /**
     * Install advanced monitoring validation rules.
     * These rules apply primarily to PRODUCTION and STAGING environments.
     *
     * @param ctx System context
     */
    @Override
    public void install(SystemContext ctx) {
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
    private List<ComplianceRule> validateSecurityHub(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring which includes Security Hub
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();
        // Use DeploymentContext fields directly to avoid JSII callback loops during synthesis
        boolean securityHubEnabled = ctx.cfc.securityHubEnabled() != null ?
            ctx.cfc.securityHubEnabled() : securityMonitoringEnabled;

        // Check ComplianceMatrix to determine if Security Hub is REQUIRED or ADVISORY
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.SECURITY_HUB,
            complianceFrameworks,
            securityHubEnabled,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "SECURITYHUB-ENABLED",
                    "AWS Security Hub required for " + complianceFrameworks + " compliance dashboard",
                    "SecurityHubEnabled",
                    "Enable Security Hub for centralized compliance monitoring and reporting. " +
                    "Provides PCI-DSS, CIS AWS Foundations, and AWS Foundational Security Best Practices. " +
                    "Set securityHubEnabled = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Security Hub recommended but not required for " +
                    (complianceFrameworks != null ? complianceFrameworks : "this deployment"));
                rules.add(ComplianceRule.pass(
                    "SECURITYHUB-ENABLED",
                    "Security Hub is advisory for " + complianceFrameworks + " (recommended but not required)"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECURITYHUB-ENABLED",
                    "AWS Security Hub enabled for compliance dashboard" +
                    (securityMonitoringEnabled ? " (via security profile)" : ""),
                    "SecurityHubEnabled"
                ));
            }

            // Security Hub standards enabled (advisory - best practice recommendation)
            if (securityHubEnabled) {
            // Security Hub standards enabled (advisory - best practice recommendation)
            if (securityHubEnabled) {
                boolean pciDssStandardEnabled = getBooleanSetting(ctx, "securityHubPciDssEnabled", false);
                boolean cisStandardEnabled = getBooleanSetting(ctx, "securityHubCisEnabled", false);
                boolean awsFoundationalEnabled = getBooleanSetting(ctx, "securityHubAwsFoundationalEnabled", true);

                // Check if at least one standard is enabled (advisory recommendation)
                if (!pciDssStandardEnabled && !cisStandardEnabled && !awsFoundationalEnabled) {
                    LOG.warning("Security Hub best practice: At least one compliance standard should be enabled");
                    rules.add(ComplianceRule.pass(
                        "SECURITYHUB-STANDARDS",
                        "Security Hub standards recommended but not required (Enable PCI-DSS, CIS, or AWS Foundational for enhanced compliance tracking)"
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
                    LOG.warning("Security Hub best practice: Automated remediation recommended for production");
                    rules.add(ComplianceRule.pass(
                        "SECURITYHUB-AUTO-REMEDIATION",
                        "Security Hub auto-remediation recommended but not required"
                    ));
                }
            }            }
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
    private List<ComplianceRule> validateInspector(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring which includes Inspector
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();
        // Use DeploymentContext fields directly to avoid JSII callback loops during synthesis
        boolean inspectorEnabled = ctx.cfc.inspectorEnabled() != null ?
            ctx.cfc.inspectorEnabled() : securityMonitoringEnabled;

        // Check ComplianceMatrix to determine if Inspector is REQUIRED or ADVISORY
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.VULNERABILITY_SCANNING,
            complianceFrameworks,
            inspectorEnabled,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "INSPECTOR-ENABLED",
                    "Amazon Inspector required for " + complianceFrameworks + " vulnerability scanning",
                    "InspectorEnabled",
                    "Enable Inspector for automated vulnerability scanning of EC2 instances and containers. " +
                    "PCI-DSS Req 6.2, 11.2 requires vulnerability scanning. " +
                    "Set inspectorEnabled = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Inspector recommended but not required for " +
                    (complianceFrameworks != null ? complianceFrameworks : "this deployment"));
                rules.add(ComplianceRule.pass(
                    "INSPECTOR-ENABLED",
                    "Inspector is advisory for " + complianceFrameworks + " (recommended but not required)"
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

                // Continuous scanning (advisory - best practice recommendation)
                boolean inspectorContinuousScanning = getBooleanSetting(ctx, "inspectorContinuousScanning", securityMonitoringEnabled);

                if (inspectorContinuousScanning) {
                    rules.add(ComplianceRule.pass(
                        "INSPECTOR-CONTINUOUS",
                        "Inspector continuous scanning enabled" +
                        (securityMonitoringEnabled ? " (via security profile)" : "")
                    ));
                } else {
                    LOG.warning("Inspector best practice: Continuous scanning recommended for production");
                    rules.add(ComplianceRule.pass(
                        "INSPECTOR-CONTINUOUS",
                        "Inspector continuous scanning recommended but not required"
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
    private List<ComplianceRule> validateMacie(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Use DeploymentContext fields directly to avoid JSII callback loops during synthesis
        boolean macieEnabled = ctx.cfc.macieEnabled() != null ?
            ctx.cfc.macieEnabled() : false;

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
                    "Set macieEnabled = true in deployment context."
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
                // Use DeploymentContext fields directly to avoid JSII callback loops during synthesis
                boolean macieAutomatedDiscovery = ctx.cfc.macieAutomatedDiscoveryEnabled() != null ?
                    ctx.cfc.macieAutomatedDiscoveryEnabled() : false;

                if (!macieAutomatedDiscovery) {
                    rules.add(ComplianceRule.fail(
                        "MACIE-AUTOMATED-DISCOVERY",
                        "Automated sensitive data discovery jobs required",
                        "Configure automated Macie discovery jobs for all S3 buckets. " +
                        "Set macieAutomatedDiscovery = true when jobs are scheduled."
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
    private List<ComplianceRule> validateCentralizedMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();

        // CloudWatch dashboard for compliance
        boolean complianceDashboardEnabled = getBooleanSetting(ctx, "complianceDashboardEnabled", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (!complianceDashboardEnabled) {
                LOG.warning("Best practice: CloudWatch compliance dashboard recommended for production monitoring");
                rules.add(ComplianceRule.pass(
                    "COMPLIANCE-DASHBOARD",
                    "Compliance dashboard recommended but not required"
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
                LOG.warning("Best practice: Security alerting (SNS) recommended for production incidents");
                rules.add(ComplianceRule.pass(
                    "SECURITY-ALERTING",
                    "Security alerting recommended but not required"
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
    private boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
