package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.Set;

/**
 * Database security compliance validation rules.
 *
 * <p>These rules enforce database security best practices across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 3.4, 8.7: Database encryption and access control</li>
 *   <li><b>HIPAA</b> - §164.312(a)(2)(iv), §164.310(d): Database encryption and backup</li>
 *   <li><b>SOC 2</b> - CC6.1, A1.3: Data protection and availability</li>
 *   <li><b>GDPR</b> - Art.32, Art.25: Security and data protection by design</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>RDS encryption at rest enforcement</li>
 *   <li>RDS automated backup validation</li>
 *   <li>Multi-AZ deployment for production</li>
 *   <li>Database activity monitoring</li>
 *   <li>DynamoDB encryption and backup</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Automatically loaded via FrameworkLoader (v2.0 pattern)
 * // Or manually: new DatabaseSecurityRules().install(ctx);
 * }</pre>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "DatabaseSecurity",
    priority = -5,
    alwaysLoad = true,
    displayName = "Database Security",
    description = "Cross-framework database security validation"
)
public class DatabaseSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(DatabaseSecurityRules.class.getName());

    /** Controls that still block STAGING synthesis: encryption at rest -- PHI/cardholder/federal
     *  data may exist in a provisioned STAGING database, same rationale as the encryption-at-rest
     *  entries in HipaaRules/PciDssRules/FedRampRules' own STAGING_BLOCKING_RULES. Everything else
     *  here (backup, Multi-AZ, PITR, activity-streams monitoring) stays non-blocking. */
    private static final Set<String> STAGING_BLOCKING_RULES = Set.of("RDS-ENCRYPTION", "DYNAMODB-ENCRYPTION");

    /**
     * Install database security validation rules.
     * These rules apply primarily to PRODUCTION environments.
     *
     * @param ctx System context
     */
    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing database security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // RDS security validation
            rules.addAll(validateRdsSecurity(ctx));

            // Database access control (public access restriction + IAM auth) -- runs against
            // ctx.dbConnection, same real-infrastructure gate validateRdsSecurity now uses
            rules.addAll(validateDatabaseAccessControl(ctx));

            // DynamoDB security validation
            rules.addAll(validateDynamoDbSecurity(ctx));

            // Database monitoring
            rules.addAll(validateDatabaseMonitoring(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Database Security validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // DEV is advisory only. STAGING blocks on the subset in STAGING_BLOCKING_RULES
                // (encryption at rest); everything else is a visible, non-blocking finding.
                if (ctx.security == SecurityProfile.DEV) {
                    return List.of();
                }
                if (ctx.security == SecurityProfile.STAGING) {
                    return failedRules.stream()
                        .filter(rule -> STAGING_BLOCKING_RULES.contains(rule.ruleId()))
                        .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                        .toList();
                }

                // For PRODUCTION, convert to error strings
                return failedRules.stream()
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
            } else {
                LOG.info("Database Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate RDS database security configuration.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Encryption at rest enabled</li>
     *   <li>Automated backups enabled</li>
     *   <li>Multi-AZ deployment for production</li>
     *   <li>Minor version auto-upgrade</li>
     * </ul>
     */
    private List<ComplianceRule> validateRdsSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Gate on ctx.dbConnection, the "was a database provisioned" signal every other
        // matrix-controls check in this class uses (see validateDatabaseAccessControl), not the
        // self-attested "rdsEnabled" flag -- a stale/mismatched flag would otherwise skip
        // encryption/backup/multi-AZ checks against a provisioned database, or evaluate them
        // against config that drives no infrastructure.
        if (ctx.dbConnection.get().isEmpty()) {
            // No RDS in use, skip validation
            rules.add(ComplianceRule.pass(
                "RDS-NOT-USED",
                "RDS not enabled - validation skipped"
            ));
            return rules;
        }

        // RDS encryption at rest -- read the field RdsFactory actually consumes
        // (enableEncryption), not the disconnected "rdsEncryptionEnabled" name.
        boolean rdsEncryptionEnabled = getBooleanSetting(ctx, "enableEncryption", true);

        if (!rdsEncryptionEnabled) {
            rules.add(ComplianceRule.fail(
                "RDS-ENCRYPTION",
                "RDS encryption at rest must be enabled",
                "RdsEncryptionAtRestEnabled",
                "Enable RDS encryption at rest for all database instances. " +
                "PCI-DSS Req 3.4, HIPAA §164.312(a)(2)(iv), GDPR Art.32(1)(a). " +
                "Set enableEncryption = true in deployment context."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "RDS-ENCRYPTION",
                "RDS encryption at rest enabled",
                "RdsEncryptionAtRestEnabled"
            ));
        }

        // RDS automated backups
        boolean rdsBackupEnabled = getBooleanSetting(ctx, "rdsBackupEnabled", true);

        if (!rdsBackupEnabled) {
            rules.add(ComplianceRule.fail(
                "RDS-BACKUP",
                "RDS automated backups must be enabled",
                "RdsBackupEnabled",
                "Enable automated backups for RDS instances (7-35 days retention). " +
                "HIPAA §164.310(d)(2)(iii), SOC2 A1.3, GDPR Art.32(1)(c). " +
                "Set rdsBackupEnabled = true in deployment context."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "RDS-BACKUP",
                "RDS automated backups enabled",
                "RdsBackupEnabled"
            ));
        }

        // Multi-AZ for production - use ComplianceMatrix. Mirror RdsFactory.createDatabase's own
        // override-then-profile-default resolution (databaseMultiAz context override, falling back
        // to SecurityProfileConfiguration.isRdsDatabaseMultiAzEnabled(), which PRODUCTION defaults
        // to true for even with no override set) -- reading the raw override flag alone with a
        // hardcoded false default would flag a database RdsFactory actually built with Multi-AZ on.
        Boolean multiAzOverride = ctx.cfc.databaseMultiAz();
        boolean rdsMultiAz = multiAzOverride != null ? multiAzOverride
            : ctx.securityProfileConfig.get().map(c -> c.isRdsDatabaseMultiAzEnabled()).orElse(false);

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        ComplianceMatrix.ValidationResult multiAzResult = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ,
            complianceFrameworks,
            rdsMultiAz,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) {
            if (multiAzResult == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "RDS-MULTI-AZ",
                    "RDS Multi-AZ deployment required for " + complianceFrameworks,
                    "RdsMultiAzEnabled",
                    "Enable Multi-AZ deployment for high availability. " +
                    "Set databaseMultiAz = true in deployment context."
                ));
            } else if (multiAzResult == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("RDS Multi-AZ recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.advisory(
                    "RDS-MULTI-AZ",
                    "RDS Multi-AZ is advisory for " + complianceFrameworks + " (recommended but not required)",
                    "RdsMultiAzEnabled",
                    "Enable Multi-AZ on the RDS instance for higher availability."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "RDS-MULTI-AZ",
                    "RDS Multi-AZ deployment enabled",
                    "RdsMultiAzEnabled"
                ));
            }
        }

        // Backup retention period
        int rdsBackupRetentionDays = getIntSetting(ctx, "rdsBackupRetentionDays", 7);

        if ((ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) && rdsBackupRetentionDays < 7) {
            rules.add(ComplianceRule.fail(
                "RDS-BACKUP-RETENTION",
                "RDS backup retention must be at least 7 days for production",
                "Minimum 7 days backup retention required. Current: " + rdsBackupRetentionDays + " days. " +
                "Set rdsBackupRetentionDays = 7 (or higher) in deployment context."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "RDS-BACKUP-RETENTION",
                "RDS backup retention configured: " + rdsBackupRetentionDays + " days"
            ));
        }

        // Minor version auto-upgrade (security patches)
        boolean rdsAutoMinorVersionUpgrade = getBooleanSetting(ctx, "rdsAutoMinorVersionUpgrade", true);

        if (!rdsAutoMinorVersionUpgrade && (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING)) {
            rules.add(ComplianceRule.fail(
                "RDS-AUTO-UPGRADE",
                "RDS automatic minor version upgrades recommended for production",
                "Enable automatic minor version upgrades for security patches. " +
                "PCI-DSS Req 6.2. Set rdsAutoMinorVersionUpgrade = true."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "RDS-AUTO-UPGRADE",
                "RDS automatic minor version upgrades enabled"
            ));
        }

        return rules;
    }

    /**
     * Public access restriction and IAM authentication (matrix: DATABASE_ACCESS_CONTROL). Runs
     * unconditionally against {@code ctx.dbConnection} -- the "was a database provisioned"
     * signal every other framework's matrix-controls checks use -- rather than the self-attested
     * {@code rdsEnabled} flag {@link #validateRdsSecurity} reads, since this control's guarantee
     * comes from {@code RdsFactory} itself: it hardcodes {@code publiclyAccessible(false)}
     * unconditionally and derives {@code iamAuthentication} purely from the security profile, with
     * no override path in the factory. Verified against RdsFactory's source.
     */
    private List<ComplianceRule> validateDatabaseAccessControl(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        if (ctx.dbConnection.get().isEmpty()) {
            return rules;
        }

        boolean iamAuthRequired = ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING;
        rules.add(ComplianceRule.pass(
            "RDS-ACCESS-CONTROL",
            "RDS is never publicly accessible by construction (RdsFactory hardcodes " +
            "publiclyAccessible=false)" + (iamAuthRequired ? ", and IAM authentication is enabled for " + ctx.security : "")
        ));

        return rules;
    }

    /**
     * Validate DynamoDB security configuration.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Encryption at rest enabled</li>
     *   <li>Point-in-time recovery enabled</li>
     *   <li>Backup enabled</li>
     * </ul>
     */
    private List<ComplianceRule> validateDynamoDbSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        boolean dynamoDbEnabled = getBooleanSetting(ctx, "dynamoDbEnabled", false);

        if (!dynamoDbEnabled) {
            // No DynamoDB in use, skip validation
            rules.add(ComplianceRule.pass(
                "DYNAMODB-NOT-USED",
                "DynamoDB not enabled - validation skipped"
            ));
            return rules;
        }

        // DynamoDB encryption at rest
        boolean dynamoDbEncryptionEnabled = getBooleanSetting(ctx, "dynamoDbEncryptionEnabled", true);

        if (!dynamoDbEncryptionEnabled) {
            rules.add(ComplianceRule.fail(
                "DYNAMODB-ENCRYPTION",
                "DynamoDB encryption at rest must be enabled",
                "DynamoDbEncryptionEnabled",
                "Enable DynamoDB encryption at rest (KMS). " +
                "PCI-DSS Req 3.4, HIPAA §164.312(a)(2)(iv), GDPR Art.32(1)(a). " +
                "Set dynamoDbEncryptionEnabled = true in deployment context."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "DYNAMODB-ENCRYPTION",
                "DynamoDB encryption at rest enabled",
                "DynamoDbEncryptionEnabled"
            ));
        }

        // DynamoDB Point-in-Time Recovery - use ComplianceMatrix
        boolean dynamoDbPitrEnabled = getBooleanSetting(ctx, "dynamoDbPitrEnabled", false);

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        ComplianceMatrix.ValidationResult pitrResult = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.DATABASE_PITR,
            complianceFrameworks,
            dynamoDbPitrEnabled,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) {
            if (pitrResult == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "DYNAMODB-PITR",
                    "DynamoDB Point-in-Time Recovery required for " + complianceFrameworks,
                    "DynamoDbPitrEnabled",
                    "Enable Point-in-Time Recovery for production tables. " +
                    "Set dynamoDbPitrEnabled = true in deployment context."
                ));
            } else if (pitrResult == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("DynamoDB PITR recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.advisory(
                    "DYNAMODB-PITR",
                    "DynamoDB PITR is advisory for " + complianceFrameworks + " (recommended but not required)",
                    "DynamoDbPitrEnabled",
                    "Enable point-in-time recovery on the DynamoDB table."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "DYNAMODB-PITR",
                    "DynamoDB Point-in-Time Recovery enabled",
                    "DynamoDbPitrEnabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate database monitoring and auditing.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Database activity monitoring enabled</li>
     *   <li>Performance Insights enabled</li>
     *   <li>Enhanced monitoring enabled</li>
     * </ul>
     */
    private List<ComplianceRule> validateDatabaseMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Gate on either signal, not the self-attested "rdsEnabled" flag alone -- a real
        // ctx.dbConnection with no flag set would otherwise skip validation for a database that
        // was actually provisioned. Keep the flag as an alternate trigger too: DB-ACTIVITY-STREAMS
        // below specifically checks ctx.dbConnection itself, so it still needs to run (and fail)
        // when the flag claims a database but ctx.dbConnection is empty -- that mismatch is
        // exactly what it exists to catch.
        boolean rdsEnabled = getBooleanSetting(ctx, "rdsEnabled", false);
        if (!rdsEnabled && ctx.dbConnection.get().isEmpty()) {
            return rules; // Skip if RDS not in use
        }

        // Database activity logging. Real RDS Activity Streams only supports Aurora
        // MySQL/PostgreSQL, Oracle, and SQL Server -- RdsFactory only provisions standalone
        // PostgreSQL/MySQL/MariaDB via DatabaseInstance, so it's out of scope until engine
        // support catches up. In the meantime, check the signal RdsFactory already provides
        // unconditionally for every engine it does support: general/slow-query/DDL logging
        // enabled in the parameter group and exported to CloudWatch Logs (see
        // RdsFactory.createParameterGroup / getCloudWatchLogsExports). Same
        // provisioned-resource signal as validateDatabaseAccessControl above, not the
        // self-attested flag.
        boolean dbActivityStreamsEnabled = ctx.dbConnection.get().isPresent();

        if ((ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) && !dbActivityStreamsEnabled) {
            rules.add(ComplianceRule.fail(
                "DB-ACTIVITY-STREAMS",
                "Database activity logging (CloudWatch Logs export of general/slow-query/DDL logs) not present",
                "No database was provisioned, so RdsFactory's parameter-group logging and " +
                "CloudWatch Logs export never ran. PCI-DSS Req 10.2, HIPAA §164.312(b)."
            ));
        } else if (dbActivityStreamsEnabled) {
            rules.add(ComplianceRule.pass(
                "DB-ACTIVITY-STREAMS",
                "Database activity logging enabled (general/slow-query/DDL logs exported to CloudWatch Logs)"
            ));
        }

        // Performance Insights
        boolean performanceInsightsEnabled = getBooleanSetting(ctx, "performanceInsightsEnabled", false);

        if (performanceInsightsEnabled) {
            rules.add(ComplianceRule.pass(
                "DB-PERFORMANCE-INSIGHTS",
                "RDS Performance Insights enabled"
            ));

            // Performance Insights encryption
            boolean performanceInsightsEncrypted = getBooleanSetting(ctx, "performanceInsightsEncrypted", true);

            if (!performanceInsightsEncrypted) {
                rules.add(ComplianceRule.fail(
                    "DB-PERFORMANCE-INSIGHTS-ENCRYPTION",
                    "Performance Insights data must be encrypted",
                    "Enable encryption for Performance Insights data. " +
                    "Set performanceInsightsEncrypted = true."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "DB-PERFORMANCE-INSIGHTS-ENCRYPTION",
                    "Performance Insights encryption enabled"
                ));
            }
        }

        // Enhanced Monitoring
        boolean enhancedMonitoringEnabled = getBooleanSetting(ctx, "rdsEnhancedMonitoringEnabled", false);

        if ((ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) && !enhancedMonitoringEnabled) {
            rules.add(ComplianceRule.fail(
                "DB-ENHANCED-MONITORING",
                "RDS Enhanced Monitoring recommended for production",
                "Enable Enhanced Monitoring for real-time OS metrics. " +
                "SOC2 CC7.2. Set rdsEnhancedMonitoringEnabled = true."
            ));
        } else if (enhancedMonitoringEnabled) {
            rules.add(ComplianceRule.pass(
                "DB-ENHANCED-MONITORING",
                "RDS Enhanced Monitoring enabled"
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
     * Helper method to safely get integer settings from deployment context.
     */
    private int getIntSetting(SystemContext ctx, String key, int defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Controls checked across every {@code validate*} method above -- see {@link
     * com.cloudforge.core.interfaces.FrameworkRules#claimedControls}. RDS automatic minor-version
     * upgrade and backup-retention-period length are conditional checks with no corresponding
     * {@link ComplianceMatrix.SecurityControl} entry, so they aren't claimed here.
     *
     * <p>{@code validateRdsSecurity} and {@link #validateDatabaseAccessControl} both gate on
     * {@code ctx.dbConnection} -- the "was a database provisioned" signal every other framework's
     * matrix-controls checks use -- rather than a self-attested context flag, so
     * RDS-ENCRYPTION/RDS-BACKUP/RDS-MULTI-AZ/etc can't validate a database that doesn't exist or
     * skip one that does.
     */
    @Override
    public Set<String> claimedControls() {
        return Set.of(
            "ENCRYPTION_AT_REST", "BACKUP_RECOVERY", "DATABASE_MULTI_AZ", "DATABASE_PITR",
            "DATABASE_LOGGING", "DATABASE_ACCESS_CONTROL"
        );
    }
}
