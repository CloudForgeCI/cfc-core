package com.cloudforgeci.api.interfaces;

import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.RemovalPolicy;

// Note: TopologyType and RuntimeType are already imported via the interface package

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
     * Whether AWS Config should be enabled for compliance monitoring.
     */
    boolean isAwsConfigEnabled();

    /**
     * Whether AWS Audit Manager should be enabled for continuous auditing.
     */
    boolean isAuditManagerEnabled();

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
     * Get the number of NAT gateways to create based on topology, runtime, and security profile.
     * This method encapsulates all NAT gateway logic including network mode, security requirements,
     * and topology-specific needs.
     *
     * @param topology The deployment topology (JENKINS_SINGLE_NODE, JENKINS_SERVICE, etc.)
     * @param runtime The runtime type (EC2, FARGATE)
     * @param networkMode The network mode (public-no-nat, private-with-nat)
     * @return The number of NAT gateways to create (0, 1, or 2)
     */
    int getNatGatewayCount(TopologyType topology, RuntimeType runtime, String networkMode);

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

    // AWS Config Remediation Settings
    /**
     * Whether S3 bucket versioning remediation should be enabled.
     * Automatically enables versioning on non-compliant S3 buckets.
     * WARNING: Has cost implications - versioned objects consume additional storage.
     */
    boolean isS3VersioningRemediationEnabled();

    /**
     * Whether CloudTrail bucket access remediation should be enabled.
     * Automatically fixes CloudTrail S3 bucket policy when CloudTrail can't write logs.
     */
    boolean isCloudTrailBucketAccessRemediationEnabled();

    /**
     * Whether EBS encryption remediation should be enabled.
     * Automatically enables EBS encryption by default for the account.
     */
    boolean isEbsEncryptionRemediationEnabled();

    /**
     * Whether GuardDuty remediation should be enabled.
     * Automatically enables GuardDuty threat detection if not already enabled.
     */
    boolean isGuardDutyRemediationEnabled();

    /**
     * Whether VPC default security group remediation should be enabled.
     * Automatically removes all rules from the default security group.
     */
    boolean isVpcDefaultSgRemediationEnabled();

    /**
     * Whether ELB deletion protection remediation should be enabled.
     * Automatically enables deletion protection on load balancers.
     */
    boolean isElbDeletionProtectionRemediationEnabled();

    /**
     * Whether KMS key rotation remediation should be enabled.
     * Automatically enables automatic key rotation for customer-managed KMS keys.
     */
    boolean isKmsKeyRotationRemediationEnabled();

    /**
     * Whether SSH removal remediation should be enabled.
     * Automatically removes public SSH access from security groups.
     * WARNING: Could break access if SSH is required.
     */
    boolean isSshRemovalRemediationEnabled();

    /**
     * Whether access key rotation remediation should be enabled.
     * Automatically revokes IAM access keys that are 90+ days old.
     * WARNING: Requires user notification workflow.
     */
    boolean isAccessKeyRotationRemediationEnabled();

    /**
     * Whether DynamoDB point-in-time recovery remediation should be enabled.
     * Automatically enables PITR for DynamoDB tables.
     */
    boolean isDynamoDbPitrRemediationEnabled();

    /**
     * Whether RDS Multi-AZ remediation should be enabled.
     * Automatically enables Multi-AZ for RDS instances.
     * WARNING: Requires maintenance window and causes brief downtime.
     */
    boolean isRdsMultiAzRemediationEnabled();

    /**
     * Whether RDS encryption remediation should be enabled.
     * Automatically creates encrypted snapshot and replaces unencrypted RDS instances.
     * WARNING: Complex operation requiring snapshot recreation.
     */
    boolean isRdsEncryptionRemediationEnabled();
}
