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
public class StagingSecurityProfileConfiguration implements SecurityProfileConfiguration {

    private static final Logger LOG = Logger.getLogger(StagingSecurityProfileConfiguration.class.getName());
    private final DeploymentContext deploymentContext;

    /**
     * Create StagingSecurityProfileConfiguration.
     * @param deploymentContext Optional deployment context for overriding defaults
     */
    public StagingSecurityProfileConfiguration(DeploymentContext deploymentContext) {
        this.deploymentContext = deploymentContext;
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
    public DeploymentContext getDeploymentContext() {
        return deploymentContext;
    }

    @Override
    public SecurityProfile getSecurityProfile() {
        return SecurityProfile.STAGING;
    }

    /**
     * Get the effective compliance mode, defaulting intelligently based on whether frameworks are specified.
     * If frameworks are specified, defaults to ENFORCE. If no frameworks, defaults to DISABLED.
     */
    private ComplianceMode getEffectiveComplianceMode() {
        if (deploymentContext == null) {
            return ComplianceMode.DISABLED;
        }

        // Use the compliance mode from deployment context if set
        ComplianceMode contextMode = deploymentContext.complianceMode();
        if (contextMode != null) {
            return contextMode;
        }

        // Default based on whether compliance frameworks are enabled
        String frameworks = deploymentContext.complianceFrameworks();
        return (frameworks != null && !frameworks.isEmpty())
            ? ComplianceMode.ENFORCE
            : ComplianceMode.DISABLED;
    }

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

    @Override
    public RemovalPolicy getLogRemovalPolicy() {
        return RemovalPolicy.RETAIN; // Retain logs for staging analysis
    }

    // Flow Log Configuration - Enhanced monitoring
    @Override
    public boolean isFlowLogsEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.NETWORK_FLOW_LOGS
            )) {
                LOG.severe("STAGING profile: Flow Logs enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.enableFlowlogs() != null) {
            return Boolean.TRUE.equals(deploymentContext.enableFlowlogs());
        }

        // Default: enabled for staging monitoring
        return true;
    }

    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ALL; // Record accepted and rejected traffic
    }

    // Security Monitoring - Moderate for staging
    @Override
    public boolean isSecurityMonitoringEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SECURITY_MONITORING
            )) {
                LOG.severe("STAGING profile: Security Monitoring enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor)
        if (deploymentContext != null && deploymentContext.securityMonitoringEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.securityMonitoringEnabled());
        }

        // Default: enabled for staging
        return true;
    }

    @Override
    public boolean isCloudTrailEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.AUDIT_LOGGING
            )) {
                LOG.severe("STAGING profile: CloudTrail enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.cloudTrailEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.cloudTrailEnabled());
        }

        // Default: enabled for staging audit
        return true;
    }

    @Override
    public boolean isGuardDutyEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.THREAT_DETECTION
            )) {
                LOG.severe("STAGING profile: GuardDuty enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.guardDutyEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.guardDutyEnabled());
            LOG.severe("STAGING profile: Overriding GuardDuty from deployment context: " + enabled);
            return enabled;
        }

        // Default: optional for staging (default for non-compliant deployments)
        return false;
    }

    @Override
    public boolean isAwsConfigEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.VULNERABILITY_MANAGEMENT
            )) {
                LOG.severe("STAGING profile: AWS Config enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.awsConfigEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.awsConfigEnabled());
        }

        return false;
    }

    @Override
    public boolean isAuditManagerEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.AUDIT_MANAGER
            )) {
                LOG.severe("STAGING profile: Audit Manager enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.auditManagerEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.auditManagerEnabled());
        }

        return false;
    }

    // Encryption Configuration - Full encryption
    @Override
    public boolean isEbsEncryptionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            )) {
                LOG.severe("STAGING profile: EBS Encryption enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: encryption enabled
        return true;
    }

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
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

    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            )) {
                LOG.severe("STAGING profile: EFS Encryption at Rest enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: encryption enabled
        return true;
    }

    @Override
    public boolean isS3EncryptionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            )) {
                LOG.severe("STAGING profile: S3 Encryption enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: encryption enabled
        return true;
    }

    // Network Security - Moderate restrictions
    @Override
    public boolean isVpcEndpointsEnabled() {
        return true; // Enabled for staging security
    }

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
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
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
    public boolean isNatGatewayEnabled() {
        return true; // Use private subnets for staging
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
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
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
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
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

    @Override
    public boolean isCloudFrontEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null && deploymentContext.cloudfrontEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.cloudfrontEnabled());
        }
        return false; // Optional for staging
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
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                LOG.severe("STAGING profile: Cross-Region Backup enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor)
        if (deploymentContext != null && deploymentContext.crossRegionBackupEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.crossRegionBackupEnabled());
        }

        // Default: optional for staging
        return false;
    }

    @Override
    public boolean isBackupVaultLockEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                LOG.severe("STAGING profile: Backup Vault Lock enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: Vault lock typically not enabled in staging to allow easy cleanup
        return false;
    }

    @Override
    public boolean isBackupVaultRetentionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                LOG.severe("STAGING profile: Backup Vault Retention enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: Staging environments typically don't retain backup vaults
        // to allow easy cleanup and recreation
        return false;
    }

    // Compliance and Audit - Moderate for staging
    @Override
    public boolean isDetailedBillingEnabled() {
        return true; // Enabled for staging cost analysis
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.AUDIT_LOGGING
            )) {
                LOG.severe("STAGING profile: ALB Access Logging enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.albAccessLogging() != null) {
            return Boolean.TRUE.equals(deploymentContext.albAccessLogging());
        }

        // Default: enabled for staging analysis
        return true;
    }

    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.THREE_MONTHS; // Minimum retention for SOC2/GDPR compliance
    }

    // Performance and Reliability - Moderate for staging
    @Override
    public boolean isMultiAzEnforced() {
        return true; // Multi-AZ for staging reliability
    }

    @Override
    public boolean isAutoScalingEnabled() {
        return true; // Auto-scaling for staging
    }

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
    public boolean isS3VersioningRemediationEnabled() {
        // Disabled by default due to cost implications
        // Enable in staging to test versioning behavior before production
        return false;
    }

    @Override
    public boolean isCloudTrailBucketAccessRemediationEnabled() {
        // Disabled by default to prevent automatic policy changes
        // Enable in staging to validate CloudTrail bucket policies
        return false;
    }

    @Override
    public boolean isEbsEncryptionRemediationEnabled() {
        // Enabled by default - mirrors production for compliance testing
        // Staging should validate EBS encryption remediation
        return true;
    }

    @Override
    public boolean isGuardDutyRemediationEnabled() {
        // Enabled by default - mirrors production for compliance testing
        // Staging should validate GuardDuty remediation behavior
        return true;
    }

    @Override
    public boolean isVpcDefaultSgRemediationEnabled() {
        // Enabled by default - mirrors production for compliance testing
        // Staging should validate default security group remediation
        return true;
    }

    @Override
    public boolean isElbDeletionProtectionRemediationEnabled() {
        // Enabled by default - mirrors production for compliance testing
        // Staging should validate load balancer deletion protection
        return true;
    }

    @Override
    public boolean isKmsKeyRotationRemediationEnabled() {
        // Enabled by default - mirrors production for compliance testing
        // Staging should validate KMS key rotation remediation
        return true;
    }

    @Override
    public boolean isSshRemovalRemediationEnabled() {
        // Disabled by default - could break SSH access for testing
        // Enable in staging to validate SSH removal behavior before production
        return false;
    }

    @Override
    public boolean isAccessKeyRotationRemediationEnabled() {
        // Disabled by default - requires user notification workflow
        // Enable in staging to test access key rotation procedures
        return false;
    }

    @Override
    public boolean isDynamoDbPitrRemediationEnabled() {
        // Enabled by default - mirrors production for compliance testing
        // Staging should validate DynamoDB PITR remediation
        return true;
    }

    @Override
    public boolean isRdsMultiAzRemediationEnabled() {
        // Disabled by default - requires maintenance window and causes downtime
        // Enable in staging to test Multi-AZ conversion before production
        return false;
    }

    @Override
    public boolean isRdsEncryptionRemediationEnabled() {
        // Disabled by default - complex operation requiring snapshot recreation
        // Enable in staging to test RDS encryption process before production
        return false;
    }

    @Override
    public boolean isRdsDeletionProtectionRemediationEnabled() {
        // Disabled by default - deletion protection is set during RDS creation
        return false;
    }

    @Override
    public boolean isRdsDeletionProtectionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.DELETION_PROTECTION
            )) {
                LOG.severe("STAGING profile: RDS Deletion Protection enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: Staging environments typically don't require deletion protection
        // to allow easy cleanup and recreation
        return false;
    }

    @Override
    public boolean isRdsDatabaseMultiAzEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ
            )) {
                LOG.severe("STAGING profile: RDS Multi-AZ enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: Staging environments typically use single-AZ for cost savings
        // unless compliance testing requires multi-AZ
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
    public boolean isMfaRequired() {
        // MFA required in staging to test production-like security
        return true;
    }

    @Override
    public String getDefaultMfaMethod() {
        // Test both MFA methods in staging
        return "both";
    }

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

    @Override
    public boolean isSelfSignupEnabled() {
        // Admin-controlled access like production
        return false;
    }

    @Override
    public boolean isPreventUserExistenceErrorsEnabled() {
        // Test production security behavior
        return true;
    }

    @Override
    public boolean isAdvancedSecurityEnabled() {
        // Enable for CDK-nag COG3 compliance (requires Cognito Plus tier)
        // Adaptive authentication detects suspicious login patterns
        return true;
    }

    // ==================== Advanced Monitoring & Threat Detection ====================

    @Override
    public boolean isMacieEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SENSITIVE_DATA_DISCOVERY
            )) {
                LOG.severe("STAGING profile: Macie enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.macieEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.macieEnabled());
            LOG.severe("STAGING profile: Overriding Macie from deployment context: " + enabled);
            return enabled;
        }

        // STAGING default: false (opt-in for testing)
        return false;
    }

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
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SECURITY_HUB
            )) {
                LOG.severe("STAGING profile: Security Hub enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.securityHubEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.securityHubEnabled());
            LOG.severe("STAGING profile: Overriding Security Hub from deployment context: " + enabled);
            return enabled;
        }

        // STAGING default: true (test security monitoring)
        return true;
    }

    @Override
    public boolean isInspectorEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.VULNERABILITY_SCANNING
            )) {
                LOG.severe("STAGING profile: Inspector enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.inspectorEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.inspectorEnabled());
            LOG.severe("STAGING profile: Overriding Inspector from deployment context: " + enabled);
            return enabled;
        }

        // STAGING default: true (test vulnerability scanning)
        return true;
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
        // Check deployment context override
        if (deploymentContext != null && deploymentContext.cloudWatchLogsKmsEncryptionEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.cloudWatchLogsKmsEncryptionEnabled());
            LOG.info("STAGING profile: Overriding CloudWatch Logs KMS encryption from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing)
        return false;
    }

    @Override
    public boolean isCloudTrailInsightsEnabled() {
        // Check deployment context override
        if (deploymentContext != null && deploymentContext.cloudTrailInsightsEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.cloudTrailInsightsEnabled());
            LOG.info("STAGING profile: Overriding CloudTrail Insights from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing)
        return false;
    }

    @Override
    public boolean isRoute53QueryLoggingEnabled() {
        // Check deployment context override
        if (deploymentContext != null && deploymentContext.route53QueryLoggingEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.route53QueryLoggingEnabled());
            LOG.info("STAGING profile: Overriding Route53 Query Logging from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing)
        return false;
    }

    @Override
    public boolean isS3ObjectLockEnabled() {
        // Check deployment context override
        if (deploymentContext != null && deploymentContext.s3ObjectLockEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.s3ObjectLockEnabled());
            LOG.info("STAGING profile: Overriding S3 Object Lock from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: false (optional for testing - enable for HIPAA/PCI-DSS)
        return false;
    }

    @Override
    public boolean isSnsKmsEncryptionEnabled() {
        // STAGING: false - Optional for testing
        return false;
    }

    @Override
    public boolean isImdsv2Required() {
        // STAGING: true - Test production security behavior
        return true;
    }
}
