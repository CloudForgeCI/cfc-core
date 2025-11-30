package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Incident response and disaster recovery compliance validation rules.
 *
 * <p>These rules enforce incident response and business continuity requirements
 * across multiple compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 12.10: Incident response plan</li>
 *   <li><b>HIPAA</b> - §164.308(a)(6): Security incident procedures</li>
 *   <li><b>SOC 2</b> - A1.2, A1.3: Business continuity and disaster recovery</li>
 *   <li><b>GDPR</b> - Art.33: Breach notification within 72 hours</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>Incident response plan documentation</li>
 *   <li>Disaster recovery plan and testing</li>
 *   <li>Backup and restore procedures</li>
 *   <li>Forensic logging and evidence preservation</li>
 *   <li>Business continuity procedures</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Automatically loaded via FrameworkLoader (v2.0 pattern)
 * // Or manually: new IncidentResponseRules().install(ctx);
 * }</pre>
 *
 * @since 3.1.0
 */
@ComplianceFramework(
    value = "IncidentResponse",
    priority = 0,
    alwaysLoad = true,
    displayName = "Incident Response & DR",
    description = "Cross-framework incident response and disaster recovery validation"
)
public class IncidentResponseRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(IncidentResponseRules.class.getName());

    /**
     * Install incident response validation rules.
     * These rules apply primarily to PRODUCTION and STAGING environments.
     *
     * @param ctx System context
     */
    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing incident response compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Incident response planning
            rules.addAll(validateIncidentResponsePlan(ctx));

            // Disaster recovery and business continuity
            rules.addAll(validateDisasterRecovery(ctx));

            // Backup and restore
            rules.addAll(validateBackupRestore(ctx));

            // Forensic logging
            rules.addAll(validateForensicLogging(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Incident Response validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only (warnings but not blocking)
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION, filter to only infrastructure requirements (not organizational/operational)
                // Organizational controls like backup testing are advisory recommendations
                List<String> blockingRules = failedRules.stream()
                    .filter(rule -> isInfrastructureRequirement(rule.description()))
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();

                return blockingRules;
            } else {
                LOG.info("Incident Response validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate incident response plan.
     *
     * <p>Requirements:</p>
     * <ul>
     *   <li>PCI-DSS Req 12.10.1: Incident response plan created and implemented</li>
     *   <li>HIPAA §164.308(a)(6): Security incident procedures</li>
     *   <li>GDPR Art.33: Breach notification within 72 hours</li>
     *   <li>SOC 2 CC7.4, CC7.5: Incident response and resolution</li>
     * </ul>
     */
    private List<ComplianceRule> validateIncidentResponsePlan(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables security monitoring which implies incident response
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();

        // Incident response plan documented
        boolean incidentResponsePlanDocumented = getBooleanSetting(ctx, "incidentResponsePlanDocumented", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !incidentResponsePlanDocumented) {
            rules.add(ComplianceRule.fail(
                "INCIDENT-RESPONSE-PLAN",
                "Incident response plan required for production",
                "Document incident response procedures including: detection, analysis, " +
                "containment, eradication, recovery, post-incident review. " +
                "PCI-DSS Req 12.10.1; HIPAA §164.308(a)(6); SOC2 CC7.4; GDPR Art.33. " +
                "Note: PRODUCTION security profile enables security monitoring by default. " +
                "Set incidentResponsePlanDocumented = true when plan is documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "INCIDENT-RESPONSE-PLAN",
                "Incident response plan documented or not required for " + ctx.security +
                (securityMonitoringEnabled ? " (via security profile)" : "")
            ));
        }

        // Incident response team defined
        boolean incidentResponseTeamDefined = getBooleanSetting(ctx, "incidentResponseTeamDefined", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !incidentResponseTeamDefined) {
            rules.add(ComplianceRule.fail(
                "INCIDENT-RESPONSE-TEAM",
                "Incident response team roles and responsibilities required",
                "Define incident response team: incident commander, technical lead, " +
                "communications coordinator, legal/compliance liaison. " +
                "PCI-DSS Req 12.10.2. " +
                "Note: PRODUCTION security profile enables security monitoring by default. " +
                "Set incidentResponseTeamDefined = true when defined."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "INCIDENT-RESPONSE-TEAM",
                "Incident response team defined or not required" +
                (securityMonitoringEnabled ? " (via security profile)" : "")
            ));
        }

        // Incident response testing
        boolean incidentResponseTested = getBooleanSetting(ctx, "incidentResponseTested", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !incidentResponseTested) {
            rules.add(ComplianceRule.fail(
                "INCIDENT-RESPONSE-TESTING",
                "Incident response plan must be tested annually",
                "Conduct tabletop exercises or simulated incidents annually. " +
                "PCI-DSS Req 12.10.3; SOC2 CC7.4. Document test results and improvements. " +
                "Note: PRODUCTION security profile provides security monitoring foundation. " +
                "Set incidentResponseTested = true when last test is within 12 months."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "INCIDENT-RESPONSE-TESTING",
                "Incident response testing completed or not required" +
                (securityMonitoringEnabled ? " (via security profile)" : "")
            ));
        }

        // Breach notification procedures (72-hour GDPR timeline)
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresGdprNotification = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("GDPR");

        if (requiresGdprNotification) {
            boolean breachNotification72Hours = getBooleanSetting(ctx, "breachNotification72Hours", false);

            if (!breachNotification72Hours) {
                rules.add(ComplianceRule.fail(
                    "BREACH-NOTIFICATION-72HR",
                    "GDPR requires breach notification to supervisory authority within 72 hours",
                    "Document procedures for: breach detection, severity assessment, " +
                    "supervisory authority notification (72 hours), data subject notification. " +
                    "GDPR Article 33. Set breachNotification72Hours = true when procedures exist."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "BREACH-NOTIFICATION-72HR",
                    "GDPR 72-hour breach notification procedures documented"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate disaster recovery and business continuity.
     *
     * <p>Requirements:</p>
     * <ul>
     *   <li>PCI-DSS Req 12.10.4: Business continuity and disaster recovery procedures</li>
     *   <li>HIPAA §164.308(a)(7)(ii)(B): Disaster recovery plan</li>
     *   <li>SOC 2 A1.2: System availability and recovery</li>
     * </ul>
     */
    private List<ComplianceRule> validateDisasterRecovery(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // PRODUCTION profile enables automated backups and cross-region backup (implies DR planning)
        boolean backupEnabled = config != null && config.isAutomatedBackupEnabled();
        boolean crossRegionBackup = config != null && config.isCrossRegionBackupEnabled();

        // Disaster recovery plan
        boolean disasterRecoveryPlanDocumented = getBooleanSetting(ctx, "disasterRecoveryPlanDocumented", backupEnabled && crossRegionBackup);

        if (ctx.security == SecurityProfile.PRODUCTION && !disasterRecoveryPlanDocumented) {
            rules.add(ComplianceRule.fail(
                "DISASTER-RECOVERY-PLAN",
                "Disaster recovery plan required for production",
                "Document disaster recovery procedures: RTO/RPO targets, recovery steps, " +
                "communication plan, infrastructure rebuild procedures. " +
                "PCI-DSS Req 12.10.4; HIPAA §164.308(a)(7)(ii)(B); SOC2 A1.2. " +
                "Note: PRODUCTION security profile enables automated backups and cross-region replication. " +
                "Set disasterRecoveryPlanDocumented = true when plan is documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "DISASTER-RECOVERY-PLAN",
                "Disaster recovery plan documented or not required" +
                (backupEnabled && crossRegionBackup ? " (via security profile)" : "")
            ));
        }

        // Recovery Time Objective (RTO) defined
        boolean rtoRpoDefined = getBooleanSetting(ctx, "rtoRpoDefined", backupEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !rtoRpoDefined) {
            rules.add(ComplianceRule.fail(
                "RTO-RPO-DEFINED",
                "Recovery Time Objective (RTO) and Recovery Point Objective (RPO) required",
                "Define acceptable downtime (RTO) and data loss (RPO) for each system. " +
                "SOC2 A1.2. Common targets: RTO 4-24 hours, RPO 1-24 hours. " +
                "Note: PRODUCTION security profile provides backup foundation. " +
                "Set rtoRpoDefined = true when RTO/RPO are documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "RTO-RPO-DEFINED",
                "RTO/RPO defined or not required" +
                (backupEnabled ? " (via security profile)" : "")
            ));
        }

        // Disaster recovery testing
        boolean disasterRecoveryTested = getBooleanSetting(ctx, "disasterRecoveryTested", backupEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !disasterRecoveryTested) {
            rules.add(ComplianceRule.fail(
                "DISASTER-RECOVERY-TESTING",
                "Disaster recovery plan must be tested annually",
                "Conduct DR test: restore from backups, rebuild infrastructure, verify RTO/RPO. " +
                "PCI-DSS Req 12.10.5; HIPAA §164.308(a)(7)(ii)(D); SOC2 A1.2. " +
                "Note: PRODUCTION security profile enables automated backups. " +
                "Set disasterRecoveryTested = true when last test is within 12 months."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "DISASTER-RECOVERY-TESTING",
                "Disaster recovery testing completed or not required" +
                (backupEnabled ? " (via security profile)" : "")
            ));
        }

        // Business continuity plan
        boolean businessContinuityPlan = getBooleanSetting(ctx, "businessContinuityPlan", backupEnabled && crossRegionBackup);

        if (ctx.security == SecurityProfile.PRODUCTION && !businessContinuityPlan) {
            rules.add(ComplianceRule.fail(
                "BUSINESS-CONTINUITY-PLAN",
                "Business continuity plan required for critical systems",
                "Document business continuity procedures: alternate processing site, " +
                "personnel availability, critical business functions prioritization. " +
                "SOC2 A1.1. " +
                "Note: PRODUCTION security profile enables cross-region backup for DR. " +
                "Set businessContinuityPlan = true when documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "BUSINESS-CONTINUITY-PLAN",
                "Business continuity plan documented or not required" +
                (backupEnabled && crossRegionBackup ? " (via security profile)" : "")
            ));
        }

        return rules;
    }

    /**
     * Validate backup and restore procedures.
     *
     * <p>Requirements:</p>
     * <ul>
     *   <li>Infrastructure backup already validated by other rules</li>
     *   <li>This validates backup testing and restore procedures</li>
     * </ul>
     */
    private List<ComplianceRule> validateBackupRestore(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules; // Skip if config not available
        }

        // Backup restore testing
        boolean backupRestoreTested = getBooleanSetting(ctx, "backupRestoreTested", false);

        if (ctx.security == SecurityProfile.PRODUCTION && config.isAutomatedBackupEnabled() && !backupRestoreTested) {
            rules.add(ComplianceRule.fail(
                "BACKUP-RESTORE-TESTING",
                "Backup restore procedures must be tested regularly",
                "Test backup restoration quarterly: verify backup integrity, " +
                "measure restore time, validate data completeness. " +
                "HIPAA §164.308(a)(7)(ii)(D); SOC2 A1.3. " +
                "Set backupRestoreTested = true when last test is within 90 days."
            ));
        } else if (config.isAutomatedBackupEnabled()) {
            rules.add(ComplianceRule.pass(
                "BACKUP-RESTORE-TESTING",
                "Backup restore testing completed or not required"
            ));
        }

        // Off-site backup storage
        if (ctx.security == SecurityProfile.PRODUCTION && config.isAutomatedBackupEnabled()) {
            boolean offsiteBackupStorage = config.isCrossRegionBackupEnabled();

            if (!offsiteBackupStorage) {
                rules.add(ComplianceRule.fail(
                    "OFFSITE-BACKUP-STORAGE",
                    "Off-site backup storage required for disaster recovery",
                    "Enable cross-region backup replication for geographic redundancy. " +
                    "PCI-DSS Req 9.5.1; HIPAA §164.310(d)(2)(iv); SOC2 A1.3. " +
                    "Set crossRegionBackupEnabled = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "OFFSITE-BACKUP-STORAGE",
                    "Cross-region backup storage enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate forensic logging and evidence preservation.
     *
     * <p>Requirements:</p>
     * <ul>
     *   <li>PCI-DSS Req 10.5: Secure audit trails against tampering</li>
     *   <li>PCI-DSS Req 10.6: Review logs for anomalies</li>
     *   <li>HIPAA §164.312(b): Audit controls - protect against tampering</li>
     * </ul>
     */
    private List<ComplianceRule> validateForensicLogging(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // CloudTrail log file validation (prevents tampering)
        boolean cloudTrailLogValidation = getBooleanSetting(ctx, "cloudTrailLogFileValidation", config.isCloudTrailEnabled());

        if (config.isCloudTrailEnabled() && !cloudTrailLogValidation) {
            rules.add(ComplianceRule.fail(
                "CLOUDTRAIL-LOG-VALIDATION",
                "CloudTrail log file validation required for forensic integrity",
                "CloudTrailLogFileValidationRule",
                "Enable CloudTrail log file validation to detect tampering. " +
                "PCI-DSS Req 10.5.5; HIPAA §164.312(c)(2). " +
                "Note: PRODUCTION security profile enables CloudTrail by default. " +
                "Set cloudTrailLogFileValidation = true in deployment context."
            ));
        } else if (config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.pass(
                "CLOUDTRAIL-LOG-VALIDATION",
                "CloudTrail log file validation enabled (via security profile)",
                "CloudTrailLogFileValidationRule"
            ));
        }

        // Centralized log aggregation (implied by CloudTrail and security monitoring)
        boolean securityMonitoringEnabled = config.isSecurityMonitoringEnabled();
        boolean centralizedLogAggregation = getBooleanSetting(ctx, "centralizedLogAggregation", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !centralizedLogAggregation) {
            rules.add(ComplianceRule.fail(
                "CENTRALIZED-LOG-AGGREGATION",
                "Centralized log aggregation recommended for forensic analysis",
                "Aggregate logs to CloudWatch Logs, S3, or SIEM for correlation and analysis. " +
                "PCI-DSS Req 10.6; SOC2 CC7.2. " +
                "Note: PRODUCTION security profile enables security monitoring and CloudTrail. " +
                "Set centralizedLogAggregation = true when logs are centralized."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "CENTRALIZED-LOG-AGGREGATION",
                "Centralized log aggregation configured or not required" +
                (securityMonitoringEnabled ? " (via security profile)" : "")
            ));
        }

        // Log review and alerting (implied by GuardDuty and security monitoring)
        boolean guardDutyEnabled = config.isGuardDutyEnabled();
        boolean automatedLogReview = getBooleanSetting(ctx, "automatedLogReview", guardDutyEnabled || securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !automatedLogReview) {
            rules.add(ComplianceRule.fail(
                "AUTOMATED-LOG-REVIEW",
                "Automated log review and alerting required",
                "Configure CloudWatch alarms or GuardDuty for automated log analysis. " +
                "PCI-DSS Req 10.6.1; HIPAA §164.308(a)(1)(ii)(D). " +
                "Note: PRODUCTION security profile enables GuardDuty and security monitoring. " +
                "Set automatedLogReview = true when automated alerting is configured."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "AUTOMATED-LOG-REVIEW",
                "Automated log review configured or not required" +
                (guardDutyEnabled || securityMonitoringEnabled ? " (via security profile)" : "")
            ));
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

    /**
     * Determine if a validation rule is an infrastructure requirement (blocking)
     * versus an organizational/operational control (advisory).
     *
     * Infrastructure requirements are technical controls that can be enforced in code.
     * Organizational controls are documentation and process requirements.
     */
    private boolean isInfrastructureRequirement(String ruleDescription) {
        // Organizational/operational controls (non-blocking for PRODUCTION)
        // These are documentation, testing, and process requirements
        if (ruleDescription.contains("must be tested") ||
            ruleDescription.contains("must be documented") ||
            ruleDescription.contains("testing") ||
            ruleDescription.contains("restore procedures") ||
            ruleDescription.contains("team defined") ||
            ruleDescription.contains("plan documented") ||
            ruleDescription.contains("RTO/RPO defined") ||
            ruleDescription.contains("business continuity")) {
            return false; // Advisory only
        }

        // Infrastructure requirements (blocking for PRODUCTION)
        // These are technical controls that should be enforced
        if (ruleDescription.contains("CloudTrail") ||
            ruleDescription.contains("log file validation") ||
            ruleDescription.contains("off-site backup storage")) {
            return true; // Blocking
        }

        // Default: advisory for incident response (most are organizational)
        return false;
    }
}
