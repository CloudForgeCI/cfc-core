package com.cloudforgeci.api.interfaces;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.core.DeploymentContext;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.RemovalPolicy;

/**
 * Configuration interface for security profile settings.
 * Defines security best practices and compliance requirements for each environment.
 *
 * <p><b>Adding a new profile-aware field:</b></p>
 * <ol>
 *   <li>Add the field to {@code DeploymentConfig.java}</li>
 *   <li>Add a default method here using {@link #boolOverride(String, boolean)}
 *       for the common override pattern</li>
 *   <li>Override the default method in each profile implementation if the default differs</li>
 * </ol>
 *
 * <p>The common override pattern (check deployment context, then fall back to profile default)
 * is encapsulated in the default methods below, reducing boilerplate.</p>
 */
public interface SecurityProfileConfiguration {

    /**
     * Returns the deployment context for override resolution, or null if not set.
     * Implementations should return the DeploymentContext passed to their constructor.
     */
    DeploymentContext getDeploymentContext();

    /**
     * Helper for the common boolean override pattern: if the deployment context
     * has a non-null value for the given config field, use it; otherwise return
     * the provided profile default.
     *
     * @param configFieldName the field name on DeploymentConfig to check
     * @param profileDefault the default value for this security profile
     * @return the resolved boolean value
     */
    default boolean boolOverride(String configFieldName, boolean profileDefault) {
        DeploymentContext ctx = getDeploymentContext();
        if (ctx != null && ctx.config != null) {
            try {
                var field = DeploymentConfig.class.getField(configFieldName);
                Object val = field.get(ctx.config);
                if (val instanceof Boolean b) {
                    return b;
                }
            } catch (Exception ignored) {}
        }
        return profileDefault;
    }

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
     * Whether security group egress should be restricted to VPC CIDR only.
     *
     * <p>When enabled, security groups are created with allowAllOutbound=false
     * and egress is restricted to the VPC CIDR range. This requires VPC endpoints
     * for AWS services (CloudWatch, RDS monitoring, etc.) to function properly.</p>
     *
     * <ul>
     *   <li>DEV: false - Allow all outbound for simplicity</li>
     *   <li>STAGING: false - Allow all outbound unless explicitly enabled</li>
     *   <li>PRODUCTION: false - Requires VPC endpoints, enable via deployment context</li>
     * </ul>
     *
     * @return true if egress should be restricted to VPC CIDR only
     */
    boolean isRestrictSecurityGroupEgressEnabled();

    /**
     * Whether NAT Gateway should be used for outbound internet access.
     */
    boolean isNatGatewayEnabled();

    /**
     * Get the number of NAT gateways to create based on topology, runtime, and security profile.
     * This method encapsulates all NAT gateway logic including network mode, security requirements,
     * and topology-specific needs.
     *
     * @param topology The deployment topology (JENKINS_SERVICE, S3_WEBSITE, etc.)
     * @param runtime The runtime type (EC2, FARGATE)
     * @param networkMode The network mode (public-no-nat, private-with-nat)
     * @return The number of NAT gateways to create (0, 1, or 2)
     */
    // codeql[java/unused-parameter] -- topology/runtime go unused by every current implementation
    // (Dev/Staging/Production all key only on networkMode); kept as a deliberate extensibility
    // point per this method's own javadoc, not trimmed just because nothing needs it yet.
    int getNatGatewayCount(TopologyType topology, RuntimeType runtime, NetworkMode networkMode);

    /**
     * Whether WAF should be enabled for web application protection.
     */
    boolean isWafEnabled();

    /**
     * Whether HTTPS-only mode should be enforced (no HTTP listener).
     *
     * <p>When enabled with SSL, the ALB will only listen on port 443 (HTTPS).
     * No HTTP listener on port 80 will be created, meaning users must explicitly
     * use https:// in their URLs. This provides stricter security by eliminating
     * any unencrypted traffic path.</p>
     *
     * <p>This is required by PCI-DSS and NIST for strict TLS enforcement.
     * When disabled (default), HTTP requests are redirected to HTTPS.</p>
     *
     * @return true if HTTPS-only mode should be enforced
     */
    boolean isHttpsStrictEnabled();

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

    /**
     * Whether backup vault lock should be enabled.
     *
     * <p>Vault lock prevents backups from being deleted or modified for a
     * specified retention period, ensuring immutability of backup data.</p>
     *
     * <p>Required for:</p>
     * <ul>
     *   <li>PCI-DSS - Immutable backup retention</li>
     *   <li>HIPAA - Data integrity and retention requirements</li>
     * </ul>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true when PCI-DSS or HIPAA compliance is required</li>
     * </ul>
     *
     * @return true if backup vault lock should be enabled
     */
    boolean isBackupVaultLockEnabled();

    /**
     * Whether backup vault should be retained on stack deletion.
     *
     * <p>When enabled, the backup vault and its backups are retained even
     * after the CloudFormation stack is deleted, ensuring compliance with
     * data retention policies.</p>
     *
     * <ul>
     *   <li>DEV: false - Allow cleanup for development</li>
     *   <li>STAGING: false - Allow cleanup for staging</li>
     *   <li>PRODUCTION: true when compliance frameworks are enabled</li>
     * </ul>
     *
     * @return true if backup vault should be retained
     */
    boolean isBackupVaultRetentionEnabled();

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

    /**
     * Whether RDS deletion protection remediation should be enabled.
     * Automatically enables deletion protection on RDS instances.
     */
    boolean isRdsDeletionProtectionRemediationEnabled();

    /**
     * Whether RDS deletion protection should be enabled.
     *
     * <p>Deletion protection prevents accidental deletion of RDS instances.
     * Required for production deployments with compliance frameworks (PCI-DSS, HIPAA, SOC2, GDPR).</p>
     *
     * <ul>
     *   <li>DEV: false - Allow easy cleanup during development</li>
     *   <li>STAGING: false - Allow cleanup of staging environments</li>
     *   <li>PRODUCTION: true when compliance frameworks are enabled</li>
     * </ul>
     *
     * @return true if deletion protection should be enabled
     */
    boolean isRdsDeletionProtectionEnabled();

    /**
     * Whether RDS database Multi-AZ deployment should be enabled.
     *
     * <p>Multi-AZ provides high availability and automatic failover for RDS instances.
     * Required for production deployments with compliance frameworks (PCI-DSS, HIPAA, SOC2, GDPR, NIST).</p>
     *
     * <p>Required for:</p>
     * <ul>
     *   <li>PCI-DSS - Req 12.10.4: Critical system availability</li>
     *   <li>HIPAA - §164.308(a)(7)(ii)(B): Disaster recovery</li>
     *   <li>SOC2 - A1.2: System availability</li>
     *   <li>GDPR - Art. 32(1)(b): System resilience</li>
     *   <li>NIST - CP-6: Alternate Storage Site</li>
     * </ul>
     *
     * <ul>
     *   <li>DEV: false - Single AZ for cost savings</li>
     *   <li>STAGING: false by default, true when compliance frameworks require it</li>
     *   <li>PRODUCTION: true when compliance frameworks are enabled</li>
     * </ul>
     *
     * @return true if RDS Multi-AZ should be enabled
     */
    boolean isRdsDatabaseMultiAzEnabled();

    /**
     * Whether an OPTIONAL {@link com.cloudforge.core.interfaces.DatabaseSpec} application must
     * have an explicit {@code provisionDatabase} choice in its deployment context, rather than
     * being allowed to silently fall through to that application's own embedded-storage default
     * (whatever it degrades to when {@code provisionDatabase} is never set — {@code cloudforge-manager}
     * falls back to an embedded H2 file, for example).
     *
     * <p>This isn't about which value {@code provisionDatabase} resolves to — {@code
     * ApplicationFactory} still honors whatever the deployment context sets it to either way —
     * only about whether leaving it unset is itself acceptable. DEV/STAGING's whole point of
     * being OPTIONAL is that they're allowed to default to the free, no-extra-infra fallback
     * without ceremony; a PRODUCTION deployment ending up there by omission rather than a
     * deliberate choice is the problem this closes: cloudforge-manager's own PRODUCTION preset
     * deployed against embedded H2 for hours with nobody having decided that on purpose.</p>
     *
     * <ul>
     *   <li>DEV: false - the default fallback is the point</li>
     *   <li>STAGING: false - same reasoning as DEV</li>
     *   <li>PRODUCTION: true - must be set one way or the other</li>
     * </ul>
     *
     * @return true if an unset provisionDatabase should fail synthesis for this profile
     */
    boolean isDatabaseProvisioningChoiceRequired();

    /**
     * Whether Security Hub remediation should be enabled.
     * Automatically enables AWS Security Hub if not already enabled.
     * Security Hub aggregates security findings from GuardDuty, Inspector, Macie, and other services.
     */
    boolean isSecurityHubRemediationEnabled();

    /**
     * Whether Inspector remediation should be enabled.
     * Automatically enables Amazon Inspector v2 for vulnerability scanning if not already enabled.
     * Inspector continuously scans EC2, ECR, and Lambda for software vulnerabilities.
     */
    boolean isInspectorRemediationEnabled();

    /**
     * Whether Macie remediation should be enabled.
     * Automatically enables Amazon Macie for sensitive data discovery if not already enabled.
     * WARNING: Has cost implications - charges per GB of data scanned.
     */
    boolean isMacieRemediationEnabled();

    /**
     * Whether ECR image scanning remediation should be enabled.
     * Automatically enables scan-on-push for ECR repositories if not already enabled.
     * Scans container images for vulnerabilities before they can be deployed.
     */
    boolean isEcrImageScanningRemediationEnabled();

    // ==================== Authentication Configuration ====================

    /**
     * Whether MFA (Multi-Factor Authentication) is required for user authentication.
     *
     * <p>MFA provides an additional layer of security by requiring users to provide
     * a second form of verification beyond their password.</p>
     *
     * <ul>
     *   <li>DEV: false - MFA optional for development convenience</li>
     *   <li>STAGING: true - MFA required to test production-like security</li>
     *   <li>PRODUCTION: true - MFA required for compliance (PCI-DSS, HIPAA, SOC 2)</li>
     * </ul>
     *
     * @return true if MFA should be required
     */
    boolean isMfaRequired();

    /**
     * Get the default MFA method for the security profile.
     *
     * <p>Available methods:</p>
     * <ul>
     *   <li>"totp" - Time-based One-Time Password (authenticator apps)</li>
     *   <li>"sms" - SMS text message codes</li>
     *   <li>"both" - Users can choose their preferred method</li>
     * </ul>
     *
     * <ul>
     *   <li>DEV: "totp" - Simple authenticator app</li>
     *   <li>STAGING: "both" - Test all MFA methods</li>
     *   <li>PRODUCTION: "both" - Maximum flexibility for users</li>
     * </ul>
     *
     * @return MFA method: "totp", "sms", or "both"
     */
    String getDefaultMfaMethod();

    /**
     * Get the OAuth 2.0 access token validity duration in hours.
     *
     * <p>Shorter durations are more secure but require more frequent re-authentication.</p>
     *
     * <ul>
     *   <li>DEV: 8 hours - Full workday without re-auth</li>
     *   <li>STAGING: 2 hours - Balance security and convenience</li>
     *   <li>PRODUCTION: 1 hour - Strict security, comply with PCI-DSS requirements</li>
     * </ul>
     *
     * @return Access token validity in hours
     */
    int getAccessTokenValidityHours();

    /**
     * Get the OAuth 2.0 ID token validity duration in hours.
     *
     * <p>ID tokens contain user identity information and should have limited lifetime.</p>
     *
     * <ul>
     *   <li>DEV: 8 hours - Match access token for simplicity</li>
     *   <li>STAGING: 2 hours - Balance security and convenience</li>
     *   <li>PRODUCTION: 1 hour - Minimize exposure window</li>
     * </ul>
     *
     * @return ID token validity in hours
     */
    int getIdTokenValidityHours();

    /**
     * Get the OAuth 2.0 refresh token validity duration in days.
     *
     * <p>Refresh tokens allow obtaining new access tokens without re-authentication.
     * Longer durations improve UX but increase risk if token is compromised.</p>
     *
     * <ul>
     *   <li>DEV: 30 days - Long-lived for development convenience</li>
     *   <li>STAGING: 7 days - Weekly re-authentication</li>
     *   <li>PRODUCTION: 1 day - Daily re-authentication for maximum security</li>
     * </ul>
     *
     * @return Refresh token validity in days
     */
    int getRefreshTokenValidityDays();

    /**
     * Get the minimum password length required for user accounts.
     *
     * <p>Longer passwords provide better security against brute-force attacks.</p>
     *
     * <ul>
     *   <li>DEV: 8 - Minimum acceptable for testing</li>
     *   <li>STAGING: 12 - Production-like requirements</li>
     *   <li>PRODUCTION: 14 - Strong password policy (NIST 800-63B compliant)</li>
     * </ul>
     *
     * @return Minimum password length
     */
    int getMinimumPasswordLength();

    /**
     * Get the temporary password validity duration in days.
     *
     * <p>Temporary passwords are issued to new users and must be changed on first login.
     * Shorter durations reduce the window for password interception.</p>
     *
     * <ul>
     *   <li>DEV: 7 days - Flexible for testing</li>
     *   <li>STAGING: 3 days - Production-like urgency</li>
     *   <li>PRODUCTION: 1 day - Immediate action required</li>
     * </ul>
     *
     * @return Temporary password validity in days
     */
    int getTempPasswordValidityDays();

    /**
     * Whether self-service user registration is allowed.
     *
     * <p>Self-signup allows users to create their own accounts without admin intervention.
     * This should be disabled in production for controlled access.</p>
     *
     * <ul>
     *   <li>DEV: true - Allow easy account creation for testing</li>
     *   <li>STAGING: false - Admin-controlled access like production</li>
     *   <li>PRODUCTION: false - Strict access control, admins create accounts</li>
     * </ul>
     *
     * @return true if self-service signup is allowed
     */
    boolean isSelfSignupEnabled();

    /**
     * Whether to prevent user existence errors in authentication responses.
     *
     * <p>When enabled, authentication errors don't reveal whether a username exists.
     * This prevents username enumeration attacks but makes debugging harder.</p>
     *
     * <ul>
     *   <li>DEV: false - Helpful error messages for debugging</li>
     *   <li>STAGING: true - Test production security behavior</li>
     *   <li>PRODUCTION: true - Prevent username enumeration</li>
     * </ul>
     *
     * @return true if user existence errors should be prevented
     */
    boolean isPreventUserExistenceErrorsEnabled();

    /**
     * Whether advanced security features (risk-based authentication) should be enabled.
     *
     * <p>Advanced security includes adaptive authentication that analyzes login patterns
     * and can block suspicious activity. Requires Cognito Plus tier.</p>
     *
     * <ul>
     *   <li>DEV: false - Not needed for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true - Recommended for threat detection (requires Plus tier)</li>
     * </ul>
     *
     * @return true if advanced security features should be enabled
     */
    boolean isAdvancedSecurityEnabled();

    // ==================== Advanced Monitoring & Threat Detection ====================

    /**
     * Whether Amazon Macie should be enabled for sensitive data discovery.
     *
     * <p>Macie uses machine learning to automatically discover, classify, and protect
     * sensitive data like PII and PHI in S3 buckets.</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true - Required for HIPAA/GDPR compliance</li>
     * </ul>
     *
     * @return true if Macie should be enabled
     */
    boolean isMacieEnabled();

    /**
     * Whether Macie automated discovery jobs should be enabled.
     *
     * <p>Automated discovery continuously scans S3 buckets for sensitive data.
     * Only applicable when Macie is enabled.</p>
     *
     * <ul>
     *   <li>DEV: false - Not applicable</li>
     *   <li>STAGING: false - Manual discovery preferred</li>
     *   <li>PRODUCTION: true - Continuous monitoring required for compliance</li>
     * </ul>
     *
     * @return true if automated discovery should be enabled
     */
    boolean isMacieAutomatedDiscoveryEnabled();

    /**
     * Whether AWS Security Hub should be enabled for centralized security findings.
     *
     * <p>Security Hub aggregates security findings from multiple AWS services
     * (GuardDuty, Inspector, Macie, etc.) and provides compliance checks.</p>
     *
     * <ul>
     *   <li>DEV: false - Not needed for development</li>
     *   <li>STAGING: true - Test security monitoring</li>
     *   <li>PRODUCTION: true - Centralized security monitoring</li>
     * </ul>
     *
     * @return true if Security Hub should be enabled
     */
    boolean isSecurityHubEnabled();

    /**
     * Whether Amazon Inspector should be enabled for vulnerability scanning.
     *
     * <p>Inspector automatically discovers workloads and continuously scans
     * for software vulnerabilities and network exposure.</p>
     *
     * <ul>
     *   <li>DEV: false - Not needed for development</li>
     *   <li>STAGING: true - Test vulnerability scanning</li>
     *   <li>PRODUCTION: true - Required for PCI-DSS and security best practices</li>
     * </ul>
     *
     * @return true if Inspector should be enabled
     */
    boolean isInspectorEnabled();

    /**
     * Whether anti-malware protection should be enabled on EC2 instances.
     *
     * <p>Deploys and configures anti-malware software on EC2 instances.
     * Only applicable for EC2 runtime (not Fargate).</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true (EC2 only) - Required for PCI-DSS Req 5.1</li>
     * </ul>
     *
     * @return true if anti-malware should be enabled
     */
    boolean isAntiMalwareEnabled();

    /**
     * Whether file integrity monitoring should be enabled on EC2 instances.
     *
     * <p>Monitors critical system files for unauthorized changes.
     * Only applicable for EC2 runtime (not Fargate).</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true (EC2 only) - Required for PCI-DSS Req 11.5</li>
     * </ul>
     *
     * @return true if file integrity monitoring should be enabled
     */
    boolean isFileIntegrityMonitoringEnabled();

    /**
     * Whether container runtime security monitoring should be enabled.
     *
     * <p>Monitors container behavior at runtime for suspicious activity.
     * Only applicable for containerized workloads (Fargate, ECS, EKS).</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true (Fargate/ECS only) - Security best practice</li>
     * </ul>
     *
     * @return true if container runtime security should be enabled
     */
    boolean isContainerRuntimeSecurityEnabled();

    /**
     * Whether container image scanning should be enabled.
     *
     * <p>Scans container images for vulnerabilities before deployment.
     * Typically handled by ECR image scanning.</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: true - Test image scanning pipeline</li>
     *   <li>PRODUCTION: true - Required for secure container deployments</li>
     * </ul>
     *
     * @return true if container image scanning should be enabled
     */
    boolean isContainerImageScanningEnabled();

    // ==================== Enhanced Compliance Controls ====================

    /**
     * Whether CloudWatch Logs should be encrypted with KMS.
     *
     * <p>KMS encryption provides customer-managed encryption keys for CloudWatch
     * Logs, ensuring audit logs are protected at rest with customer-controlled keys.</p>
     *
     * <ul>
     *   <li>DEV: false - Standard CloudWatch encryption is sufficient</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true when compliance frameworks require it (PCI-DSS, HIPAA, SOC2)</li>
     * </ul>
     *
     * @return true if CloudWatch Logs should use KMS encryption
     */
    boolean isCloudWatchLogsKmsEncryptionEnabled();

    /**
     * Whether CloudTrail Insights should be enabled for anomaly detection.
     *
     * <p>CloudTrail Insights analyzes API activity and detects unusual patterns
     * that may indicate security incidents or operational issues.</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true when compliance frameworks require it (SOC2, NIST)</li>
     * </ul>
     *
     * @return true if CloudTrail Insights should be enabled
     */
    boolean isCloudTrailInsightsEnabled();

    /**
     * Whether Route53 DNS query logging should be enabled.
     *
     * <p>DNS query logging captures all DNS queries made to Route53 hosted zones,
     * providing network visibility for security monitoring and forensics.</p>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true when compliance frameworks require it (SOC2, NIST)</li>
     * </ul>
     *
     * @return true if Route53 query logging should be enabled
     */
    boolean isRoute53QueryLoggingEnabled();

    /**
     * Whether S3 Object Lock should be enabled for compliance audit buckets.
     *
     * <p>S3 Object Lock prevents objects from being deleted or overwritten for a
     * specified retention period, ensuring immutability of audit trails.</p>
     *
     * <p>Required for:</p>
     * <ul>
     *   <li>HIPAA § 164.312(c)(1) - Data integrity controls</li>
     *   <li>PCI-DSS Req 10.7 - Audit log retention</li>
     *   <li>SEC 17a-4 - Record retention for financial services</li>
     * </ul>
     *
     * <ul>
     *   <li>DEV: false - Not required for development</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true when HIPAA or PCI-DSS compliance is required</li>
     * </ul>
     *
     * @return true if S3 Object Lock should be enabled
     */
    boolean isS3ObjectLockEnabled();

    /**
     * Whether SNS topics should be encrypted with KMS.
     *
     * <p>KMS encryption provides customer-managed encryption keys for SNS topics,
     * ensuring messages at rest are protected with customer-controlled keys.</p>
     *
     * <p>Required for:</p>
     * <ul>
     *   <li>HIPAA § 164.312(a)(2)(iv) - Encryption of ePHI</li>
     *   <li>HIPAA § 164.312(e)(2)(ii) - Encryption mechanism</li>
     *   <li>PCI-DSS Req 8.2.1 - Data at rest encryption</li>
     * </ul>
     *
     * <ul>
     *   <li>DEV: false - Standard SNS encryption is sufficient</li>
     *   <li>STAGING: false - Optional for testing</li>
     *   <li>PRODUCTION: true when HIPAA or PCI-DSS compliance is required</li>
     * </ul>
     *
     * @return true if SNS topics should use KMS encryption
     */
    boolean isSnsKmsEncryptionEnabled();

    /**
     * Whether EC2 instances must use IMDSv2 (Instance Metadata Service Version 2).
     *
     * <p>IMDSv2 uses session-based tokens and provides better protection against
     * SSRF attacks and unauthorized access to instance metadata.</p>
     *
     * <p>Required for:</p>
     * <ul>
     *   <li>HIPAA § 164.308(a)(3)(i) - Access controls</li>
     *   <li>HIPAA § 164.308(a)(4)(ii)(A) - Access authorization</li>
     *   <li>HIPAA § 164.312(a)(1) - Access control</li>
     *   <li>PCI-DSS - Defense in depth</li>
     * </ul>
     *
     * <ul>
     *   <li>DEV: false - IMDSv1 allowed for development convenience</li>
     *   <li>STAGING: true - Test production security behavior</li>
     *   <li>PRODUCTION: true - Required for HIPAA compliance</li>
     * </ul>
     *
     * @return true if IMDSv2 should be required
     */
    boolean isImdsv2Required();
}
