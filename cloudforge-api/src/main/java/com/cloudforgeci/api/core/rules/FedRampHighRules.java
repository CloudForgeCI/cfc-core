package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * FedRAMP High Impact Level compliance validator.
 *
 * Implements NIST SP 800-53 Rev 5 controls for FedRAMP High baseline.
 * The High baseline includes all Moderate controls plus approximately 87 additional
 * controls for systems processing highly sensitive federal data.
 *
 * <h2>FedRAMP High vs Moderate</h2>
 * FedRAMP High is required for systems where:
 * <ul>
 *   <li>Confidentiality, integrity, or availability impact is HIGH</li>
 *   <li>Processing law enforcement or national security data</li>
 *   <li>Healthcare or financial systems with critical data</li>
 * </ul>
 *
 * <h2>Additional Control Families for High Baseline</h2>
 * Key additional controls beyond Moderate:
 * <ul>
 *   <li>AC-2(2): Automated temporary/emergency account removal</li>
 *   <li>AC-2(3): Disable inactive accounts (90 days vs 180 for Moderate)</li>
 *   <li>AC-2(13): Disable accounts for high-risk individuals</li>
 *   <li>AU-9(4): Access by subset of privileged users</li>
 *   <li>CP-6(1): Separation from primary site</li>
 *   <li>CP-7: Alternate processing site</li>
 *   <li>CP-7(1): Separation from primary site for processing</li>
 *   <li>IR-4(1): Automated incident handling</li>
 *   <li>SC-7(8): Route traffic to authenticated proxy</li>
 *   <li>SC-7(18): Fail secure</li>
 *   <li>SI-4(5): System-generated alerts</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * Enable via deployment context:
 * <pre>{@code
 * cfc.put("complianceFrameworks", "FEDRAMP-HIGH");
 * }</pre>
 *
 * @see FedRampRules for Moderate baseline controls
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "FEDRAMP-HIGH",
    priority = 26, // Load after FedRAMP Moderate (25)
    displayName = "FedRAMP High",
    description = "Federal Risk and Authorization Management Program - NIST 800-53 Rev 5 High Impact Baseline"
)
public final class FedRampHighRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(FedRampHighRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        // FedRAMP High only applies to PRODUCTION (High baseline is for critical systems)
        if (ctx.security != SecurityProfile.PRODUCTION) {
            LOG.info("FedRAMP High rules skipped for " + ctx.security + " environment - only PRODUCTION requires High controls");
            return;
        }

        LOG.info("Installing FedRAMP High Impact Level compliance validations (NIST 800-53 Rev 5)");

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // High baseline includes all Moderate controls plus additional requirements
            // The FedRampRules (Moderate) should also be installed - this adds High-specific controls

            // AC-2(2): Automated temporary/emergency account removal
            rules.addAll(validateAutomatedAccountRemoval(ctx));

            // AC-2(13): Disable accounts for high-risk individuals
            rules.addAll(validateHighRiskAccountDisabling(ctx));

            // CP-6(1): Separation from primary site
            rules.addAll(validateAlternateSiteSeparation(ctx));

            // CP-7: Alternate processing site
            rules.addAll(validateAlternateProcessingSite(ctx));

            // IR-4(1): Automated incident handling
            rules.addAll(validateAutomatedIncidentHandling(ctx));

            // SC-7(18): Fail secure
            rules.addAll(validateFailSecure(ctx));

            // SI-4(5): System-generated alerts
            rules.addAll(validateSystemGeneratedAlerts(ctx));

            // Return all failures
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(java.util.Optional::stream)
                .toList();
        });

        LOG.info("FedRAMP High rules installed - additional High baseline controls validated");
    }

    /**
     * AC-2(2): Automated Temporary and Emergency Account Removal
     * High baseline requires automated removal of temporary accounts.
     */
    private List<ComplianceRule> validateAutomatedAccountRemoval(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-AC-2(2)",
                "Automated Account Removal",
                "Security profile configuration required for automated account management"));
            return rules;
        }

        // For High baseline, AWS Config rules should monitor IAM for temporary accounts
        if (config.isAwsConfigEnabled()) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-AC-2(2)",
                "Automated Account Removal - AWS Config enabled for account monitoring"));
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-AC-2(2)",
                "Automated Account Removal",
                "AWS Config required for automated account lifecycle monitoring in High baseline"));
        }

        return rules;
    }

    /**
     * AC-2(13): Disable Accounts for High-Risk Individuals
     * High baseline requires automated disabling of accounts for high-risk users.
     */
    private List<ComplianceRule> validateHighRiskAccountDisabling(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // GuardDuty integration enables detection of high-risk account behavior
        if (config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-AC-2(13)",
                "High-Risk Account Monitoring - GuardDuty enabled for anomaly detection"));
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-AC-2(13)",
                "High-Risk Account Monitoring",
                "GuardDuty required for detecting high-risk account behavior in High baseline"));
        }

        return rules;
    }

    /**
     * CP-6(1): Separation from Primary Site
     * High baseline requires geographically separated alternate storage.
     */
    private List<ComplianceRule> validateAlternateSiteSeparation(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // Cross-region backup indicates geographic separation
        if (config.isCrossRegionBackupEnabled()) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-CP-6(1)",
                "Alternate Site Separation - Cross-region backup configured"));
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-CP-6(1)",
                "Alternate Site Separation",
                "Cross-region backup required for geographic separation in High baseline"));
        }

        return rules;
    }

    /**
     * CP-7: Alternate Processing Site
     * High baseline requires an alternate processing site for continuity.
     */
    private List<ComplianceRule> validateAlternateProcessingSite(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // Multi-AZ provides alternate processing within a region
        // For true High compliance, multi-region would be recommended
        if (config.isMultiAzEnforced()) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-CP-7",
                "Alternate Processing Site - Multi-AZ deployment configured"));

            // Advisory: recommend multi-region for full High compliance
            LOG.info("CP-7 Advisory: For full FedRAMP High compliance, consider multi-region deployment");
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-CP-7",
                "Alternate Processing Site",
                "Multi-AZ deployment required for alternate processing capability in High baseline"));
        }

        return rules;
    }

    /**
     * IR-4(1): Automated Incident Handling
     * High baseline requires automated incident handling processes.
     */
    private List<ComplianceRule> validateAutomatedIncidentHandling(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // Security Hub provides automated incident aggregation and handling
        boolean hasAutomatedIncidentHandling = config.isGuardDutyEnabled() &&
                                               config.isSecurityMonitoringEnabled();

        if (hasAutomatedIncidentHandling) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-IR-4(1)",
                "Automated Incident Handling - GuardDuty and Security monitoring enabled"));
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-IR-4(1)",
                "Automated Incident Handling",
                "GuardDuty and Security monitoring required for automated incident handling in High baseline"));
        }

        return rules;
    }

    /**
     * SC-7(18): Fail Secure
     * High baseline requires systems to fail in a secure state.
     */
    private List<ComplianceRule> validateFailSecure(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Security groups with default deny provide fail-secure behavior
        // VPC configuration with private subnets ensures fail-secure networking
        NetworkMode networkMode = ctx.cfc.networkMode();
        boolean hasFailSecureNetwork = networkMode != null &&
                                       networkMode != NetworkMode.PUBLIC;

        if (hasFailSecureNetwork) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-SC-7(18)",
                "Fail Secure - Network configured with private subnets (default deny)"));
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-SC-7(18)",
                "Fail Secure",
                "Private subnet deployment required for fail-secure network configuration in High baseline"));
        }

        return rules;
    }

    /**
     * SI-4(5): System-Generated Alerts
     * High baseline requires automated security alerts.
     */
    private List<ComplianceRule> validateSystemGeneratedAlerts(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // GuardDuty provides system-generated security alerts
        // CloudWatch Alarms provide system-generated operational alerts
        boolean hasSystemAlerts = config.isGuardDutyEnabled() &&
                                  config.isSecurityMonitoringEnabled();

        if (hasSystemAlerts) {
            rules.add(ComplianceRule.pass("FEDRAMP-HIGH-SI-4(5)",
                "System-Generated Alerts - GuardDuty and CloudWatch monitoring enabled"));
        } else {
            rules.add(ComplianceRule.fail("FEDRAMP-HIGH-SI-4(5)",
                "System-Generated Alerts",
                "GuardDuty and security monitoring required for automated alerts in High baseline"));
        }

        return rules;
    }
}
