package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.RuntimeType;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.RetentionDays;

/**
 * Production security profile configuration with comprehensive security measures.
 * Implements enterprise-grade security for SOC/HIPAA/PCI-DSS compliance.
 */
public class ProductionSecurityProfileConfiguration implements SecurityProfileConfiguration {

    private final DeploymentContext deploymentContext;

    /**
     * Create ProductionSecurityProfileConfiguration.
     * @param deploymentContext Optional deployment context for overriding defaults
     */
    public ProductionSecurityProfileConfiguration(DeploymentContext deploymentContext) {
        this.deploymentContext = deploymentContext;
    }

    /**
     * Create ProductionSecurityProfileConfiguration with no deployment context.
     * Uses only profile defaults.
     */
    public ProductionSecurityProfileConfiguration() {
        this(null);
    }

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
    public boolean isAwsConfigEnabled() {
        // AWS Config enabled for production compliance monitoring
        // NOTE: AWS Config requires Configuration Recorder (one per region per account)
        // If you already have a Configuration Recorder in this region/account,
        // this may cause conflicts. To disable: override this in a custom SecurityProfileConfiguration
        return true;
    }

    @Override
    public boolean isAuditManagerEnabled() {
        // AWS Audit Manager enabled for production continuous auditing
        // NOTE: Audit Manager must be enabled in the AWS account first
        // This provides automated evidence collection for compliance frameworks
        return true;
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
    public int getNatGatewayCount(TopologyType topology, RuntimeType runtime, String networkMode) {
        // Production respects network mode but defaults to NAT gateways for security
        if ("public-no-nat".equals(networkMode)) {
            return 0; // No NAT gateways for public subnets when explicitly requested
        }
        // Use 2 NAT gateways for high availability across AZs for private subnets
        return 2;
    }
    
    @Override
    public boolean isWafEnabled() {
        // Use deployment context wafEnabled field, default to false if not set
        // The WafFactory configuration for Jenkins:
        // - CommonRuleSet: DISABLED (too many false positives on i18n, setup, config pages)
        // - SQL Injection rules: ACTIVE (blocks SQLi attacks)
        // - Linux OS rules: ACTIVE (blocks shell injection, path traversal)
        // - Known Bad Inputs: ACTIVE (with Java deserialization exceptions)
        //
        // This provides core security protection (SQLi + OS exploits)
        // while allowing Jenkins to function without 403 errors.
        //
        // To enable: set wafEnabled=true in deployment-context.json
        return deploymentContext != null && deploymentContext.wafEnabled();
    }
    
    @Override
    public boolean isCloudFrontEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null) {
            return deploymentContext.cloudfrontEnabled();
        }

        // CloudFront disabled by default for Jenkins deployments
        // Jenkins is primarily dynamic content (build logs, real-time updates, WebSockets)
        // that doesn't benefit from CDN caching and can cause issues with:
        // - Session management and CSRF tokens
        // - WebSocket connections for real-time build logs
        // - Authentication flows (ALB OIDC, Jenkins OIDC)
        // - POST-heavy workflows (form submissions, API calls)
        //
        // For DDoS protection, use WAF at the ALB level instead.
        // CloudFront is better suited for static content delivery (e.g., S3 websites).
        //
        // To enable: set cloudfrontEnabled=true in deployment-context.json
        return false;
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

    // AWS Config Remediation Settings - Production defaults
    @Override
    public boolean isS3VersioningRemediationEnabled() {
        // Disabled by default due to cost implications
        // Versioning increases storage costs as it retains all object versions
        // Enable manually if required for compliance
        return false;
    }

    @Override
    public boolean isCloudTrailBucketAccessRemediationEnabled() {
        // Disabled by default to prevent automatic policy changes
        // CloudTrail bucket policy should be carefully managed
        // Enable manually if needed
        return false;
    }

    @Override
    public boolean isEbsEncryptionRemediationEnabled() {
        // Enabled by default - low risk, high security value
        // Automatically enables EBS encryption by default for the account
        // This is a one-time account-level setting with no disruption
        return true;
    }

    @Override
    public boolean isGuardDutyRemediationEnabled() {
        // Enabled by default - low risk, high security value
        // Automatically enables GuardDuty threat detection if not already enabled
        // Production should always have threat detection enabled
        return true;
    }

    @Override
    public boolean isVpcDefaultSgRemediationEnabled() {
        // Enabled by default - low risk, high security value
        // Automatically removes all rules from the default security group
        // Best practice: never use the default security group
        return true;
    }

    @Override
    public boolean isElbDeletionProtectionRemediationEnabled() {
        // Enabled by default - low risk, high security value
        // Automatically enables deletion protection on load balancers
        // Prevents accidental deletion of production load balancers
        return true;
    }

    @Override
    public boolean isKmsKeyRotationRemediationEnabled() {
        // Enabled by default - low risk, high security value
        // Automatically enables automatic key rotation for customer-managed KMS keys
        // Key rotation is a compliance best practice
        return true;
    }

    @Override
    public boolean isSshRemovalRemediationEnabled() {
        // Disabled by default - could break SSH access if required
        // Only enable if you're certain SSH should never be publicly accessible
        // Use bastion hosts or VPN for SSH access instead
        return false;
    }

    @Override
    public boolean isAccessKeyRotationRemediationEnabled() {
        // Disabled by default - requires user notification workflow
        // Automatically revoking access keys can break applications
        // Enable only with proper notification and rotation procedures
        return false;
    }

    @Override
    public boolean isDynamoDbPitrRemediationEnabled() {
        // Enabled by default - low risk, high security value
        // Automatically enables point-in-time recovery for DynamoDB tables
        // Provides data protection with minimal cost impact
        return true;
    }

    @Override
    public boolean isRdsMultiAzRemediationEnabled() {
        // Disabled by default - requires maintenance window and causes brief downtime
        // Multi-AZ conversion requires database restart
        // Enable manually during planned maintenance window
        return false;
    }

    @Override
    public boolean isRdsEncryptionRemediationEnabled() {
        // Disabled by default - complex operation requiring snapshot recreation
        // Encrypting an existing RDS instance requires:
        // 1. Creating encrypted snapshot
        // 2. Restoring from snapshot
        // 3. Updating application connection strings
        // Enable manually with proper planning
        return false;
    }
}
