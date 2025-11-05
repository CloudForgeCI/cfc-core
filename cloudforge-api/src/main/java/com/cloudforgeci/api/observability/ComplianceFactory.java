package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.cloudtrail.Trail;
import software.amazon.awscdk.services.config.ManagedRule;
import software.amazon.awscdk.services.config.ManagedRuleIdentifiers;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for creating compliance and audit resources (CloudTrail, AWS Config).
 * Creates audit logging and compliance monitoring based on security profiles.
 */
public class ComplianceFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(ComplianceFactory.class.getName());

    public ComplianceFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        LOG.info("Creating compliance resources for security profile: " + ctx.security);

        // Create CloudTrail if enabled for this security profile
        if (config.isCloudTrailEnabled()) {
            createCloudTrail();
        } else {
            LOG.info("CloudTrail disabled for security profile: " + ctx.security);
        }

        // Create AWS Config rules if enabled for this security profile
        if (config.isConfigEnabled()) {
            createConfigRules();
        } else {
            LOG.info("AWS Config disabled for security profile: " + ctx.security);
        }

        LOG.info("Compliance resources created successfully for profile: " + ctx.security);
    }

    /**
     * Create CloudTrail for audit logging.
     */
    private void createCloudTrail() {
        LOG.info("Creating CloudTrail for audit logging");

        // Create S3 bucket for CloudTrail logs
        // Use shorter bucket name to avoid S3's 63-character limit
        // Hash the stack name to keep it short but unique
        String shortId = Integer.toHexString(ctx.stackName.hashCode());
        String bucketName = "trail-" + shortId + "-" + ctx.security.name().toLowerCase();
        Bucket trailBucket = Bucket.Builder.create(this, "CloudTrailBucket")
                .bucketName(bucketName)
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        // Create CloudTrail
        // Use shorter trail name to avoid length limits
        Trail trail = Trail.Builder.create(this, "CloudTrail")
                .trailName("trail-" + shortId)
                .bucket(trailBucket)
                .sendToCloudWatchLogs(true)
                .enableFileValidation(true)
                .includeGlobalServiceEvents(true)
                .isMultiRegionTrail(true)
                .build();

        LOG.info("CloudTrail created: " + trail.getTrailArn());
    }

    /**
     * Create AWS Config rules for compliance monitoring.
     */
    private void createConfigRules() {
        LOG.info("Creating AWS Config rules for compliance monitoring");

        // Create managed Config rules based on security profile
        createEncryptionConfigRules();
        createS3ConfigRules();
        createIAMConfigRules();

        // Additional rules for production
        if (ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.PRODUCTION) {
            createProductionConfigRules();
        }

        LOG.info("AWS Config rules created successfully");
    }

    /**
     * Create encryption-related Config rules.
     */
    private void createEncryptionConfigRules() {
        // EBS encryption check
        ManagedRule.Builder.create(this, "EbsEncryptionRule")
                .identifier(ManagedRuleIdentifiers.EC2_EBS_ENCRYPTION_BY_DEFAULT)
                .description("Checks whether EBS encryption is enabled by default")
                .build();

        // S3 bucket encryption check
        ManagedRule.Builder.create(this, "S3BucketEncryptionRule")
                .identifier(ManagedRuleIdentifiers.S3_BUCKET_SERVER_SIDE_ENCRYPTION_ENABLED)
                .description("Checks that S3 buckets have server-side encryption enabled")
                .build();
    }

    /**
     * Create S3-related Config rules.
     */
    private void createS3ConfigRules() {
        // S3 bucket public access block check
        ManagedRule.Builder.create(this, "S3PublicAccessBlockRule")
                .identifier(ManagedRuleIdentifiers.S3_BUCKET_PUBLIC_READ_PROHIBITED)
                .description("Checks that S3 buckets do not allow public read access")
                .build();

        // S3 bucket versioning check
        ManagedRule.Builder.create(this, "S3VersioningRule")
                .identifier(ManagedRuleIdentifiers.S3_BUCKET_VERSIONING_ENABLED)
                .description("Checks that S3 buckets have versioning enabled")
                .build();
    }

    /**
     * Create IAM-related Config rules.
     */
    private void createIAMConfigRules() {
        // IAM password policy check
        ManagedRule.Builder.create(this, "IAMPasswordPolicyRule")
                .identifier(ManagedRuleIdentifiers.IAM_PASSWORD_POLICY)
                .description("Checks that the account password policy meets specified requirements")
                .build();

        // IAM root access key check
        ManagedRule.Builder.create(this, "IAMRootAccessKeyRule")
                .identifier(ManagedRuleIdentifiers.IAM_ROOT_ACCESS_KEY_CHECK)
                .description("Checks whether root account has access keys")
                .build();
    }

    /**
     * Create additional Config rules for production environments.
     */
    private void createProductionConfigRules() {
        // CloudTrail enabled check
        ManagedRule.Builder.create(this, "CloudTrailEnabledRule")
                .identifier(ManagedRuleIdentifiers.CLOUD_TRAIL_ENABLED)
                .description("Checks whether CloudTrail is enabled")
                .build();

        // CloudTrail log file validation check
        ManagedRule.Builder.create(this, "CloudTrailLogFileValidationRule")
                .identifier(ManagedRuleIdentifiers.CLOUD_TRAIL_LOG_FILE_VALIDATION_ENABLED)
                .description("Checks whether CloudTrail log file validation is enabled")
                .build();

        // VPC flow logs check
        ManagedRule.Builder.create(this, "VpcFlowLogsRule")
                .identifier(ManagedRuleIdentifiers.VPC_FLOW_LOGS_ENABLED)
                .description("Checks whether VPC Flow Logs are enabled")
                .build();
    }
}
