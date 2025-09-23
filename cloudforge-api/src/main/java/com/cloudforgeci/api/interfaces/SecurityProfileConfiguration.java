package com.cloudforgeci.api.interfaces;

import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.RemovalPolicy;

/**
 * Configuration interface for security profile settings.
 * Defines security best practices and compliance requirements for each environment.
 */
public interface SecurityProfileConfiguration {
    
    /**
     * Get the security profile this configuration applies to.
     */
    SecurityProfile getSecurityProfile();
    
    // Logging Configuration
    /**
     * Get the CloudWatch log retention period for application logs.
     */
    RetentionDays getLogRetentionDays();
    
    /**
     * Get the CloudWatch log retention period for VPC flow logs.
     */
    RetentionDays getFlowLogRetentionDays();
    
    /**
     * Get the removal policy for log groups.
     */
    RemovalPolicy getLogRemovalPolicy();
    
    // Flow Log Configuration
    /**
     * Whether flow logs should be enabled for this security profile.
     */
    boolean isFlowLogsEnabled();
    
    /**
     * Get the flow log traffic type to capture.
     */
    FlowLogTrafficType getFlowLogTrafficType();
    
    // Security Monitoring
    /**
     * Whether security monitoring and alerting should be enabled.
     */
    boolean isSecurityMonitoringEnabled();
    
    /**
     * Whether CloudTrail should be enabled for audit logging.
     */
    boolean isCloudTrailEnabled();
    
    /**
     * Whether GuardDuty should be enabled for threat detection.
     */
    boolean isGuardDutyEnabled();
    
    /**
     * Whether Config should be enabled for compliance monitoring.
     */
    boolean isConfigEnabled();
    
    // Encryption Configuration
    /**
     * Whether EBS volumes should be encrypted.
     */
    boolean isEbsEncryptionEnabled();
    
    /**
     * Whether EFS should be encrypted in transit.
     */
    boolean isEfsEncryptionInTransitEnabled();
    
    /**
     * Whether EFS should be encrypted at rest.
     */
    boolean isEfsEncryptionAtRestEnabled();
    
    /**
     * Whether S3 buckets should be encrypted.
     */
    boolean isS3EncryptionEnabled();
    
    // Network Security
    /**
     * Whether VPC endpoints should be used for AWS services.
     */
    boolean isVpcEndpointsEnabled();
    
    /**
     * Whether NAT Gateway should be used for outbound internet access.
     */
    boolean isNatGatewayEnabled();
    
    /**
     * Whether WAF should be enabled for web application protection.
     */
    boolean isWafEnabled();
    
    /**
     * Whether CloudFront should be enabled for DDoS protection.
     */
    boolean isCloudFrontEnabled();
    
    // Backup and Recovery
    /**
     * Whether automated backups should be enabled.
     */
    boolean isAutomatedBackupEnabled();
    
    /**
     * Get the backup retention period in days.
     */
    int getBackupRetentionDays();
    
    /**
     * Whether cross-region backup replication should be enabled.
     */
    boolean isCrossRegionBackupEnabled();
    
    // Compliance and Audit
    /**
     * Whether detailed billing should be enabled.
     */
    boolean isDetailedBillingEnabled();
    
    /**
     * Whether access logging should be enabled for ALB.
     */
    boolean isAlbAccessLoggingEnabled();
    
    /**
     * Get the ALB access log retention period in days.
     */
    RetentionDays getAlbAccessLogRetentionDays();
    
    // Performance and Reliability
    /**
     * Whether multi-AZ deployment should be enforced.
     */
    boolean isMultiAzEnforced();
    
    /**
     * Whether auto-scaling should be enabled.
     */
    boolean isAutoScalingEnabled();
    
    /**
     * Get the minimum number of instances for auto-scaling.
     */
    int getMinInstanceCount();
    
    /**
     * Get the maximum number of instances for auto-scaling.
     */
    int getMaxInstanceCount();
}
