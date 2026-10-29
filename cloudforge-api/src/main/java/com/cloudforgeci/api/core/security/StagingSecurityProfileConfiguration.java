package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.rules.ComplianceMatrix;
import com.cloudforgeci.api.core.util.RetentionDaysConverter;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.logging.Logger;

/**
 * Staging security profile configuration for pre-production environments.
 *
 * STAGING is designed as a pre-production environment that mirrors PRODUCTION
 * security posture while allowing for compliance testing and validation.
 *
 * Key characteristics:
 * - Supports all compliance frameworks (PCI-DSS, HIPAA, SOC2, GDPR) via deployment context overrides
 * - Defaults can be overridden for framework-specific requirements (e.g., log retention, GuardDuty)
 * - Balances production-like security with operational flexibility for testing
 */
public class StagingSecurityProfileConfiguration extends AbstractHardenedProfileConfiguration {

    private static final Logger LOG = Logger.getLogger(StagingSecurityProfileConfiguration.class.getName());

    /**
     * Create StagingSecurityProfileConfiguration.
     * @param deploymentContext Optional deployment context for overriding defaults
     */
    public StagingSecurityProfileConfiguration(DeploymentContext deploymentContext) {
        super(deploymentContext, "STAGING", LOG);
        LOG.fine("=== STAGING profile constructor called ===");
        LOG.fine("deploymentContext = " + (deploymentContext != null ? "NOT NULL" : "NULL"));
        if (deploymentContext != null) {
            LOG.fine("logRetentionDays = " + deploymentContext.logRetentionDays());
            LOG.fine("guardDutyEnabled = " + deploymentContext.guardDutyEnabled());
        }
    }

    /**
     * Create StagingSecurityProfileConfiguration with no deployment context.
     * Uses only profile defaults.
     */
    public StagingSecurityProfileConfiguration() {
        this(null);
    }

    @Override
    public SecurityProfile getSecurityProfile() {
        return SecurityProfile.STAGING;
    }

    /**
     * Get the effective compliance mode, defaulting intelligently based on whether frameworks are specified.
     * If frameworks are specified, defaults to ENFORCE. If no frameworks, defaults to DISABLED.
     */
    // Logging Configuration - Moderate retention
    @Override
    public RetentionDays getLogRetentionDays() {
        // Check deployment context first for compliance framework overrides
        if (deploymentContext != null && deploymentContext.logRetentionDays() != null) {
            int days = deploymentContext.logRetentionDays();
            RetentionDays retention = RetentionDaysConverter.fromDays(days);
            java.util.logging.Logger.getLogger(getClass().getName())
                .info("STAGING profile: Overriding log retention from deployment context: " + days + " days -> " + retention);
            return retention;
        }
        return RetentionDays.THREE_MONTHS; // Minimum retention for SOC2/GDPR compliance (CC7.2, Art. 30/32)
    }

    @Override
    public RetentionDays getFlowLogRetentionDays() {
        return RetentionDays.THREE_MONTHS; // Minimum retention for SOC2/GDPR compliance
    }

    // Flow Log Configuration - Enhanced monitoring

    // Security Monitoring - Moderate for staging

    @Override
    public boolean isGuardDutyEnabled() {
        // Default: optional for staging (default for non-compliant deployments)
        return controls.resolve("GuardDuty", ComplianceMatrix.SecurityControl.THREAT_DETECTION,
            DeploymentContext::guardDutyEnabled, false);
    }

    @Override
    public boolean isAwsConfigEnabled() {
        return controls.resolve("AWS Config", ComplianceMatrix.SecurityControl.VULNERABILITY_MANAGEMENT,
            DeploymentContext::awsConfigEnabled, false);
    }

    @Override
    public boolean isAuditManagerEnabled() {
        return controls.resolve("Audit Manager", ComplianceMatrix.SecurityControl.AUDIT_MANAGER,
            DeploymentContext::auditManagerServiceEnabled, false);
    }

    // Encryption Configuration - Full encryption

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT
            )) {
                LOG.severe("STAGING profile: EFS Encryption in Transit enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: encryption enabled
        return true;
    }

    // Network Security - Moderate restrictions

    @Override
    public boolean isRestrictSecurityGroupEgressEnabled() {
        // Check deployment context override FIRST
        if (deploymentContext != null && deploymentContext.restrictSecurityGroupEgress() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.restrictSecurityGroupEgress());
            LOG.info("STAGING profile: Security group egress restriction explicitly configured: " + enabled);
            return enabled;
        }

        // Check if compliance matrix requires network segmentation
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION
            )) {
                LOG.info("STAGING profile: Security group egress restriction enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        return false; // Default: allow all outbound unless explicitly enabled
    }

    @Override
    public int getNatGatewayCount(TopologyType topology, RuntimeType runtime, NetworkMode networkMode) {
        // Staging respects network mode for cost optimization
        if (networkMode == NetworkMode.PRIVATE_WITH_NAT) {
            return 2; // High availability for staging
        }
        return 0; // No NAT gateways for public subnets in staging
    }

    @Override
    public boolean isWafEnabled() {
        // Check deployment context override FIRST
        if (deploymentContext != null && deploymentContext.wafEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.wafEnabled());
            LOG.info("STAGING profile: WAF explicitly configured: " + enabled);
            return enabled;
        }

        // Check if compliance matrix requires WAF protection
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.WAF_PROTECTION
            )) {
                LOG.info("STAGING profile: WAF enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        return true; // Enabled by default for staging testing
    }

    @Override
    public boolean isHttpsStrictEnabled() {
        // Check deployment context override FIRST
        if (deploymentContext != null && deploymentContext.httpsStrictEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.httpsStrictEnabled());
            LOG.info("STAGING profile: HTTPS strict mode explicitly configured: " + enabled);
            return enabled;
        }

        // Check if compliance matrix requires HTTPS strict mode
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.HTTPS_STRICT
            )) {
                LOG.info("STAGING profile: HTTPS strict mode enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: disabled (allow HTTP→HTTPS redirect for better UX)
        return false;
    }

    // Backup and Recovery - Moderate for staging
    @Override
    public boolean isAutomatedBackupEnabled() {
        return true; // Automated backups for staging
    }

    @Override
    public int getBackupRetentionDays() {
        return 30; // Moderate retention for staging
    }

    @Override
    public boolean isCrossRegionBackupEnabled() {
        // Default: optional for staging
        return controls.resolve("Cross-Region Backup", ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
            DeploymentContext::crossRegionBackupEnabled, false);
    }

    @Override
    public boolean isBackupVaultLockEnabled() {
        // Default: Vault lock typically not enabled in staging to allow easy cleanup
        return controls.resolve("Backup Vault Lock", ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
            DeploymentContext::backupVaultLockEnabled, false);
    }

    @Override
    public boolean isBackupVaultRetentionEnabled() {
        // Default: Staging environments typically don't retain backup vaults
        // to allow easy cleanup and recreation
        return controls.resolve("Backup Vault Retention", ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
            DeploymentContext::backupVaultRetentionEnabled, false);
    }

    // Compliance and Audit - Moderate for staging

    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.THREE_MONTHS; // Minimum retention for SOC2/GDPR compliance
    }

    // Performance and Reliability - Moderate for staging

    @Override
    public int getMinInstanceCount() {
        return 1; // Minimum for staging
    }

    @Override
    public int getMaxInstanceCount() {
        return 5; // Moderate scaling for staging
    }

    // AWS Config Remediation Settings - Staging mirrors production for compliance testing

    @Override
    public boolean isRdsDeletionProtectionEnabled() {
        // Default: Staging environments typically don't require deletion protection
        // to allow easy cleanup and recreation
        return controls.resolve("RDS Deletion Protection", ComplianceMatrix.SecurityControl.DELETION_PROTECTION,
            DeploymentContext::rdsDeletionProtectionEnabled, false);
    }

    @Override
    public boolean isRdsDatabaseMultiAzEnabled() {
        // Default: Staging environments typically use single-AZ for cost savings
        // unless compliance testing requires multi-AZ
        return controls.resolve("RDS Multi-AZ", ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ,
            DeploymentContext::rdsDatabaseMultiAzEnabled, false);
    }

    @Override
    public boolean isDatabaseProvisioningChoiceRequired() {
        // Staging is allowed to default to an OPTIONAL app's embedded fallback, same as DEV
        return false;
    }

    @Override
    public boolean isSecurityHubRemediationEnabled() {
        // Disabled by default - enable explicitly to test auto-remediation
        return false;
    }

    @Override
    public boolean isInspectorRemediationEnabled() {
        // Disabled by default - enable explicitly to test auto-remediation
        return false;
    }

    @Override
    public boolean isMacieRemediationEnabled() {
        // Disabled by default - enable explicitly to test behavior before production
        return false;
    }

    @Override
    public boolean isEcrImageScanningRemediationEnabled() {
        // Disabled by default - enable explicitly to test auto-remediation
        return false;
    }

    // ==================== Authentication Configuration ====================

    @Override
    public int getAccessTokenValidityHours() {
        // Moderate token lifetime - balance security and testing convenience
        return 2;
    }

    @Override
    public int getIdTokenValidityHours() {
        // Match access token
        return 2;
    }

    @Override
    public int getRefreshTokenValidityDays() {
        // Weekly re-authentication
        return 7;
    }

    @Override
    public int getMinimumPasswordLength() {
        // Production-like requirements
        return 12;
    }

    @Override
    public int getTempPasswordValidityDays() {
        // Production-like urgency
        return 3;
    }

    // ==================== Advanced Monitoring & Threat Detection ====================

    @Override
    public boolean isMacieAutomatedDiscoveryEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.macieAutomatedDiscoveryEnabled() != null) {
            boolean enabled = deploymentContext.macieAutomatedDiscoveryEnabled();
            LOG.severe("STAGING profile: Overriding Macie automated discovery from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (manual discovery preferred for testing)
        return false;
    }

    @Override
    public boolean isSecurityHubEnabled() {
        // STAGING default: true (test security monitoring)
        return controls.resolve("Security Hub", ComplianceMatrix.SecurityControl.SECURITY_HUB,
            DeploymentContext::securityHubEnabled, true);
    }

    @Override
    public boolean isInspectorEnabled() {
        // STAGING default: true (test vulnerability scanning)
        return controls.resolve("Inspector", ComplianceMatrix.SecurityControl.VULNERABILITY_SCANNING,
            DeploymentContext::inspectorEnabled, true);
    }

    @Override
    public boolean isAntiMalwareEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.antiMalwareEnabled() != null) {
            boolean enabled = deploymentContext.antiMalwareEnabled();
            LOG.severe("STAGING profile: Overriding anti-malware from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing)
        return false;
    }

    @Override
    public boolean isFileIntegrityMonitoringEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.fileIntegrityMonitoringEnabled() != null) {
            boolean enabled = deploymentContext.fileIntegrityMonitoringEnabled();
            LOG.severe("STAGING profile: Overriding file integrity monitoring from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing)
        return false;
    }

    @Override
    public boolean isContainerRuntimeSecurityEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.containerRuntimeSecurityEnabled() != null) {
            boolean enabled = deploymentContext.containerRuntimeSecurityEnabled();
            LOG.severe("STAGING profile: Overriding container runtime security from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing)
        return false;
    }

    @Override
    public boolean isContainerImageScanningEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.containerImageScanningEnabled() != null) {
            boolean enabled = deploymentContext.containerImageScanningEnabled();
            LOG.severe("STAGING profile: Overriding container image scanning from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: true (test image scanning pipeline)
        return true;
    }

    // ==================== Enhanced Compliance Controls ====================

    @Override
    public boolean isCloudWatchLogsKmsEncryptionEnabled() {
        // STAGING default: false (optional for testing)
        return controls.resolve("CloudWatch Logs KMS encryption", ComplianceMatrix.SecurityControl.CLOUDWATCH_LOGS_KMS_ENCRYPTION,
            DeploymentContext::cloudWatchLogsKmsEncryptionEnabled, false);
    }

    @Override
    public boolean isAlbDeletionProtectionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.DELETION_PROTECTION
            )) {
                LOG.info("STAGING profile: ALB deletion protection enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }
        // STAGING default: off unless a compliance framework requires it
        return false;
    }

    @Override
    public boolean isSnsKmsEncryptionEnabled() {
        // STAGING: false - Optional for testing
        return controls.resolve("SNS KMS encryption", ComplianceMatrix.SecurityControl.SNS_KMS_ENCRYPTION,
            DeploymentContext::snsKmsEncryptionEnabled, false);
    }

    @Override
    public boolean isImdsv2Required() {
        // Check deployment context override
        if (deploymentContext != null && deploymentContext.imdsv2Required() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.imdsv2Required());
            LOG.info("STAGING profile: Overriding IMDSv2 from deployment context: " + enabled);
            return enabled;
        }
        // STAGING: true - Test production security behavior
        return true;
    }
}
