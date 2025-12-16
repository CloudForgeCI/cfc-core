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
 * Production security profile configuration with comprehensive security measures.
 * Implements enterprise-grade security for SOC/HIPAA/PCI-DSS compliance.
 */
public class ProductionSecurityProfileConfiguration implements SecurityProfileConfiguration {

    private static final Logger LOG = Logger.getLogger(ProductionSecurityProfileConfiguration.class.getName());
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
        // Check deployment context first for compliance framework overrides
        if (deploymentContext != null && deploymentContext.logRetentionDays() != null) {
            int days = deploymentContext.logRetentionDays();
            RetentionDays retention = RetentionDaysConverter.fromDays(days);
            LOG.info("PRODUCTION profile: Overriding log retention from deployment context: " + days + " days -> " + retention);
            return retention;
        }
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
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("flowLogsEnabled");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding flowLogsEnabled from deployment context: " + enabled);
                return enabled;
            }
        }
        return true; // Always enabled for production
    }

    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ALL; // All traffic for comprehensive monitoring
    }

    // Security Monitoring - Comprehensive for production
    @Override
    public boolean isSecurityMonitoringEnabled() {
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("securityMonitoringEnabled");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding securityMonitoringEnabled from deployment context: " + enabled);
                return enabled;
            }
        }
        return true; // Always enabled for production
    }

    @Override
    public boolean isCloudTrailEnabled() {
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("cloudTrailEnabled");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding cloudTrailEnabled from deployment context: " + enabled);
                return enabled;
            }
        }
        return true; // Always enabled for production audit
    }

    @Override
    public boolean isGuardDutyEnabled() {
        // Check deployment context first for compliance framework overrides
        if (deploymentContext != null && deploymentContext.guardDutyEnabled() != null) {
            boolean enabled = deploymentContext.guardDutyEnabled();
            LOG.info("PRODUCTION profile: Overriding GuardDuty from deployment context: " + enabled);
            return enabled;
        }
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
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("efsEncryptionInTransitEnabled");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding efsEncryptionInTransitEnabled from deployment context: " + enabled);
                return enabled;
            }
        }
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
        // To enable: set wafEnabled = true in deployment-context.json
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
        // To enable: set cloudfrontEnabled = true in deployment-context.json
        return false;
    }

    // Backup and Recovery - Comprehensive for production
    @Override
    public boolean isAutomatedBackupEnabled() {
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("automatedBackupEnabled");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding automatedBackupEnabled from deployment context: " + enabled);
                return enabled;
            }
        }
        return true; // Always enabled for production
    }

    @Override
    public int getBackupRetentionDays() {
        return 90; // Extended retention for production
    }

    @Override
    public boolean isCrossRegionBackupEnabled() {
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("crossRegionBackupEnabled");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding crossRegionBackupEnabled from deployment context: " + enabled);
                return enabled;
            }
        }
        return true; // Always enabled for production disaster recovery
    }

    // Compliance and Audit - Comprehensive for production
    @Override
    public boolean isDetailedBillingEnabled() {
        return true; // Always enabled for production cost management
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
        // Check deployment context raw map first for testing overrides
        if (deploymentContext != null && deploymentContext.raw() != null) {
            Object value = deploymentContext.raw().get("albAccessLogging");
            if (value != null) {
                boolean enabled = Boolean.parseBoolean(String.valueOf(value));
                LOG.info("PRODUCTION profile: Overriding albAccessLogging from deployment context: " + enabled);
                return enabled;
            }
        }
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

    @Override
    public boolean isSecurityHubRemediationEnabled() {
        // Enabled by default for production - required for compliance frameworks
        // Security Hub aggregates findings from GuardDuty, Inspector, Macie
        // Required for FedRamp, PCI-DSS, HIPAA, SOC2 centralized security monitoring
        return true;
    }

    @Override
    public boolean isInspectorRemediationEnabled() {
        // Enabled by default for production - required for vulnerability scanning
        // Inspector v2 continuously scans EC2, ECR, Lambda for CVEs
        // Required for PCI-DSS Req 6.2, FedRamp RA-5
        return true;
    }

    @Override
    public boolean isMacieRemediationEnabled() {
        // Enabled by default for production - required for sensitive data discovery
        // Macie discovers and protects PII/PHI in S3 buckets
        // Required for HIPAA, GDPR data classification and protection
        // WARNING: Has cost implications (~$1/GB scanned)
        return true;
    }

    @Override
    public boolean isEcrImageScanningRemediationEnabled() {
        // Enabled by default for production - required for container security
        // ECR scan-on-push ensures no vulnerable images are deployed
        // Required for container security best practices
        return true;
    }

    // ==================== Authentication Configuration ====================

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
    public int getAccessTokenValidityHours() {
        // Strict token lifetime for production security (PCI-DSS compliant)
        return 1;
    }

    @Override
    public int getIdTokenValidityHours() {
        // Minimize exposure window
        return 1;
    }

    @Override
    public int getRefreshTokenValidityDays() {
        // Daily re-authentication for maximum security
        return 1;
    }

    @Override
    public int getMinimumPasswordLength() {
        // Strong password policy (NIST 800-63B compliant)
        return 14;
    }

    @Override
    public int getTempPasswordValidityDays() {
        // Immediate action required
        return 1;
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

    // ==================== Advanced Monitoring & Threat Detection ====================

    @Override
    public boolean isMacieEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.macieEnabled() != null) {
            boolean enabled = deploymentContext.macieEnabled();
            LOG.info("PRODUCTION profile: Overriding Macie from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: false (requires explicit opt-in due to cost)
        // Note: FrameworkRules will require this for HIPAA/GDPR compliance
        return false;
    }

    @Override
    public boolean isMacieAutomatedDiscoveryEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.macieAutomatedDiscoveryEnabled() != null) {
            boolean enabled = deploymentContext.macieAutomatedDiscoveryEnabled();
            LOG.info("PRODUCTION profile: Overriding Macie automated discovery from deployment context: " + enabled);
            return enabled;
        }
        // Only enable automated discovery if Macie itself is enabled
        return isMacieEnabled();
    }

    @Override
    public boolean isSecurityHubEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.securityHubEnabled() != null) {
            boolean enabled = deploymentContext.securityHubEnabled();
            LOG.info("PRODUCTION profile: Overriding Security Hub from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: true (recommended for centralized security monitoring)
        return isSecurityMonitoringEnabled();
    }

    @Override
    public boolean isInspectorEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.inspectorEnabled() != null) {
            boolean enabled = deploymentContext.inspectorEnabled();
            LOG.info("PRODUCTION profile: Overriding Inspector from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: true (recommended for vulnerability scanning)
        return isSecurityMonitoringEnabled();
    }

    @Override
    public boolean isAntiMalwareEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.antiMalwareEnabled() != null) {
            boolean enabled = deploymentContext.antiMalwareEnabled();
            LOG.info("PRODUCTION profile: Overriding anti-malware from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: false (only applicable to EC2, requires explicit configuration)
        return false;
    }

    @Override
    public boolean isFileIntegrityMonitoringEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.fileIntegrityMonitoringEnabled() != null) {
            boolean enabled = deploymentContext.fileIntegrityMonitoringEnabled();
            LOG.info("PRODUCTION profile: Overriding file integrity monitoring from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: false (only applicable to EC2, requires explicit configuration)
        return false;
    }

    @Override
    public boolean isContainerRuntimeSecurityEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.containerRuntimeSecurityEnabled() != null) {
            boolean enabled = deploymentContext.containerRuntimeSecurityEnabled();
            LOG.info("PRODUCTION profile: Overriding container runtime security from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: false (requires explicit configuration)
        return false;
    }

    @Override
    public boolean isContainerImageScanningEnabled() {
        // Check deployment context override using proper accessor method
        if (deploymentContext != null && deploymentContext.containerImageScanningEnabled() != null) {
            boolean enabled = deploymentContext.containerImageScanningEnabled();
            LOG.info("PRODUCTION profile: Overriding container image scanning from deployment context: " + enabled);
            return enabled;
        }
        // PRODUCTION default: false (typically handled by ECR, requires explicit configuration)
        return false;
    }
}
