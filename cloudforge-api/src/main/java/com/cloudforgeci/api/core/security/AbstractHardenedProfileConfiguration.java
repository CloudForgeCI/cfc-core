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
 * The controls PRODUCTION and STAGING configure the same way. Each profile supplies the ones that
 * differ.
 */
public abstract class AbstractHardenedProfileConfiguration implements SecurityProfileConfiguration {

    private static final Logger LOG = Logger.getLogger(AbstractHardenedProfileConfiguration.class.getName());

    protected final DeploymentContext deploymentContext;
    protected final ProfileControls controls;

    /**
     * @param deploymentContext optional deployment context for overriding defaults
     * @param profile the profile name used in log messages
     * @param log the profile's logger
     */
    protected AbstractHardenedProfileConfiguration(DeploymentContext deploymentContext, String profile, Logger log) {
        this.deploymentContext = deploymentContext;
        this.controls = new ProfileControls(profile, deploymentContext, log);
    }

    @Override
    public DeploymentContext getDeploymentContext() {
        return deploymentContext;
    }

    @Override
    public RemovalPolicy getLogRemovalPolicy() {
        return RemovalPolicy.RETAIN; // Always retain logs in production
    }

    @Override
    public boolean isFlowLogsEnabled() {
        // Default: always enabled for production
        return controls.resolve("Flow Logs", ComplianceMatrix.SecurityControl.NETWORK_FLOW_LOGS,
            DeploymentContext::enableFlowlogs, true);
    }

    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ALL; // Record accepted and rejected traffic
    }

    @Override
    public boolean isSecurityMonitoringEnabled() {
        // Default: always enabled for production
        return controls.resolve("Security Monitoring", ComplianceMatrix.SecurityControl.SECURITY_MONITORING,
            DeploymentContext::securityMonitoringEnabled, true);
    }

    @Override
    public boolean isCloudTrailEnabled() {
        // Default: always enabled for production audit
        return controls.resolve("CloudTrail", ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
            DeploymentContext::cloudTrailEnabled, true);
    }

    @Override
    public boolean isEbsEncryptionEnabled() {
        // Default: mandatory encryption for production
        return controls.resolve("EBS Encryption", ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
            DeploymentContext::ebsEncryptionEnabled, true);
    }

    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        // Default: mandatory encryption for production
        return controls.resolve("EFS Encryption at Rest", ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
            DeploymentContext::efsEncryptionAtRestEnabled, true);
    }

    @Override
    public boolean isS3EncryptionEnabled() {
        // Default: mandatory encryption for production
        return controls.resolve("S3 Encryption", ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
            DeploymentContext::s3EncryptionEnabled, true);
    }

    @Override
    public boolean isVpcEndpointsEnabled() {
        return true; // Always enabled for production security
    }

    @Override
    public boolean isNatGatewayEnabled() {
        return true; // Always use private subnets for production
    }

    @Override
    public boolean isCloudFrontEnabled() {
        // Check deployment context first, then fall back to profile default
        if (deploymentContext != null && deploymentContext.cloudfrontEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.cloudfrontEnabled());
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
        // To enable: set cloudfrontEnabled = true in deployment-context.json
        return false;
    }

    @Override
    public boolean isDetailedBillingEnabled() {
        return true; // Always enabled for production cost management
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
        // Default: always enabled for production audit
        return controls.resolve("ALB Access Logging", ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
            DeploymentContext::albAccessLogging, true);
    }

    @Override
    public boolean isMultiAzEnforced() {
        return true; // Always enforced for production
    }

    @Override
    public boolean isAutoScalingEnabled() {
        return true; // Always enabled for production
    }

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

    @Override
    public boolean isRdsDeletionProtectionRemediationEnabled() {
        // Disabled by default - deletion protection is set during RDS creation
        // This remediation would enable protection on existing instances
        return false;
    }

    @Override
    public boolean isMfaRequired() {
        // MFA required for production - compliance requirement (PCI-DSS, HIPAA, SOC 2)
        return true;
    }

    @Override
    public String getDefaultMfaMethod() {
        // Both methods available - maximum flexibility for users
        return "both";
    }

    @Override
    public boolean isSelfSignupEnabled() {
        // Strict access control - admins must create accounts
        return false;
    }

    @Override
    public boolean isPreventUserExistenceErrorsEnabled() {
        // Prevent username enumeration attacks
        return true;
    }

    @Override
    public boolean isAdvancedSecurityEnabled() {
        // Recommended for threat detection (requires Cognito Plus tier)
        // Risk-based adaptive authentication detects suspicious login patterns
        return true;
    }

    @Override
    public boolean isMacieEnabled() {
        // PRODUCTION default: false (requires explicit opt-in due to cost)
        // Note: ComplianceMatrix will require this for HIPAA/GDPR compliance in ENFORCE mode
        return controls.resolve("Macie", ComplianceMatrix.SecurityControl.SENSITIVE_DATA_DISCOVERY,
            DeploymentContext::macieEnabled, false);
    }

    @Override
    public boolean isCloudTrailInsightsEnabled() {
        // PRODUCTION default: false (opt-in due to cost, but required for SOC2/NIST)
        return controls.resolve("CloudTrail Insights", ComplianceMatrix.SecurityControl.CLOUDTRAIL_INSIGHTS,
            DeploymentContext::cloudTrailInsightsEnabled, false);
    }

    @Override
    public boolean isRoute53QueryLoggingEnabled() {
        // PRODUCTION default: false (opt-in, but required for SOC2/NIST)
        return controls.resolve("Route53 Query Logging", ComplianceMatrix.SecurityControl.ROUTE53_QUERY_LOGGING,
            DeploymentContext::route53QueryLoggingEnabled, false);
    }

    @Override
    public boolean isS3ObjectLockEnabled() {
        // PRODUCTION default: false (opt-in, but required for HIPAA/PCI-DSS)
        return controls.resolve("S3 Object Lock", ComplianceMatrix.SecurityControl.S3_OBJECT_LOCK,
            DeploymentContext::s3ObjectLockEnabled, false);
    }

    @Override
    public boolean isSecretsKmsEncryptionEnabled() {
        // Default: on. The profile does not create a secret under the AWS-managed key.
        return controls.resolve("secrets KMS encryption", ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
            DeploymentContext::secretsKmsEncryptionEnabled, true);
    }
}
