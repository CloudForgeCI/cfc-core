package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.RetentionDays;

/**
 * Development security profile configuration with minimal security constraints.
 * Optimized for development productivity with basic security measures.
 */
public class DevSecurityProfileConfiguration implements SecurityProfileConfiguration {

    private final DeploymentContext deploymentContext;

    /**
     * Create DevSecurityProfileConfiguration.
     * @param deploymentContext Optional deployment context for overriding defaults
     */
    public DevSecurityProfileConfiguration(DeploymentContext deploymentContext) {
        this.deploymentContext = deploymentContext;
    }

    /**
     * Create DevSecurityProfileConfiguration with no deployment context.
     * Uses only profile defaults.
     */
    public DevSecurityProfileConfiguration() {
        this(null);
    }

    @Override
    public SecurityProfile getSecurityProfile() {
        return SecurityProfile.DEV;
    }

    // Logging Configuration - Minimal retention for cost optimization
    @Override
    public RetentionDays getLogRetentionDays() {
        return RetentionDays.ONE_WEEK; // Short retention for dev
    }

    @Override
    public RetentionDays getFlowLogRetentionDays() {
        return RetentionDays.ONE_WEEK; // Short retention for dev
    }

    @Override
    public RemovalPolicy getLogRemovalPolicy() {
        return RemovalPolicy.DESTROY; // Allow cleanup in dev
    }

    // Flow Log Configuration - Basic monitoring
    @Override
    public boolean isFlowLogsEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.enableFlowlogs() != null) {
            return Boolean.TRUE.equals(deploymentContext.enableFlowlogs());
        }
        return false; // Disabled by default in dev for cost savings
    }

    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ACCEPT; // Only accepted traffic
    }

    // Security Monitoring - Minimal for dev
    @Override
    public boolean isSecurityMonitoringEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.securityMonitoringEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.securityMonitoringEnabled());
        }
        return false; // Disabled for dev
    }

    @Override
    public boolean isCloudTrailEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.cloudTrailEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.cloudTrailEnabled());
        }
        return false; // Disabled for dev
    }

    @Override
    public boolean isGuardDutyEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.guardDutyEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.guardDutyEnabled());
        }
        return false; // Disabled for dev
    }

    @Override
    public boolean isAwsConfigEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.awsConfigEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.awsConfigEnabled());
        }
        return false; // Disabled for dev
    }

    @Override
    public boolean isAuditManagerEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.auditManagerEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.auditManagerEnabled());
        }
        return false; // Disabled for dev to reduce costs
    }

    // Encryption Configuration - Basic encryption
    @Override
    public boolean isEbsEncryptionEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.enableEncryption() != null) {
            return Boolean.TRUE.equals(deploymentContext.enableEncryption());
        }
        return true; // Basic encryption enabled
    }

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.efsEncryptionInTransitEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.efsEncryptionInTransitEnabled());
        }
        return true; // Basic encryption enabled
    }

    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.enableEncryption() != null) {
            return Boolean.TRUE.equals(deploymentContext.enableEncryption());
        }
        return true; // Basic encryption enabled
    }

    @Override
    public boolean isS3EncryptionEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.enableEncryption() != null) {
            return Boolean.TRUE.equals(deploymentContext.enableEncryption());
        }
        return true; // Basic encryption enabled
    }

    // Network Security - Relaxed for dev
    @Override
    public boolean isVpcEndpointsEnabled() {
        return false; // Not required for dev
    }

    @Override
    public boolean isNatGatewayEnabled() {
        return false; // Use public subnets for dev
    }

    @Override
    public int getNatGatewayCount(TopologyType topology, RuntimeType runtime, NetworkMode networkMode) {
        // DEV profile respects network mode for cost optimization
        if (networkMode == NetworkMode.PRIVATE_WITH_NAT) {
            return 1; // Single NAT gateway for cost optimization in dev
        }
        return 0; // No NAT gateways for public subnets in dev
    }

    @Override
    public boolean isWafEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null && deploymentContext.wafEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.wafEnabled());
        }
        return false; // Not required for dev
    }

    @Override
    public boolean isCloudFrontEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null && deploymentContext.cloudfrontEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.cloudfrontEnabled());
        }
        return false; // Not required for dev
    }

    // Backup and Recovery - Minimal for dev
    @Override
    public boolean isAutomatedBackupEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.automatedBackupEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.automatedBackupEnabled());
        }
        return false; // Manual backups for dev
    }

    @Override
    public int getBackupRetentionDays() {
        return 7; // Short retention for dev
    }

    @Override
    public boolean isCrossRegionBackupEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.crossRegionBackupEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.crossRegionBackupEnabled());
        }
        return false; // Not required for dev
    }

    // Compliance and Audit - Minimal for dev
    @Override
    public boolean isDetailedBillingEnabled() {
        return false; // Not required for dev
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.albAccessLogging() != null) {
            return Boolean.TRUE.equals(deploymentContext.albAccessLogging());
        }
        return false; // Not required for dev
    }

    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.ONE_WEEK; // Short retention for dev
    }

    // Performance and Reliability - Basic for dev
    @Override
    public boolean isMultiAzEnforced() {
        return false; // Single AZ for dev cost savings
    }

    @Override
    public boolean isAutoScalingEnabled() {
        return false; // Manual scaling for dev
    }

    @Override
    public int getMinInstanceCount() {
        return 1; // Single instance for dev
    }

    @Override
    public int getMaxInstanceCount() {
        return 2; // Limited scaling for dev
    }

    // AWS Config Remediation Settings - All disabled for dev flexibility
    @Override
    public boolean isS3VersioningRemediationEnabled() {
        // Disabled for dev - developers need flexibility to manage buckets
        return false;
    }

    @Override
    public boolean isCloudTrailBucketAccessRemediationEnabled() {
        // Disabled for dev - no CloudTrail in dev by default
        return false;
    }

    @Override
    public boolean isEbsEncryptionRemediationEnabled() {
        // Disabled for dev - allow flexibility for testing
        return false;
    }

    @Override
    public boolean isGuardDutyRemediationEnabled() {
        // Disabled for dev - no GuardDuty in dev by default
        return false;
    }

    @Override
    public boolean isVpcDefaultSgRemediationEnabled() {
        // Disabled for dev - developers may use default SG for testing
        return false;
    }

    @Override
    public boolean isElbDeletionProtectionRemediationEnabled() {
        // Disabled for dev - allow quick iteration and deletion
        return false;
    }

    @Override
    public boolean isKmsKeyRotationRemediationEnabled() {
        // Disabled for dev - not needed in development environment
        return false;
    }

    @Override
    public boolean isSshRemovalRemediationEnabled() {
        // Disabled for dev - developers may need SSH access for debugging
        return false;
    }

    @Override
    public boolean isAccessKeyRotationRemediationEnabled() {
        // Disabled for dev - developers manage their own access keys
        return false;
    }

    @Override
    public boolean isDynamoDbPitrRemediationEnabled() {
        // Disabled for dev - point-in-time recovery not needed for dev data
        return false;
    }

    @Override
    public boolean isRdsMultiAzRemediationEnabled() {
        // Disabled for dev - single AZ is sufficient for development
        return false;
    }

    @Override
    public boolean isRdsEncryptionRemediationEnabled() {
        // Disabled for dev - encryption not required for development data
        return false;
    }

    @Override
    public boolean isSecurityHubRemediationEnabled() {
        // Disabled for dev - not needed in development
        return false;
    }

    @Override
    public boolean isInspectorRemediationEnabled() {
        // Disabled for dev - not needed in development
        return false;
    }

    @Override
    public boolean isMacieRemediationEnabled() {
        // Disabled for dev - not needed in development
        return false;
    }

    @Override
    public boolean isEcrImageScanningRemediationEnabled() {
        // Disabled for dev - not needed in development
        return false;
    }

    // ==================== Authentication Configuration ====================

    @Override
    public boolean isMfaRequired() {
        // MFA optional for dev - convenience over security
        return false;
    }

    @Override
    public String getDefaultMfaMethod() {
        // TOTP only for dev - simpler than SMS
        return "totp";
    }

    @Override
    public int getAccessTokenValidityHours() {
        // Long token lifetime for dev convenience
        return 8;
    }

    @Override
    public int getIdTokenValidityHours() {
        // Match access token for simplicity
        return 8;
    }

    @Override
    public int getRefreshTokenValidityDays() {
        // Long-lived refresh tokens for dev
        return 30;
    }

    @Override
    public int getMinimumPasswordLength() {
        // Minimum acceptable for testing
        return 8;
    }

    @Override
    public int getTempPasswordValidityDays() {
        // Flexible for testing
        return 7;
    }

    @Override
    public boolean isSelfSignupEnabled() {
        // Allow easy account creation for testing
        return true;
    }

    @Override
    public boolean isPreventUserExistenceErrorsEnabled() {
        // Helpful error messages for debugging
        return false;
    }

    @Override
    public boolean isAdvancedSecurityEnabled() {
        // Not needed for development
        return false;
    }

    // ==================== Advanced Monitoring & Threat Detection ====================

    @Override
    public boolean isMacieEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.macieEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.macieEnabled());
        }
        return false; // Not required for development
    }

    @Override
    public boolean isMacieAutomatedDiscoveryEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.macieAutomatedDiscoveryEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.macieAutomatedDiscoveryEnabled());
        }
        return false; // Not applicable
    }

    @Override
    public boolean isSecurityHubEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.securityHubEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.securityHubEnabled());
        }
        return false; // Not needed for development
    }

    @Override
    public boolean isInspectorEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.inspectorEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.inspectorEnabled());
        }
        return false; // Not needed for development
    }

    @Override
    public boolean isAntiMalwareEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.antiMalwareEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.antiMalwareEnabled());
        }
        return false; // Not required for development
    }

    @Override
    public boolean isFileIntegrityMonitoringEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.fileIntegrityMonitoringEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.fileIntegrityMonitoringEnabled());
        }
        return false; // Not required for development
    }

    @Override
    public boolean isContainerRuntimeSecurityEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.containerRuntimeSecurityEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.containerRuntimeSecurityEnabled());
        }
        return false; // Not required for development
    }

    @Override
    public boolean isContainerImageScanningEnabled() {
        // Allow deployment context to override profile default
        if (deploymentContext != null && deploymentContext.containerImageScanningEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.containerImageScanningEnabled());
        }
        return false; // Not required for development
    }
}
