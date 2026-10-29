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
 * Production security profile configuration for logging, monitoring, encryption,
 * backups, and audit controls used by SOC 2, HIPAA, and PCI-DSS configurations.
 */
public class ProductionSecurityProfileConfiguration extends AbstractHardenedProfileConfiguration {

    private static final Logger LOG = Logger.getLogger(ProductionSecurityProfileConfiguration.class.getName());

    /**
     * Create ProductionSecurityProfileConfiguration.
     * @param deploymentContext Optional deployment context for overriding defaults
     */
    public ProductionSecurityProfileConfiguration(DeploymentContext deploymentContext) {
        super(deploymentContext, "PRODUCTION", LOG);
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
        return RetentionDays.SIX_YEARS; // HIPAA minimum retention (§164.316(b)(2)(i) - 6 years)
    }

    @Override
    public RetentionDays getFlowLogRetentionDays() {
        return RetentionDays.SIX_YEARS; // HIPAA minimum retention for audit logs
    }

    // Flow Log Configuration - accepted and rejected traffic

    // Security Monitoring - CloudTrail, GuardDuty, and AWS Config defaults

    @Override
    public boolean isGuardDutyEnabled() {
        // Default: always enabled for production threat detection
        return controls.resolve("GuardDuty", ComplianceMatrix.SecurityControl.THREAT_DETECTION,
            DeploymentContext::guardDutyEnabled, true);
    }

    @Override
    public boolean isAwsConfigEnabled() {
        // Default: enabled for production compliance monitoring
        // NOTE: AWS Config requires Configuration Recorder (one per region per account)
        // If you already have a Configuration Recorder in this region/account,
        // this may cause conflicts. To disable: override this in a custom SecurityProfileConfiguration
        return controls.resolve("AWS Config", ComplianceMatrix.SecurityControl.VULNERABILITY_MANAGEMENT,
            DeploymentContext::awsConfigEnabled, true);
    }

    @Override
    public boolean isAuditManagerEnabled() {
        // Opt-in only, deliberately not driven by the compliance matrix or a PRODUCTION default:
        // AWS closed Audit Manager to new accounts as of April 30, 2026 (existing per-account
        // setups still work, including creating new Assessments there, but a fresh account can
        // never enable it). Forcing this on by default or by framework requirement would fail
        // deploys for essentially every new account -- including every AWS Marketplace buyer
        // launching this template into their own account -- for a reason no deployment-context
        // change can fix. Soc2Rules' CC7.2 check (the one FrameworkRules control that used to
        // require this) now also accepts AWS Config's per-framework rules as an equally-valid
        // continuous-evidence-collection mechanism, which is AWS's own named replacement for this
        // exact change and is already on by default. This is the audit-manager-SERVICE flag,
        // distinct from DeploymentConfig#auditManagerEnabled, which is the separate master gate
        // SecurityRules.install() uses to decide whether ANY FrameworkRules compliance validation
        // runs at all.
        return deploymentContext != null && Boolean.TRUE.equals(deploymentContext.auditManagerServiceEnabled());
    }

    // Encryption Configuration - Full encryption mandatory

    @Override
    public boolean isEfsEncryptionInTransitEnabled() {
        // Default: mandatory encryption for production
        return controls.resolve("EFS Encryption in Transit", ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
            DeploymentContext::efsEncryptionInTransitEnabled, true);
    }

    // Network Security - Maximum restrictions

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
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
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
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
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
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
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

    // Backup and Recovery - automated and cross-region defaults
    @Override
    public boolean isAutomatedBackupEnabled() {
        // Always enabled for production
        return controls.resolve("Automated Backup", ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
            DeploymentContext::automatedBackupEnabled, true);
    }

    @Override
    public int getBackupRetentionDays() {
        return 90; // Extended retention for production
    }

    @Override
    public boolean isCrossRegionBackupEnabled() {
        // Default: always enabled for production disaster recovery
        return controls.resolve("Cross-Region Backup", ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
            DeploymentContext::crossRegionBackupEnabled, true);
    }

    @Override
    public boolean isBackupVaultLockEnabled() {
        // Enable vault lock based on compliance matrix
        // Vault lock is required for PCI-DSS and HIPAA when BACKUP_RECOVERY control is enforced
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                return true;
            }
        }

        // Check deployment context override (only if compliance doesn't require it)
        if (deploymentContext != null && deploymentContext.backupVaultLockEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.backupVaultLockEnabled());
            LOG.info("PRODUCTION profile: Overriding Backup Vault Lock from deployment context: " + enabled);
            return enabled;
        }
        return false;
    }

    @Override
    public boolean isBackupVaultRetentionEnabled() {
        // Retain backup vault based on compliance matrix
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.BACKUP_RECOVERY
            )) {
                return true;
            }
        }

        // Check deployment context override (only if compliance doesn't require it)
        if (deploymentContext != null && deploymentContext.backupVaultRetentionEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.backupVaultRetentionEnabled());
            LOG.info("PRODUCTION profile: Overriding Backup Vault Retention from deployment context: " + enabled);
            return enabled;
        }
        return false;
    }

    // Compliance and Audit - billing and ALB access log defaults

    @Override
    public boolean isAlbDeletionProtectionEnabled() {
        return true; // Always enabled for production
    }

    @Override
    public RetentionDays getAlbAccessLogRetentionDays() {
        return RetentionDays.SIX_YEARS; // HIPAA minimum retention for audit logs
    }

    // Performance and Reliability - Maximum for production

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
    public boolean isRdsDeletionProtectionEnabled() {
        // Enable deletion protection based on compliance matrix
        if (deploymentContext != null) {
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
                frameworks,
                mode,
                ComplianceMatrix.SecurityControl.DELETION_PROTECTION
            )) {
                return true;
            }
        }

        // Check deployment context override (only if compliance doesn't require it)
        if (deploymentContext != null && deploymentContext.rdsDeletionProtectionEnabled() != null) {
            boolean enabled = Boolean.TRUE.equals(deploymentContext.rdsDeletionProtectionEnabled());
            LOG.info("PRODUCTION profile: Overriding RDS Deletion Protection from deployment context: " + enabled);
            return enabled;
        }
        return false;
    }

    @Override
    public boolean isRdsDatabaseMultiAzEnabled() {
        // Default: Enable for PRODUCTION even without compliance frameworks (best practice)
        return controls.resolve("RDS Multi-AZ", ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ,
            DeploymentContext::rdsDatabaseMultiAzEnabled, true);
    }

    @Override
    public boolean isDatabaseProvisioningChoiceRequired() {
        // Unconditional, not compliance-gated: this is about a deliberate choice being made at
        // all, not a specific control PCI-DSS/HIPAA/etc. calls for -- a PRODUCTION deployment
        // silently landing on an OPTIONAL app's embedded fallback storage (never a real database)
        // because provisionDatabase was left unset is wrong regardless of which compliance
        // frameworks, if any, are in play.
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

    // ==================== Advanced Monitoring & Threat Detection ====================

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
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
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
            ComplianceMode mode = controls.effectiveMode();
            String frameworks = deploymentContext.complianceFrameworks();

            if (ComplianceMatrix.isControlEnforced(
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
        // PRODUCTION default: on. The profile does not create an unencrypted resource; a framework in ENFORCE
        // mode requires it, and an explicit deployment-context value turns it off.
        return controls.resolve("CloudWatch Logs KMS encryption", ComplianceMatrix.SecurityControl.CLOUDWATCH_LOGS_KMS_ENCRYPTION,
            DeploymentContext::cloudWatchLogsKmsEncryptionEnabled, true);
    }

    @Override
    public boolean isSnsKmsEncryptionEnabled() {
        // PRODUCTION default: on. A framework in ENFORCE mode requires it and an explicit
        // deployment-context value turns it off.
        return controls.resolve("SNS KMS encryption", ComplianceMatrix.SecurityControl.SNS_KMS_ENCRYPTION,
            DeploymentContext::snsKmsEncryptionEnabled, true);
    }

    @Override
    public boolean isImdsv2Required() {
        // PRODUCTION default: true - Required for HIPAA compliance
        return controls.resolve("IMDSv2", ComplianceMatrix.SecurityControl.EC2_IMDSV2,
            DeploymentContext::imdsv2Required, true);
    }
}
