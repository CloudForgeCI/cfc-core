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

    private ComplianceMode getEffectiveComplianceMode() {
        if (deploymentContext == null) {
            return ComplianceMode.DISABLED;
        }

        // Use the compliance mode from deployment context if set
        ComplianceMode contextMode = deploymentContext.complianceMode();
        if (contextMode != null) {
            return contextMode;
        }

        // Default based on whether compliance frameworks are enabled
        String frameworks = deploymentContext.complianceFrameworks();
        return (frameworks != null && !frameworks.isEmpty())
            ? ComplianceMode.ENFORCE
            : ComplianceMode.DISABLED;
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
        return RetentionDays.SIX_YEARS; // HIPAA minimum retention (§164.316(b)(2)(i) - 6 years)
    }

    @Override
    public RetentionDays getFlowLogRetentionDays() {
        return RetentionDays.SIX_YEARS; // HIPAA minimum retention for audit logs
    }

    @Override
    public RemovalPolicy getLogRemovalPolicy() {
        return RemovalPolicy.RETAIN; // Always retain logs in production
    }

    // Flow Log Configuration - Comprehensive monitoring
    @Override
    public boolean isFlowLogsEnabled() {
        // Check if compliance matrix REQUIRES this control (cannot be overridden)
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.NETWORK_FLOW_LOGS
            )) {
                LOG.info("PRODUCTION profile: Flow Logs enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (only if compliance doesn't require it)
        if (deploymentContext != null && deploymentContext.enableFlowlogs() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.enableFlowlogs());
            LOG.info("PRODUCTION profile: Flow Logs explicitly configured in deployment context: " + enabled +
                     " (raw value: " + deploymentContext.enableFlowlogs() + ")");
            return enabled;
        }

        // Default: always enabled for production
        LOG.info("PRODUCTION profile: Flow Logs using default (true) - no explicit config");
        return true;
    }

    @Override
    public FlowLogTrafficType getFlowLogTrafficType() {
        return FlowLogTrafficType.ALL; // All traffic for comprehensive monitoring
    }

    // Security Monitoring - Comprehensive for production
    @Override
    public boolean isSecurityMonitoringEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SECURITY_MONITORING
            )) {
                LOG.info("PRODUCTION profile: Security Monitoring enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor)
        if (deploymentContext != null && deploymentContext.securityMonitoringEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.securityMonitoringEnabled());
            LOG.info("PRODUCTION profile: Overriding Security Monitoring from deployment context: " + enabled);
            return enabled;
        }

        // Default: always enabled for production
        return true;
    }

    @Override
    public boolean isCloudTrailEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.AUDIT_LOGGING
            )) {
                LOG.info("PRODUCTION profile: CloudTrail enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.cloudTrailEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.cloudTrailEnabled());
            LOG.info("PRODUCTION profile: Overriding CloudTrail from deployment context: " + enabled);
            return enabled;
        }

        // Default: always enabled for production audit
        return true;
    }

    @Override
    public boolean isGuardDutyEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.THREAT_DETECTION
            )) {
                LOG.info("PRODUCTION profile: GuardDuty enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.guardDutyEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.guardDutyEnabled());
            LOG.info("PRODUCTION profile: Overriding GuardDuty from deployment context: " + enabled);
            return enabled;
        }

        // Default: always enabled for production threat detection
        return true;
    }

    @Override
    public boolean isAwsConfigEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.VULNERABILITY_MANAGEMENT
            )) {
                LOG.info("PRODUCTION profile: AWS Config enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.awsConfigEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.awsConfigEnabled());
        }

        // Default: enabled for production compliance monitoring
        // NOTE: AWS Config requires Configuration Recorder (one per region per account)
        // If you already have a Configuration Recorder in this region/account,
        // this may cause conflicts. To disable: override this in a custom SecurityProfileConfiguration
        return true;
    }

    @Override
    public boolean isAuditManagerEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.AUDIT_MANAGER
            )) {
                LOG.info("PRODUCTION profile: Audit Manager enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.auditManagerEnabled() != null) {
            return Boolean.TRUE.equals(deploymentContext.auditManagerEnabled());
        }

        // Default: enabled for production continuous auditing
        // NOTE: Audit Manager must be enabled in the AWS account first
        // This provides automated evidence collection for compliance frameworks
        return true;
    }

    // Encryption Configuration - Full encryption mandatory
    @Override
    public boolean isEbsEncryptionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            )) {
                LOG.info("PRODUCTION profile: EBS Encryption enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: mandatory encryption for production
        return true;
    }

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT
            )) {
                LOG.info("PRODUCTION profile: EFS Encryption in Transit enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor)
        if (deploymentContext != null && deploymentContext.efsEncryptionInTransitEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.efsEncryptionInTransitEnabled());
            LOG.info("PRODUCTION profile: Overriding EFS Encryption in Transit from deployment context: " + enabled);
            return enabled;
        }

        // Default: mandatory encryption for production
        return true;
    }

    @Override
    public boolean isEfsEncryptionAtRestEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            )) {
                LOG.info("PRODUCTION profile: EFS Encryption at Rest enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: mandatory encryption for production
        return true;
    }

    @Override
    public boolean isS3EncryptionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            )) {
                LOG.info("PRODUCTION profile: S3 Encryption enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: mandatory encryption for production
        return true;
    }

    // Network Security - Maximum restrictions
    @Override
    public boolean isVpcEndpointsEnabled() {
        return true; // Always enabled for production security
    }

    @Override
    public boolean isRestrictSecurityGroupEgressEnabled() {
        // Check deployment context override FIRST
        if (deploymentContext != null && deploymentContext.restrictSecurityGroupEgress() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.restrictSecurityGroupEgress());
            LOG.info("PRODUCTION profile: Security group egress restriction explicitly configured: " + enabled);
            return enabled;
        }

        // Check if compliance matrix requires network segmentation (which includes egress control)
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION
            )) {
                LOG.info("PRODUCTION profile: Security group egress restriction enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: allow all outbound - requires VPC endpoints to restrict
        return false;
    }

    @Override
    public boolean isNatGatewayEnabled() {
        return true; // Always use private subnets for production
    }

    @Override
    public int getNatGatewayCount(TopologyType topology, RuntimeType runtime, NetworkMode networkMode) {
        // Production respects network mode but defaults to NAT gateways for security
        if (networkMode == NetworkMode.PUBLIC) {
            return 0; // No NAT gateways for public subnets when explicitly requested
        }
        // Use 2 NAT gateways for high availability across AZs for private subnets
        return 2;
    }

    @Override
    public boolean isWafEnabled() {
        // Check deployment context override FIRST
        // The WafFactory configuration for Jenkins:
        // - CommonRuleSet: DISABLED (too many false positives on i18n, setup, config pages)
        // - SQL Injection rules: ACTIVE (blocks SQLi attacks)
        // - Linux OS rules: ACTIVE (blocks shell injection, path traversal)
        // - Known Bad Inputs: ACTIVE (with Java deserialization exceptions)
        if (deploymentContext != null && deploymentContext.wafEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.wafEnabled());
            LOG.info("PRODUCTION profile: WAF explicitly configured: " + enabled);
            return enabled;
        }

        // Check if compliance matrix requires WAF protection
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.WAF_PROTECTION
            )) {
                LOG.info("PRODUCTION profile: WAF enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        return false; // Default: disabled (opt-in when no compliance frameworks require it)
    }

    @Override
    public boolean isHttpsStrictEnabled() {
        // Check deployment context override FIRST
        if (deploymentContext != null && deploymentContext.httpsStrictEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.httpsStrictEnabled());
            LOG.info("PRODUCTION profile: HTTPS strict mode explicitly configured: " + enabled);
            return enabled;
        }

        // Check if compliance matrix requires HTTPS strict mode
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.HTTPS_STRICT
            )) {
                LOG.info("PRODUCTION profile: HTTPS strict mode enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: disabled (allow HTTP→HTTPS redirect for better UX)
        return false;
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

    // Backup and Recovery - Comprehensive for production
    @Override
    public boolean isAutomatedBackupEnabled() {
        // Check if compliance matrix REQUIRES this control (cannot be overridden)
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                LOG.info("PRODUCTION profile: Automated Backup enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (only if compliance doesn't require it)
        if (deploymentContext != null && deploymentContext.automatedBackupEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.automatedBackupEnabled());
            LOG.info("PRODUCTION profile: Overriding automatedBackupEnabled from deployment context: " + enabled);
            return enabled;
        }
        return true; // Always enabled for production
    }

    @Override
    public int getBackupRetentionDays() {
        return 90; // Extended retention for production
    }

    @Override
    public boolean isCrossRegionBackupEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                LOG.info("PRODUCTION profile: Cross-Region Backup enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor)
        if (deploymentContext != null && deploymentContext.crossRegionBackupEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.crossRegionBackupEnabled());
            LOG.info("PRODUCTION profile: Overriding Cross-Region Backup from deployment context: " + enabled);
            return enabled;
        }

        // Default: always enabled for production disaster recovery
        return true;
    }

    @Override
    public boolean isBackupVaultLockEnabled() {
        // Enable vault lock based on compliance matrix
        // Vault lock is required for PCI-DSS and HIPAA when BACKUP_RECOVERY control is enforced
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            return ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            );
        }
        return false;
    }

    @Override
    public boolean isBackupVaultRetentionEnabled() {
        // Retain backup vault based on compliance matrix
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                return true;
            }
        }
        return false;
    }

    // Compliance and Audit - Comprehensive for production
    @Override
    public boolean isDetailedBillingEnabled() {
        return true; // Always enabled for production cost management
    }

    @Override
    public boolean isAlbAccessLoggingEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.AUDIT_LOGGING
            )) {
                LOG.info("PRODUCTION profile: ALB Access Logging enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.albAccessLogging() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.albAccessLogging());
            LOG.info("PRODUCTION profile: Overriding ALB Access Logging from deployment context: " + enabled);
            return enabled;
        }

        // Default: always enabled for production audit
        return true;
    }

    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.SIX_YEARS; // HIPAA minimum retention for audit logs
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
    public boolean isRdsDeletionProtectionRemediationEnabled() {
        // Disabled by default - deletion protection is set during RDS creation
        // This remediation would enable protection on existing instances
        return false;
    }

    @Override
    public boolean isRdsDeletionProtectionEnabled() {
        // Enable deletion protection based on compliance matrix
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.DELETION_PROTECTION
            )) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isRdsDatabaseMultiAzEnabled() {
        // Enable Multi-AZ based on compliance matrix
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ
            )) {
                LOG.severe("PRODUCTION profile: RDS Multi-AZ enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Default: Enable for PRODUCTION even without compliance frameworks (best practice)
        return true;
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
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SENSITIVE_DATA_DISCOVERY
            )) {
                LOG.info("PRODUCTION profile: Macie enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.macieEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.macieEnabled());
            LOG.info("PRODUCTION profile: Overriding Macie from deployment context: " + enabled);
            return enabled;
        }

        // PRODUCTION default: false (requires explicit opt-in due to cost)
        // Note: ComplianceMatrix will require this for HIPAA/GDPR compliance in ENFORCE mode
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
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SECURITY_HUB
            )) {
                LOG.info("PRODUCTION profile: Security Hub enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.securityHubEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.securityHubEnabled());
            LOG.info("PRODUCTION profile: Overriding Security Hub from deployment context: " + enabled);
            return enabled;
        }

        // PRODUCTION default: true (recommended for centralized security monitoring)
        return isSecurityMonitoringEnabled();
    }

    @Override
    public boolean isInspectorEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.VULNERABILITY_SCANNING
            )) {
                LOG.info("PRODUCTION profile: Inspector enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override (Boolean accessor - need null check)
        if (deploymentContext != null && deploymentContext.inspectorEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.inspectorEnabled());
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

    // ==================== Enhanced Compliance Controls ====================

    @Override
    public boolean isCloudWatchLogsKmsEncryptionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.CLOUDWATCH_LOGS_KMS_ENCRYPTION
            )) {
                LOG.info("PRODUCTION profile: CloudWatch Logs KMS encryption enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override
        if (deploymentContext != null && deploymentContext.cloudWatchLogsKmsEncryptionEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.cloudWatchLogsKmsEncryptionEnabled());
            LOG.info("PRODUCTION profile: Overriding CloudWatch Logs KMS encryption from deployment context: " + enabled);
            return enabled;
        }

        // PRODUCTION default: false (opt-in, but required for compliance frameworks)
        // Note: ComplianceMatrix will require this for PCI-DSS, HIPAA, SOC2 in ENFORCE mode
        return false;
    }

    @Override
    public boolean isCloudTrailInsightsEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.CLOUDTRAIL_INSIGHTS
            )) {
                LOG.info("PRODUCTION profile: CloudTrail Insights enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override
        if (deploymentContext != null && deploymentContext.cloudTrailInsightsEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.cloudTrailInsightsEnabled());
            LOG.info("PRODUCTION profile: Overriding CloudTrail Insights from deployment context: " + enabled);
            return enabled;
        }

        // PRODUCTION default: false (opt-in due to cost, but required for SOC2/NIST)
        return false;
    }

    @Override
    public boolean isRoute53QueryLoggingEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.ROUTE53_QUERY_LOGGING
            )) {
                LOG.info("PRODUCTION profile: Route53 Query Logging enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override
        if (deploymentContext != null && deploymentContext.route53QueryLoggingEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.route53QueryLoggingEnabled());
            LOG.info("PRODUCTION profile: Overriding Route53 Query Logging from deployment context: " + enabled);
            return enabled;
        }

        // PRODUCTION default: false (opt-in, but required for SOC2/NIST)
        return false;
    }

    @Override
    public boolean isS3ObjectLockEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.S3_OBJECT_LOCK
            )) {
                LOG.info("PRODUCTION profile: S3 Object Lock enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }

        // Check deployment context override
        if (deploymentContext != null && deploymentContext.s3ObjectLockEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.s3ObjectLockEnabled());
            LOG.info("PRODUCTION profile: Overriding S3 Object Lock from deployment context: " + enabled);
            return enabled;
        }

        // PRODUCTION default: false (opt-in, but required for HIPAA/PCI-DSS)
        return false;
    }

    @Override
    public boolean isSnsKmsEncryptionEnabled() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.SNS_KMS_ENCRYPTION
            )) {
                LOG.info("PRODUCTION profile: SNS KMS encryption enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }
        // PRODUCTION default: true when HIPAA or PCI-DSS compliance is required
        return false;
    }

    @Override
    public boolean isImdsv2Required() {
        // Check if compliance matrix requires this control
        if (deploymentContext != null) {
            ComplianceMode mode = getEffectiveComplianceMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlRequired(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.EC2_IMDSV2
            )) {
                LOG.info("PRODUCTION profile: IMDSv2 enforced by compliance frameworks: " + frameworks);
                return true;
            }
        }
        // PRODUCTION default: true - Required for HIPAA compliance
        return true;
    }
}
