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
        ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
        "Checks that CloudWatch log groups are encrypted"),

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
        "Checks that ALB has WAF associated");

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
