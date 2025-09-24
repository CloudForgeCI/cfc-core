package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.RuntimeType;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.RetentionDays;

/**
 * Development security profile configuration with minimal security constraints.
 * Optimized for development productivity with basic security measures.
 */
public class DevSecurityProfileConfiguration implements SecurityProfileConfiguration {
    
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
    public boolean isConfigEnabled() {
        return false; // Disabled for dev
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
        return false; // Not required for dev
    }
    
    @Override
    public boolean isCloudFrontEnabled() {
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
}
