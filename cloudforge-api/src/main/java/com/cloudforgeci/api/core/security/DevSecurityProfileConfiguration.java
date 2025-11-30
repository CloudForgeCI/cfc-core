package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.RuntimeType;
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
        if (deploymentContext != null && deploymentContext.enableFlowlogs()) {
            return true;
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
        return false; // Disabled for dev
    }

    @Override
    public boolean isCloudTrailEnabled() {
        return false; // Disabled for dev
    }

    @Override
    public boolean isGuardDutyEnabled() {
        return false; // Disabled for dev
    }

    @Override
    public boolean isAwsConfigEnabled() {
        return false; // Disabled for dev
    }

    @Override
    public boolean isAuditManagerEnabled() {
        return false; // Disabled for dev to reduce costs
    }

    // Encryption Configuration - Basic encryption
    @Override
    public boolean isEbsEncryptionEnabled() {
        return true; // Basic encryption enabled
    }

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        return true; // Basic encryption enabled
    }

    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        return true; // Basic encryption enabled
    }

    @Override
    public boolean isS3EncryptionEnabled() {
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
    public int getNatGatewayCount(TopologyType topology, RuntimeType runtime, String networkMode) {
        // DEV profile respects network mode for cost optimization
        if ("private-with-nat".equals(networkMode)) {
            return 1; // Single NAT gateway for cost optimization in dev
        }
        return 0; // No NAT gateways for public subnets in dev
    }

    @Override
    public boolean isWafEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null) {
            return deploymentContext.wafEnabled();
        }
        return false; // Not required for dev
    }

    @Override
    public boolean isCloudFrontEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null) {
            return deploymentContext.cloudfrontEnabled();
        }
        return false; // Not required for dev
    }

    // Backup and Recovery - Minimal for dev
    @Override
    public boolean isAutomatedBackupEnabled() {
        return false; // Manual backups for dev
    }

    @Override
    public int getBackupRetentionDays() {
        return 7; // Short retention for dev
    }

    @Override
    public boolean isCrossRegionBackupEnabled() {
        return false; // Not required for dev
    }

    // Compliance and Audit - Minimal for dev
    @Override
    public boolean isDetailedBillingEnabled() {
        return false; // Not required for dev
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
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
}
