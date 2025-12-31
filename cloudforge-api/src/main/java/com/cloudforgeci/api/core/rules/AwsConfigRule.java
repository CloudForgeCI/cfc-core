package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.ComplianceMode;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AWS Config managed rules mapped to ComplianceMatrix SecurityControls.
 *
 * <p>This enum provides a single source of truth for:</p>
 * <ul>
 *   <li>Which AWS Config rules exist</li>
 *   <li>Which SecurityControl each rule validates</li>
 *   <li>Whether a rule is required based on compliance frameworks</li>
 * </ul>
 *
 * <p>Usage in ConfigRulesFactory:</p>
 * <pre>{@code
 * Set<AwsConfigRule> rulesToDeploy = AwsConfigRule.getRequiredRules(frameworks, mode);
 * for (AwsConfigRule rule : rulesToDeploy) {
 *     deployConfigRule(rule.getRuleName());
 * }
 * }</pre>
 *
 * <p>Multiple frameworks requiring the same SecurityControl will only deploy
 * the Config rule once (deduplication via Set).</p>
 *
 * @see ComplianceMatrix.SecurityControl
 * @since 3.2.0
 */
public enum AwsConfigRule {

    // ==================== Threat Detection ====================
    GUARDDUTY_ENABLED("guardduty-enabled-centralized",
        ComplianceMatrix.SecurityControl.THREAT_DETECTION,
        "Checks that GuardDuty is enabled in the account"),

    // ==================== Audit Logging ====================
    CLOUDTRAIL_ENABLED("cloudtrail-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that CloudTrail is enabled"),

    CLOUDTRAIL_LOG_FILE_VALIDATION("cloud-trail-log-file-validation-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that CloudTrail log file validation is enabled"),

    MULTI_REGION_CLOUDTRAIL("multi-region-cloudtrail-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that multi-region CloudTrail is enabled"),

    VPC_FLOW_LOGS_ENABLED("vpc-flow-logs-enabled",
        ComplianceMatrix.SecurityControl.NETWORK_FLOW_LOGS,
        "Checks that VPC flow logs are enabled"),

    ELB_LOGGING_ENABLED("elb-logging-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that ELB access logging is enabled"),

    // ==================== Encryption at Rest ====================
    S3_BUCKET_ENCRYPTION("s3-bucket-server-side-encryption-enabled",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that S3 buckets have server-side encryption enabled"),

    EBS_ENCRYPTION_BY_DEFAULT("ec2-ebs-encryption-by-default",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that EBS encryption by default is enabled"),

    RDS_STORAGE_ENCRYPTED("rds-storage-encrypted",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that RDS storage encryption is enabled"),

    EFS_ENCRYPTED("efs-encrypted-check",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that EFS file systems are encrypted"),

    CLOUDWATCH_LOG_GROUP_ENCRYPTED("cloudwatch-log-group-encrypted",
        ComplianceMatrix.SecurityControl.CLOUDWATCH_LOGS_KMS_ENCRYPTION,
        "Checks that CloudWatch log groups are encrypted with KMS"),

    CLOUDTRAIL_ENCRYPTION_ENABLED("cloud-trail-encryption-enabled",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that CloudTrail is encrypted with KMS"),

    // ==================== Encryption in Transit ====================
    ALB_HTTPS_ONLY("alb-http-to-https-redirection-check",
        ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
        "Checks that ALB redirects HTTP to HTTPS"),

    ELB_TLS_HTTPS_LISTENERS("elb-tls-https-listeners-only",
        ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
        "Checks that ELB listeners use HTTPS/TLS"),

    S3_BUCKET_SSL_REQUESTS("s3-bucket-ssl-requests-only",
        ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
        "Checks that S3 buckets require SSL"),

    // ==================== Access Control ====================
    IAM_USER_GROUP_MEMBERSHIP("iam-user-group-membership-check",
        ComplianceMatrix.SecurityControl.ACCESS_CONTROL,
        "Checks that IAM users are members of at least one group"),

    IAM_NO_ADMIN_ACCESS("iam-policy-no-statements-with-admin-access",
        ComplianceMatrix.SecurityControl.ACCESS_CONTROL,
        "Checks for IAM policies with admin access"),

    // ==================== Authentication ====================
    IAM_USER_MFA_ENABLED("iam-user-mfa-enabled",
        ComplianceMatrix.SecurityControl.AUTHENTICATION,
        "Checks that MFA is enabled for IAM users"),

    IAM_PASSWORD_POLICY("iam-password-policy",
        ComplianceMatrix.SecurityControl.AUTHENTICATION,
        "Checks that IAM password policy meets requirements"),

    // ==================== Network Segmentation ====================
    EC2_INSTANCES_IN_VPC("ec2-instances-in-vpc",
        ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION,
        "Checks that EC2 instances are in a VPC"),

    VPC_DEFAULT_SG_CLOSED("vpc-default-security-group-closed",
        ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION,
        "Checks that default security group is closed"),

    RESTRICTED_SSH("restricted-ssh",
        ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION,
        "Checks that SSH is not open to 0.0.0.0/0"),

    // ==================== S3 Logging & Compliance ====================
    S3_BUCKET_LOGGING_ENABLED("s3-bucket-logging-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that S3 server access logging is enabled"),

    S3_BUCKET_VERSIONING_ENABLED("s3-bucket-versioning-enabled",
        ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
        "Checks that S3 bucket versioning is enabled"),

    S3_BUCKET_DEFAULT_LOCK_ENABLED("s3-bucket-default-lock-enabled",
        ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
        "Checks that S3 Object Lock is enabled for WORM compliance"),

    // ==================== Backup & Recovery ====================
    DB_INSTANCE_BACKUP_ENABLED("db-instance-backup-enabled",
        ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
        "Checks that RDS automated backups are enabled"),

    S3_BUCKET_REPLICATION("s3-bucket-replication-enabled",
        ComplianceMatrix.SecurityControl.BACKUP_RECOVERY,
        "Checks that S3 cross-region replication is enabled"),

    DYNAMODB_PITR_ENABLED("dynamodb-pitr-enabled",
        ComplianceMatrix.SecurityControl.DATABASE_PITR,
        "Checks that DynamoDB point-in-time recovery is enabled"),

    // ==================== High Availability ====================
    RDS_MULTI_AZ("rds-multi-az-support",
        ComplianceMatrix.SecurityControl.DATABASE_MULTI_AZ,
        "Checks that RDS instances are Multi-AZ"),

    ELB_DELETION_PROTECTION("elb-deletion-protection-enabled",
        ComplianceMatrix.SecurityControl.HIGH_AVAILABILITY,
        "Checks that ELB deletion protection is enabled"),

    // ==================== Key Management ====================
    KMS_CMK_NOT_SCHEDULED_FOR_DELETION("kms-cmk-not-scheduled-for-deletion",
        ComplianceMatrix.SecurityControl.KMS_KEY_ROTATION,
        "Checks that KMS keys are not scheduled for deletion"),

    CMK_BACKING_KEY_ROTATION("cmk-backing-key-rotation-enabled",
        ComplianceMatrix.SecurityControl.KMS_KEY_ROTATION,
        "Checks that KMS key rotation is enabled"),

    // ==================== Security Monitoring ====================
    SECURITYHUB_ENABLED("securityhub-enabled",
        ComplianceMatrix.SecurityControl.SECURITY_HUB,
        "Checks that Security Hub is enabled"),

    // ==================== Vulnerability Scanning ====================
    ECR_PRIVATE_IMAGE_SCANNING("ecr-private-image-scanning-enabled",
        ComplianceMatrix.SecurityControl.VULNERABILITY_SCANNING,
        "Checks that ECR image scanning is enabled"),

    // ==================== WAF Protection ====================
    WAFV2_LOGGING_ENABLED("wafv2-logging-enabled",
        ComplianceMatrix.SecurityControl.WAF_PROTECTION,
        "Checks that WAFv2 logging is enabled"),

    ALB_WAF_ENABLED("alb-waf-enabled",
        ComplianceMatrix.SecurityControl.WAF_PROTECTION,
        "Checks that ALB has WAF associated"),

    // ==================== Root Account Protection ====================
    ROOT_ACCOUNT_MFA_ENABLED("root-account-mfa-enabled",
        ComplianceMatrix.SecurityControl.ROOT_ACCOUNT_PROTECTION,
        "Checks that MFA is enabled for the root account"),

    ROOT_ACCOUNT_HARDWARE_MFA_ENABLED("root-account-hardware-mfa-enabled",
        ComplianceMatrix.SecurityControl.ROOT_ACCOUNT_PROTECTION,
        "Checks that hardware MFA is enabled for the root account"),

    IAM_ROOT_ACCESS_KEY_CHECK("iam-root-access-key-check",
        ComplianceMatrix.SecurityControl.ROOT_ACCOUNT_PROTECTION,
        "Checks that root user does not have access keys"),

    // ==================== Credential Rotation ====================
    ACCESS_KEYS_ROTATED("access-keys-rotated",
        ComplianceMatrix.SecurityControl.CREDENTIAL_ROTATION,
        "Checks that IAM access keys are rotated within 90 days"),

    IAM_USER_UNUSED_CREDENTIALS_CHECK("iam-user-unused-credentials-check",
        ComplianceMatrix.SecurityControl.CREDENTIAL_ROTATION,
        "Checks that IAM users do not have unused credentials"),

    MFA_ENABLED_FOR_IAM_CONSOLE_ACCESS("mfa-enabled-for-iam-console-access",
        ComplianceMatrix.SecurityControl.AUTHENTICATION,
        "Checks that MFA is enabled for IAM users with console access"),

    // ==================== Database Access Control ====================
    RDS_INSTANCE_PUBLIC_ACCESS_CHECK("rds-instance-public-access-check",
        ComplianceMatrix.SecurityControl.DATABASE_ACCESS_CONTROL,
        "Checks that RDS instances are not publicly accessible"),

    RDS_CLUSTER_PUBLIC_ACCESS_CHECK("rds-cluster-public-access-check",
        ComplianceMatrix.SecurityControl.DATABASE_ACCESS_CONTROL,
        "Checks that RDS clusters are not publicly accessible"),

    RDS_INSTANCE_IAM_AUTHENTICATION_ENABLED("rds-instance-iam-authentication-enabled",
        ComplianceMatrix.SecurityControl.DATABASE_ACCESS_CONTROL,
        "Checks that IAM authentication is enabled for RDS instances"),

    RDS_CLUSTER_IAM_AUTHENTICATION_ENABLED("rds-cluster-iam-authentication-enabled",
        ComplianceMatrix.SecurityControl.DATABASE_ACCESS_CONTROL,
        "Checks that IAM authentication is enabled for RDS clusters"),

    REDSHIFT_CLUSTER_PUBLIC_ACCESS_CHECK("redshift-cluster-public-access-check",
        ComplianceMatrix.SecurityControl.DATABASE_ACCESS_CONTROL,
        "Checks that Redshift clusters are not publicly accessible"),

    // ==================== Database Logging ====================
    RDS_LOGGING_ENABLED("rds-logging-enabled",
        ComplianceMatrix.SecurityControl.DATABASE_LOGGING,
        "Checks that RDS logging is enabled"),

    REDSHIFT_AUDIT_LOGGING_ENABLED("redshift-audit-logging-enabled",
        ComplianceMatrix.SecurityControl.DATABASE_LOGGING,
        "Checks that Redshift audit logging is enabled"),

    // ==================== Database Deletion Protection ====================
    RDS_CLUSTER_DELETION_PROTECTION_ENABLED("rds-cluster-deletion-protection-enabled",
        ComplianceMatrix.SecurityControl.DELETION_PROTECTION,
        "Checks that RDS cluster deletion protection is enabled"),

    RDS_INSTANCE_DELETION_PROTECTION_ENABLED("rds-instance-deletion-protection-enabled",
        ComplianceMatrix.SecurityControl.DELETION_PROTECTION,
        "Checks that RDS instance deletion protection is enabled"),

    // ==================== Container Security (EKS) ====================
    EKS_ENDPOINT_NO_PUBLIC_ACCESS("eks-endpoint-no-public-access",
        ComplianceMatrix.SecurityControl.CONTAINER_SECURITY,
        "Checks that EKS cluster endpoints are not publicly accessible"),

    EKS_SECRETS_ENCRYPTED("eks-secrets-encrypted",
        ComplianceMatrix.SecurityControl.CONTAINER_SECURITY,
        "Checks that EKS secrets are encrypted with KMS"),

    EKS_CLUSTER_LOGGING_ENABLED("eks-cluster-logging-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that EKS cluster logging is enabled"),

    EKS_CLUSTER_OLDEST_SUPPORTED_VERSION("eks-cluster-oldest-supported-version",
        ComplianceMatrix.SecurityControl.VULNERABILITY_MANAGEMENT,
        "Checks that EKS clusters are not running oldest supported version"),

    // ==================== API Gateway Security ====================
    API_GW_EXECUTION_LOGGING_ENABLED("api-gw-execution-logging-enabled",
        ComplianceMatrix.SecurityControl.API_SECURITY,
        "Checks that API Gateway execution logging is enabled"),

    API_GW_SSL_ENABLED("api-gw-ssl-enabled",
        ComplianceMatrix.SecurityControl.API_SECURITY,
        "Checks that API Gateway has SSL enabled"),

    API_GW_ASSOCIATED_WITH_WAF("api-gw-associated-with-waf",
        ComplianceMatrix.SecurityControl.API_SECURITY,
        "Checks that API Gateway is associated with WAF"),

    API_GW_XRAY_ENABLED("api-gw-xray-enabled",
        ComplianceMatrix.SecurityControl.SECURITY_MONITORING,
        "Checks that API Gateway X-Ray tracing is enabled"),

    // ==================== CDN Security (CloudFront) ====================
    CLOUDFRONT_VIEWER_POLICY_HTTPS("cloudfront-viewer-policy-https",
        ComplianceMatrix.SecurityControl.CDN_SECURITY,
        "Checks that CloudFront uses HTTPS viewer policy"),

    CLOUDFRONT_ASSOCIATED_WITH_WAF("cloudfront-associated-with-waf",
        ComplianceMatrix.SecurityControl.CDN_SECURITY,
        "Checks that CloudFront is associated with WAF"),

    CLOUDFRONT_ORIGIN_ACCESS_IDENTITY_ENABLED("cloudfront-origin-access-identity-enabled",
        ComplianceMatrix.SecurityControl.CDN_SECURITY,
        "Checks that CloudFront uses origin access identity for S3"),

    CLOUDFRONT_DEFAULT_ROOT_OBJECT_CONFIGURED("cloudfront-default-root-object-configured",
        ComplianceMatrix.SecurityControl.CDN_SECURITY,
        "Checks that CloudFront has default root object configured"),

    CLOUDFRONT_ACCESSLOGS_ENABLED("cloudfront-accesslogs-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that CloudFront access logging is enabled"),

    CLOUDFRONT_NO_DEPRECATED_SSL_PROTOCOLS("cloudfront-no-deprecated-ssl-protocols",
        ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
        "Checks that CloudFront does not use deprecated SSL protocols"),

    // ==================== Instance Metadata Security ====================
    EC2_IMDSV2_CHECK("ec2-imdsv2-check",
        ComplianceMatrix.SecurityControl.INSTANCE_METADATA_SECURITY,
        "Checks that EC2 instances use IMDSv2"),

    EC2_INSTANCE_PROFILE_ATTACHED("ec2-instance-profile-attached",
        ComplianceMatrix.SecurityControl.ACCESS_CONTROL,
        "Checks that EC2 instances have an IAM instance profile attached"),

    EC2_LAUNCH_TEMPLATE_PUBLIC_IP_DISABLED("ec2-launch-template-public-ip-disabled",
        ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION,
        "Checks that EC2 launch templates do not assign public IPs"),

    // ==================== Certificate Management ====================
    ACM_CERTIFICATE_EXPIRATION_CHECK("acm-certificate-expiration-check",
        ComplianceMatrix.SecurityControl.CERTIFICATE_MANAGEMENT,
        "Checks that ACM certificates are not expired or expiring soon"),

    ACM_CERTIFICATE_RSA_CHECK("acm-certificate-rsa-check",
        ComplianceMatrix.SecurityControl.CERTIFICATE_MANAGEMENT,
        "Checks that ACM certificates use RSA with adequate key length"),

    // ==================== Lambda Security ====================
    LAMBDA_FUNCTION_PUBLIC_ACCESS_PROHIBITED("lambda-function-public-access-prohibited",
        ComplianceMatrix.SecurityControl.LAMBDA_SECURITY,
        "Checks that Lambda functions are not publicly accessible"),

    LAMBDA_DLQ_CHECK("lambda-dlq-check",
        ComplianceMatrix.SecurityControl.LAMBDA_SECURITY,
        "Checks that Lambda functions have dead letter queues configured"),

    LAMBDA_INSIDE_VPC("lambda-inside-vpc",
        ComplianceMatrix.SecurityControl.LAMBDA_SECURITY,
        "Checks that Lambda functions are inside a VPC"),

    LAMBDA_FUNCTION_SETTINGS_CHECK("lambda-function-settings-check",
        ComplianceMatrix.SecurityControl.LAMBDA_SECURITY,
        "Checks Lambda function runtime and configuration settings"),

    // ==================== Redshift Security ====================
    REDSHIFT_REQUIRE_TLS_SSL("redshift-require-tls-ssl",
        ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
        "Checks that Redshift clusters require TLS/SSL"),

    REDSHIFT_CLUSTER_KMS_ENABLED("redshift-cluster-kms-enabled",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that Redshift clusters use KMS encryption"),

    // ==================== DynamoDB Security ====================
    DYNAMODB_TABLE_ENCRYPTED_KMS("dynamodb-table-encrypted-kms",
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that DynamoDB tables are encrypted with KMS"),

    DYNAMODB_AUTOSCALING_ENABLED("dynamodb-autoscaling-enabled",
        ComplianceMatrix.SecurityControl.HIGH_AVAILABILITY,
        "Checks that DynamoDB autoscaling is enabled"),

    // ==================== CodeBuild Security ====================
    CODEBUILD_PROJECT_ENVVAR_AWSCRED_CHECK("codebuild-project-envvar-awscred-check",
        ComplianceMatrix.SecurityControl.SECRETS_MANAGER,
        "Checks that CodeBuild projects do not use plaintext AWS credentials"),

    CODEBUILD_PROJECT_LOGGING_ENABLED("codebuild-project-logging-enabled",
        ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
        "Checks that CodeBuild project logging is enabled");

    private final String ruleName;
    private final ComplianceMatrix.SecurityControl securityControl;
    private final String description;

    AwsConfigRule(String ruleName, ComplianceMatrix.SecurityControl securityControl, String description) {
        this.ruleName = ruleName;
        this.securityControl = securityControl;
        this.description = description;
    }

    /**
     * Get the AWS Config rule identifier.
     */
    public String getRuleName() {
        return ruleName;
    }

    /**
     * Get the SecurityControl this rule validates.
     */
    public ComplianceMatrix.SecurityControl getSecurityControl() {
        return securityControl;
    }

    /**
     * Get a human-readable description of what this rule checks.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Check if this Config rule is required based on compliance frameworks and mode.
     *
     * @param frameworks Comma-separated list of frameworks (e.g., "PCI-DSS,HIPAA")
     * @param mode Compliance mode (ENFORCE, ADVISORY, DISABLED)
     * @return true if this rule should be deployed
     */
    public boolean isRequired(String frameworks, ComplianceMode mode) {
        return ComplianceMatrix.isControlRequired(frameworks, mode, securityControl);
    }

    /**
     * Get all Config rules required for the given compliance frameworks and mode.
     *
     * <p>This automatically deduplicates rules - if multiple frameworks require
     * the same SecurityControl, the rule is only included once.</p>
     *
     * @param frameworks Comma-separated list of frameworks (e.g., "PCI-DSS,HIPAA")
     * @param mode Compliance mode (ENFORCE, ADVISORY, DISABLED)
     * @return Set of required Config rules (no duplicates)
     */
    public static Set<AwsConfigRule> getRequiredRules(String frameworks, ComplianceMode mode) {
        return Arrays.stream(values())
            .filter(rule -> rule.isRequired(frameworks, mode))
            .collect(Collectors.toSet());
    }

    /**
     * Get all Config rules that validate a specific SecurityControl.
     *
     * @param control The SecurityControl to get rules for
     * @return Set of Config rules for this control
     */
    public static Set<AwsConfigRule> getRulesForControl(ComplianceMatrix.SecurityControl control) {
        return Arrays.stream(values())
            .filter(rule -> rule.securityControl == control)
            .collect(Collectors.toSet());
    }

    /**
     * Find a Config rule by its AWS rule name.
     *
     * @param ruleName AWS Config rule identifier
     * @return The matching AwsConfigRule, or null if not found
     */
    public static AwsConfigRule fromRuleName(String ruleName) {
        for (AwsConfigRule rule : values()) {
            if (rule.ruleName.equals(ruleName)) {
                return rule;
            }
        }
        return null;
    }
}
