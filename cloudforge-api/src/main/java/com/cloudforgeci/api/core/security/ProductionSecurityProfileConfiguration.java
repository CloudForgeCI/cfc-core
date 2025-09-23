package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.RetentionDays;

/**
 * Production security profile configuration with comprehensive security measures.
 * Implements enterprise-grade security for SOC/HIPAA/PCI-DSS compliance.
 */
public class ProductionSecurityProfileConfiguration implements SecurityProfileConfiguration {
    
    @Override
    public SecurityProfile getSecurityProfile() {
        return SecurityProfile.PRODUCTION;
    }
    
    // Logging Configuration - Extended retention for compliance
    @Override
    public RetentionDays getLogRetentionDays() {
        return RetentionDays.TWO_YEARS; // Extended retention for compliance
    }
    
    @Override
    public RetentionDays getFlowLogRetentionDays() {
        return RetentionDays.TWO_YEARS; // Extended retention for compliance
    }
    
    @Override
    public RemovalPolicy getLogRemovalPolicy() {
        return RemovalPolicy.RETAIN; // Always retain logs in production
    }
    
    // Flow Log Configuration - Comprehensive monitoring
    @Override
    public boolean isFlowLogsEnabled() {
        return true; // Always enabled for production
    }
    
    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ALL; // All traffic for comprehensive monitoring
    }
    
    // Security Monitoring - Comprehensive for production
    @Override
    public boolean isSecurityMonitoringEnabled() {
        return true; // Always enabled for production
    }
    
    @Override
    public boolean isCloudTrailEnabled() {
        return true; // Always enabled for production audit
    }
    
    @Override
    public boolean isGuardDutyEnabled() {
        return true; // Always enabled for production threat detection
    }
    
    @Override
    public boolean isConfigEnabled() {
        return true; // Always enabled for production compliance
    }
    
    // Encryption Configuration - Full encryption mandatory
    @Override
    public boolean isEbsEncryptionEnabled() {
        return true; // Mandatory encryption
    }
    
    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        return true; // Mandatory encryption
    }
    
    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        return true; // Mandatory encryption
    }
    
    @Override
    public boolean isS3EncryptionEnabled() {
        return true; // Mandatory encryption
    }
    
    // Network Security - Maximum restrictions
    @Override
    public boolean isVpcEndpointsEnabled() {
        return true; // Always enabled for production security
    }
    
    @Override
    public boolean isNatGatewayEnabled() {
        return true; // Always use private subnets for production
    }
    
    @Override
    public boolean isWafEnabled() {
        return true; // Always enabled for production protection
    }
    
    @Override
    public boolean isCloudFrontEnabled() {
        return true; // Always enabled for production DDoS protection
    }
    
    // Backup and Recovery - Comprehensive for production
    @Override
    public boolean isAutomatedBackupEnabled() {
        return true; // Always enabled for production
    }
    
    @Override
    public int getBackupRetentionDays() {
        return 90; // Extended retention for production
    }
    
    @Override
    public boolean isCrossRegionBackupEnabled() {
        return true; // Always enabled for production disaster recovery
    }
    
    // Compliance and Audit - Comprehensive for production
    @Override
    public boolean isDetailedBillingEnabled() {
        return true; // Always enabled for production cost management
    }
    
    @Override
    public boolean isAlbAccessLoggingEnabled() {
        return true; // Always enabled for production audit
    }
    
    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.TWO_YEARS; // Extended retention for compliance
    }
    
    // Performance and Reliability - Maximum for production
    @Override
    public boolean isMultiAzEnforced() {
        return true; // Always enforced for production
    }
    
    @Override
    public boolean isAutoScalingEnabled() {
        return true; // Always enabled for production
    }
    
    @Override
    public int getMinInstanceCount() {
        return 2; // Minimum for production high availability
    }
    
    @Override
    public int getMaxInstanceCount() {
        return 20; // Extended scaling for production
    }
}
