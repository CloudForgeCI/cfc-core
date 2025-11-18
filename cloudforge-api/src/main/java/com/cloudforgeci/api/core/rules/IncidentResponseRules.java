package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;

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
 * // Install incident response validation
 * IncidentResponseRules.install(ctx);
 * }</pre>
 */
public final class IncidentResponseRules {

    private static final Logger LOG = Logger.getLogger(IncidentResponseRules.class.getName());

    private IncidentResponseRules() {}

    /**
     * Install incident response validation rules.
     * These rules apply primarily to PRODUCTION and STAGING environments.
     *
     * @param ctx System context
     */
    public static void install(SystemContext ctx) {
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

                // For DEV, these are advisory only
                if (ctx.security == SecurityProfile.DEV) {
                    return List.of();
                }

                // For PRODUCTION/STAGING, convert to error strings
                return failedRules.stream()
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
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
    private static List<ComplianceRule> validateIncidentResponsePlan(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Incident response plan documented
        boolean incidentResponsePlanDocumented = getBooleanSetting(ctx, "incidentResponsePlanDocumented", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !incidentResponsePlanDocumented) {
            rules.add(ComplianceRule.fail(
                "INCIDENT-RESPONSE-PLAN",
                "Incident response plan required for production",
                "Document incident response procedures including: detection, analysis, " +
                "containment, eradication, recovery, post-incident review. " +
                "PCI-DSS Req 12.10.1; HIPAA §164.308(a)(6); SOC2 CC7.4; GDPR Art.33. " +
                "Set incidentResponsePlanDocumented=true when plan is documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "INCIDENT-RESPONSE-PLAN",
                "Incident response plan documented or not required for " + ctx.security
            ));
        }

        // Incident response team defined
        boolean incidentResponseTeamDefined = getBooleanSetting(ctx, "incidentResponseTeamDefined", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !incidentResponseTeamDefined) {
            rules.add(ComplianceRule.fail(
                "INCIDENT-RESPONSE-TEAM",
                "Incident response team roles and responsibilities required",
                "Define incident response team: incident commander, technical lead, " +
                "communications coordinator, legal/compliance liaison. " +
                "PCI-DSS Req 12.10.2. Set incidentResponseTeamDefined=true when defined."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "INCIDENT-RESPONSE-TEAM",
                "Incident response team defined or not required"
            ));
        }

        // Incident response testing
        boolean incidentResponseTested = getBooleanSetting(ctx, "incidentResponseTested", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !incidentResponseTested) {
            rules.add(ComplianceRule.fail(
                "INCIDENT-RESPONSE-TESTING",
                "Incident response plan must be tested annually",
                "Conduct tabletop exercises or simulated incidents annually. " +
                "PCI-DSS Req 12.10.3; SOC2 CC7.4. Document test results and improvements. " +
                "Set incidentResponseTested=true when last test is within 12 months."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "INCIDENT-RESPONSE-TESTING",
                "Incident response testing completed or not required"
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
                    "GDPR Article 33. Set breachNotification72Hours=true when procedures exist."
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
    private static List<ComplianceRule> validateDisasterRecovery(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Disaster recovery plan
        boolean disasterRecoveryPlanDocumented = getBooleanSetting(ctx, "disasterRecoveryPlanDocumented", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !disasterRecoveryPlanDocumented) {
            rules.add(ComplianceRule.fail(
                "DISASTER-RECOVERY-PLAN",
                "Disaster recovery plan required for production",
                "Document disaster recovery procedures: RTO/RPO targets, recovery steps, " +
                "communication plan, infrastructure rebuild procedures. " +
                "PCI-DSS Req 12.10.4; HIPAA §164.308(a)(7)(ii)(B); SOC2 A1.2. " +
                "Set disasterRecoveryPlanDocumented=true when plan is documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "DISASTER-RECOVERY-PLAN",
                "Disaster recovery plan documented or not required"
            ));
        }

        // Recovery Time Objective (RTO) defined
        boolean rtoRpoDefined = getBooleanSetting(ctx, "rtoRpoDefined", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !rtoRpoDefined) {
            rules.add(ComplianceRule.fail(
                "RTO-RPO-DEFINED",
                "Recovery Time Objective (RTO) and Recovery Point Objective (RPO) required",
                "Define acceptable downtime (RTO) and data loss (RPO) for each system. " +
                "SOC2 A1.2. Common targets: RTO 4-24 hours, RPO 1-24 hours. " +
                "Set rtoRpoDefined=true when RTO/RPO are documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "RTO-RPO-DEFINED",
                "RTO/RPO defined or not required"
            ));
        }

        // Disaster recovery testing
        boolean disasterRecoveryTested = getBooleanSetting(ctx, "disasterRecoveryTested", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !disasterRecoveryTested) {
            rules.add(ComplianceRule.fail(
                "DISASTER-RECOVERY-TESTING",
                "Disaster recovery plan must be tested annually",
                "Conduct DR test: restore from backups, rebuild infrastructure, verify RTO/RPO. " +
                "PCI-DSS Req 12.10.5; HIPAA §164.308(a)(7)(ii)(D); SOC2 A1.2. " +
                "Set disasterRecoveryTested=true when last test is within 12 months."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "DISASTER-RECOVERY-TESTING",
                "Disaster recovery testing completed or not required"
            ));
        }

        // Business continuity plan
        boolean businessContinuityPlan = getBooleanSetting(ctx, "businessContinuityPlan", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !businessContinuityPlan) {
            rules.add(ComplianceRule.fail(
                "BUSINESS-CONTINUITY-PLAN",
                "Business continuity plan required for critical systems",
                "Document business continuity procedures: alternate processing site, " +
                "personnel availability, critical business functions prioritization. " +
                "SOC2 A1.1. Set businessContinuityPlan=true when documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "BUSINESS-CONTINUITY-PLAN",
                "Business continuity plan documented or not required"
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
    private static List<ComplianceRule> validateBackupRestore(SystemContext ctx) {
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
                "Set backupRestoreTested=true when last test is within 90 days."
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
                    "Set crossRegionBackupEnabled=true in deployment context."
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
    private static List<ComplianceRule> validateForensicLogging(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // CloudTrail log file validation (prevents tampering)
        boolean cloudTrailLogValidation = getBooleanSetting(ctx, "cloudTrailLogFileValidation", true);

        if (config.isCloudTrailEnabled() && !cloudTrailLogValidation) {
            rules.add(ComplianceRule.fail(
                "CLOUDTRAIL-LOG-VALIDATION",
                "CloudTrail log file validation required for forensic integrity",
                "CloudTrailLogFileValidationRule",
                "Enable CloudTrail log file validation to detect tampering. " +
                "PCI-DSS Req 10.5.5; HIPAA §164.312(c)(2). " +
                "Set cloudTrailLogFileValidation=true in deployment context."
            ));
        } else if (config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.pass(
                "CLOUDTRAIL-LOG-VALIDATION",
                "CloudTrail log file validation enabled",
                "CloudTrailLogFileValidationRule"
            ));
        }

        // Centralized log aggregation
        boolean centralizedLogAggregation = getBooleanSetting(ctx, "centralizedLogAggregation", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !centralizedLogAggregation) {
            rules.add(ComplianceRule.fail(
                "CENTRALIZED-LOG-AGGREGATION",
                "Centralized log aggregation recommended for forensic analysis",
                "Aggregate logs to CloudWatch Logs, S3, or SIEM for correlation and analysis. " +
                "PCI-DSS Req 10.6; SOC2 CC7.2. " +
                "Set centralizedLogAggregation=true when logs are centralized."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "CENTRALIZED-LOG-AGGREGATION",
                "Centralized log aggregation configured or not required"
            ));
        }

        // Log review and alerting
        boolean automatedLogReview = getBooleanSetting(ctx, "automatedLogReview", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !automatedLogReview) {
            rules.add(ComplianceRule.fail(
                "AUTOMATED-LOG-REVIEW",
                "Automated log review and alerting required",
                "Configure CloudWatch alarms or GuardDuty for automated log analysis. " +
                "PCI-DSS Req 10.6.1; HIPAA §164.308(a)(1)(ii)(D). " +
                "Set automatedLogReview=true when automated alerting is configured."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "AUTOMATED-LOG-REVIEW",
                "Automated log review configured or not required"
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
}
