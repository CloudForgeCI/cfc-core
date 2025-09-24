package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.RuntimeType;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.RetentionDays;

/**
 * Staging security profile configuration with moderate security measures.
 * Balances security with operational flexibility for testing environments.
 */
public class StagingSecurityProfileConfiguration implements SecurityProfileConfiguration {
    
    @Override
    public SecurityProfile getSecurityProfile() {
        return SecurityProfile.STAGING;
    }
    
    // Logging Configuration - Moderate retention
    @Override
    public RetentionDays getLogRetentionDays() {
        return RetentionDays.ONE_MONTH; // Moderate retention for staging
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
        return false; // Optional for staging
    }
    
    @Override
    public boolean isConfigEnabled() {
        return true; // Enabled for staging compliance
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
        return true; // Enabled for staging testing
    }
    
    @Override
    public boolean isCloudFrontEnabled() {
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
}
