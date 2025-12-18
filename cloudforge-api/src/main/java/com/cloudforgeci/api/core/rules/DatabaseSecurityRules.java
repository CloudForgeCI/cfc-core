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

                // For DEV, these are advisory only
                if (ctx.security == SecurityProfile.DEV) {
                    return List.of();
                }

                // For PRODUCTION/STAGING, convert to error strings
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

        boolean rdsEnabled = getBooleanSetting(ctx, "rdsEnabled", false);

        if (!rdsEnabled) {
            // No RDS in use, skip validation
            rules.add(ComplianceRule.pass(
                "RDS-NOT-USED",
                "RDS not enabled - validation skipped"
            ));
            return rules;
        }

        // RDS encryption at rest
        boolean rdsEncryptionEnabled = getBooleanSetting(ctx, "rdsEncryptionEnabled", false);

        if (!rdsEncryptionEnabled) {
            rules.add(ComplianceRule.fail(
                "RDS-ENCRYPTION",
                "RDS encryption at rest must be enabled",
                "RdsEncryptionAtRestEnabled",
                "Enable RDS encryption at rest for all database instances. " +
                "PCI-DSS Req 3.4, HIPAA §164.312(a)(2)(iv), GDPR Art.32(1)(a). " +
                "Set rdsEncryptionEnabled = true in deployment context."
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

        // Multi-AZ for production - use ComplianceMatrix
        boolean rdsMultiAz = getBooleanSetting(ctx, "rdsMultiAz", false);

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult multiAzResult = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ,
            complianceFrameworks,
            rdsMultiAz,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (multiAzResult == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "RDS-MULTI-AZ",
                    "RDS Multi-AZ deployment required for " + complianceFrameworks,
                    "RdsMultiAzEnabled",
                    "Enable Multi-AZ deployment for high availability. " +
                    "Set rdsMultiAz = true in deployment context."
                ));
            } else if (multiAzResult == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("RDS Multi-AZ recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "RDS-MULTI-AZ",
                    "RDS Multi-AZ is advisory for " + complianceFrameworks + " (recommended but not required)",
                    "RdsMultiAzEnabled"
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

        if (ctx.security == SecurityProfile.PRODUCTION && rdsBackupRetentionDays < 7) {
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

        if (!rdsAutoMinorVersionUpgrade && ctx.security == SecurityProfile.PRODUCTION) {
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
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult pitrResult = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.DATABASE_PITR,
            complianceFrameworks,
            dynamoDbPitrEnabled,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
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
                rules.add(ComplianceRule.pass(
                    "DYNAMODB-PITR",
                    "DynamoDB PITR is advisory for " + complianceFrameworks + " (recommended but not required)",
                    "DynamoDbPitrEnabled"
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

        boolean rdsEnabled = getBooleanSetting(ctx, "rdsEnabled", false);

        if (!rdsEnabled) {
            return rules; // Skip if RDS not in use
        }

        // Database Activity Streams (audit logging)
        boolean dbActivityStreamsEnabled = getBooleanSetting(ctx, "dbActivityStreamsEnabled", false);

        if (ctx.security == SecurityProfile.PRODUCTION && !dbActivityStreamsEnabled) {
            rules.add(ComplianceRule.fail(
                "DB-ACTIVITY-STREAMS",
                "Database Activity Streams recommended for production audit logging",
                "Enable Database Activity Streams for real-time audit logging. " +
                "PCI-DSS Req 10.2, HIPAA §164.312(b). " +
                "Set dbActivityStreamsEnabled = true in deployment context."
            ));
        } else if (dbActivityStreamsEnabled) {
            rules.add(ComplianceRule.pass(
                "DB-ACTIVITY-STREAMS",
                "Database Activity Streams enabled"
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

        if (ctx.security == SecurityProfile.PRODUCTION && !enhancedMonitoringEnabled) {
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
}
