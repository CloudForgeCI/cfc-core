package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.util.RetentionDaysConverter;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.RuntimeType;
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
        java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(getClass().getName());
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
        return RetentionDays.ONE_MONTH; // Moderate retention for staging (default for non-compliant deployments)
    }

    @Override
    public RetentionDays getFlowLogRetentionDays() {
        return RetentionDays.ONE_MONTH; // Moderate retention for staging
    }

    @Override
    public RemovalPolicy getLogRemovalPolicy() {
        return RemovalPolicy.RETAIN; // Retain logs for staging analysis
    }

    // Flow Log Configuration - Enhanced monitoring
    @Override
    public boolean isFlowLogsEnabled() {
        return true; // Enabled for staging monitoring
    }

    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ALL; // All traffic for comprehensive monitoring
    }

    // Security Monitoring - Moderate for staging
    @Override
    public boolean isSecurityMonitoringEnabled() {
        return true; // Enabled for staging
    }

    @Override
    public boolean isCloudTrailEnabled() {
        return true; // Enabled for staging audit
    }

    @Override
    public boolean isGuardDutyEnabled() {
        java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(getClass().getName());
        LOG.severe("DEBUG isGuardDutyEnabled: deploymentContext = " + (deploymentContext != null));
        if (deploymentContext != null) {
            LOG.severe("DEBUG guardDutyEnabled value = " + deploymentContext.guardDutyEnabled());
        }
        // Check deployment context first for compliance framework overrides
        if (deploymentContext != null && deploymentContext.guardDutyEnabled() != null) {
            boolean enabled = deploymentContext.guardDutyEnabled();
            LOG.severe("STAGING profile: Overriding GuardDuty from deployment context: " + enabled);
            return enabled;
        }
        LOG.severe("STAGING profile: Using default GuardDuty (false)");
        return false; // Optional for staging (default for non-compliant deployments)
    }

    @Override
    public boolean isAwsConfigEnabled() {
        return true; // Enabled for staging compliance
    }

    @Override
    public boolean isAuditManagerEnabled() {
        return true; // Enabled for staging to test compliance frameworks
    }

    // Encryption Configuration - Full encryption
    @Override
    public boolean isEbsEncryptionEnabled() {
        return true; // Encryption enabled
    }

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        return true; // Encryption enabled
    }

    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        return true; // Encryption enabled
    }

    @Override
    public boolean isS3EncryptionEnabled() {
        return true; // Encryption enabled
    }

    // Network Security - Moderate restrictions
    @Override
    public boolean isVpcEndpointsEnabled() {
        return true; // Enabled for staging security
    }

    @Override
    public boolean isNatGatewayEnabled() {
        return true; // Use private subnets for staging
    }

    @Override
    public int getNatGatewayCount(TopologyType topology, RuntimeType runtime, String networkMode) {
        // Staging respects network mode for cost optimization
        if ("private-with-nat".equals(networkMode)) {
            return 2; // High availability for staging
        }
        return 0; // No NAT gateways for public subnets in staging
    }

    @Override
    public boolean isWafEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null) {
            return deploymentContext.wafEnabled();
        }
        return true; // Enabled for staging testing
    }

    @Override
    public boolean isCloudFrontEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null) {
            return deploymentContext.cloudfrontEnabled();
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
        return false; // Optional for staging
    }

    // Compliance and Audit - Moderate for staging
    @Override
    public boolean isDetailedBillingEnabled() {
        return true; // Enabled for staging cost analysis
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
        return true; // Enabled for staging analysis
    }

    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.ONE_MONTH; // Moderate retention for staging
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
        // Optional for testing - can enable to test adaptive auth
        return false;
    }

    // ==================== Advanced Monitoring & Threat Detection ====================

    @Override
    public boolean isMacieEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.macieEnabled() != null) {
            boolean enabled = deploymentContext.macieEnabled();
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
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.securityHubEnabled() != null) {
            boolean enabled = deploymentContext.securityHubEnabled();
            LOG.severe("STAGING profile: Overriding Security Hub from deployment context: " + enabled);
            return enabled;
        }
        // STAGING default: true (test security monitoring)
        return true;
    }

    @Override
    public boolean isInspectorEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.inspectorEnabled() != null) {
            boolean enabled = deploymentContext.inspectorEnabled();
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
}
