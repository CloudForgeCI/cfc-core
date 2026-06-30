package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.enums.SecurityProfile;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.s3.LifecycleRule;

import java.util.List;
import java.util.Map;

/**
 * Configuration for CMS media storage on S3.
 *
 * <p>Creates S3 buckets with appropriate policies for CMS media uploads,
 * enabling scalable media storage with CDN integration.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Private bucket with block public access</li>
 *   <li>Versioning for recovery</li>
 *   <li>Lifecycle rules for old version cleanup</li>
 *   <li>CORS configuration for browser uploads</li>
 *   <li>IAM policies for CMS access</li>
 *   <li>Encryption based on security profile</li>
 * </ul>
 *
 * @since 3.1.0
 */
public final class CmsMediaStorageConfiguration {

    private CmsMediaStorageConfiguration() {
        // Utility class
    }

    /**
     * Create S3 bucket for CMS media storage.
     *
     * @param ctx the SystemContext
     * @param spec the CMS specification
     * @return the created S3 bucket
     */
    public static Bucket createMediaBucket(SystemContext ctx, CmsSpec spec) {
        // NO bucketName specified - CloudFormation auto-generates unique name
        // This prevents "AlreadyExists" errors when buckets are retained from previous deployments
        BucketEncryption encryption = determineEncryption(ctx);

        return Bucket.Builder.create(ctx, spec.applicationId().toLowerCase() + "-media")
            // NO bucketName specified - CloudFormation auto-generates unique name
            .encryption(encryption)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
            .accessControl(BucketAccessControl.PRIVATE)
            .versioned(true)
            .removalPolicy(determineRemovalPolicy(ctx))
            .lifecycleRules(createLifecycleRules())
            .cors(createCorsRules(ctx))
            .build();
    }

    /**
     * Create S3 bucket with custom configuration.
     *
     * @param ctx the SystemContext
     * @param spec the CMS specification
     * @param enableVersioning whether to enable versioning
     * @param enableTransferAcceleration whether to enable transfer acceleration
     * @return the created S3 bucket
     */
    public static Bucket createMediaBucket(
            SystemContext ctx,
            CmsSpec spec,
            boolean enableVersioning,
            boolean enableTransferAcceleration) {

        // NO bucketName specified - CloudFormation auto-generates unique name
        // This prevents "AlreadyExists" errors when buckets are retained from previous deployments
        BucketEncryption encryption = determineEncryption(ctx);

        var builder = Bucket.Builder.create(ctx, spec.applicationId().toLowerCase() + "-media")
            // NO bucketName specified - CloudFormation auto-generates unique name
            .encryption(encryption)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
            .accessControl(BucketAccessControl.PRIVATE)
            .versioned(enableVersioning)
            .removalPolicy(determineRemovalPolicy(ctx))
            .cors(createCorsRules(ctx));

        if (enableVersioning) {
            builder.lifecycleRules(createLifecycleRules());
        }

        if (enableTransferAcceleration) {
            builder.transferAcceleration(true);
        }

        return builder.build();
    }

    /**
     * Determine encryption based on security profile.
     *
     * @param ctx the SystemContext
     * @return bucket encryption type
     */
    private static BucketEncryption determineEncryption(SystemContext ctx) {
        SecurityProfile profile = ctx.cfc.securityProfile();
        if (profile == SecurityProfile.PRODUCTION) {
            return BucketEncryption.KMS_MANAGED;
        }
        return BucketEncryption.S3_MANAGED;
    }

    /**
     * Determine removal policy based on security profile.
     *
     * @param ctx the SystemContext
     * @return removal policy
     */
    private static RemovalPolicy determineRemovalPolicy(SystemContext ctx) {
        SecurityProfile profile = ctx.cfc.securityProfile();
        if (profile == SecurityProfile.PRODUCTION) {
            return RemovalPolicy.RETAIN;
        }
        return RemovalPolicy.DESTROY;
    }

    /**
     * Create lifecycle rules for version cleanup.
     *
     * @return list of lifecycle rules
     */
    private static List<LifecycleRule> createLifecycleRules() {
        return List.of(
            // Delete old versions after 30 days
            LifecycleRule.builder()
                .id("DeleteOldVersions")
                .enabled(true)
                .noncurrentVersionExpiration(Duration.days(30))
                .build(),

            // Transition old versions to cheaper storage after 7 days
            LifecycleRule.builder()
                .id("TransitionOldVersions")
                .enabled(true)
                .noncurrentVersionTransitions(List.of(
                    software.amazon.awscdk.services.s3.NoncurrentVersionTransition.builder()
                        .storageClass(software.amazon.awscdk.services.s3.StorageClass.GLACIER_INSTANT_RETRIEVAL)
                        .transitionAfter(Duration.days(7))
                        .build()
                ))
                .build(),

            // Clean up incomplete multipart uploads
            LifecycleRule.builder()
                .id("AbortIncompleteMultipartUpload")
                .enabled(true)
                .abortIncompleteMultipartUploadAfter(Duration.days(7))
                .build()
        );
    }

    /**
     * Create CORS rules for browser uploads.
     *
     * @param ctx the SystemContext
     * @return list of CORS rules
     */
    private static List<CorsRule> createCorsRules(SystemContext ctx) {
        // Determine allowed origins
        List<String> allowedOrigins;
        if (ctx.cfc.fqdn() != null && !ctx.cfc.fqdn().isBlank()) {
            allowedOrigins = List.of(
                "https://" + ctx.cfc.fqdn(),
                "http://" + ctx.cfc.fqdn()  // For development
            );
        } else {
            // Allow all origins in development
            allowedOrigins = List.of("*");
        }

        return List.of(
            CorsRule.builder()
                .allowedMethods(List.of(
                    HttpMethods.GET,
                    HttpMethods.PUT,
                    HttpMethods.POST,
                    HttpMethods.DELETE,
                    HttpMethods.HEAD
                ))
                .allowedOrigins(allowedOrigins)
                .allowedHeaders(List.of("*"))
                .exposedHeaders(List.of(
                    "ETag",
                    "x-amz-meta-custom-header"
                ))
                .maxAge(3600)
                .build()
        );
    }

    /**
     * Create IAM policy for CMS to access media bucket.
     *
     * @param bucket the S3 bucket
     * @return IAM policy statement
     */
    public static PolicyStatement createMediaBucketPolicy(Bucket bucket) {
        return PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(List.of(
                "s3:GetObject",
                "s3:GetObjectVersion",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:DeleteObjectVersion",
                "s3:ListBucket",
                "s3:GetBucketLocation"
            ))
            .resources(List.of(
                bucket.getBucketArn(),
                bucket.getBucketArn() + "/*"
            ))
            .build();
    }

    /**
     * Create read-only IAM policy for media bucket.
     *
     * @param bucket the S3 bucket
     * @return IAM policy statement for read-only access
     */
    public static PolicyStatement createReadOnlyPolicy(Bucket bucket) {
        return PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(List.of(
                "s3:GetObject",
                "s3:GetObjectVersion",
                "s3:ListBucket"
            ))
            .resources(List.of(
                bucket.getBucketArn(),
                bucket.getBucketArn() + "/*"
            ))
            .build();
    }

    /**
     * Create environment variables for S3 media integration.
     *
     * @param bucket the S3 bucket
     * @param spec the CMS specification
     * @param cdnUrl optional CloudFront URL
     * @return map of environment variables
     */
    public static Map<String, String> createS3Environment(
            Bucket bucket,
            CmsSpec spec,
            String cdnUrl) {

        var env = new java.util.HashMap<String, String>();

        // Common S3 variables
        env.put("S3_MEDIA_BUCKET", bucket.getBucketName());
        env.put("S3_MEDIA_REGION", bucket.getEnv().getRegion());

        // CMS-specific variables
        String cmsId = spec.applicationId();
        switch (cmsId) {
            case "wordpress", "woocommerce" -> {
                env.put("WP_OFFLOAD_MEDIA_BUCKET", bucket.getBucketName());
                env.put("WP_OFFLOAD_MEDIA_REGION", bucket.getEnv().getRegion());
                if (cdnUrl != null) {
                    env.put("WP_OFFLOAD_MEDIA_CLOUDFRONT", cdnUrl);
                }
            }
            case "magento" -> {
                env.put("MAGENTO_MEDIA_STORAGE", "s3");
                env.put("MAGENTO_MEDIA_S3_BUCKET", bucket.getBucketName());
                env.put("MAGENTO_MEDIA_S3_REGION", bucket.getEnv().getRegion());
            }
            case "drupal" -> {
                env.put("S3FS_BUCKET", bucket.getBucketName());
                env.put("S3FS_REGION", bucket.getEnv().getRegion());
                env.put("S3FS_USE_CNAME", cdnUrl != null ? "true" : "false");
                if (cdnUrl != null) {
                    env.put("S3FS_DOMAIN", cdnUrl);
                }
            }
            case "joomla" -> {
                env.put("JOOMLA_S3_BUCKET", bucket.getBucketName());
                env.put("JOOMLA_S3_REGION", bucket.getEnv().getRegion());
            }
            case "prestashop" -> {
                env.put("PS_AWS_S3_BUCKET", bucket.getBucketName());
                env.put("PS_AWS_S3_REGION", bucket.getEnv().getRegion());
            }
            default -> {
                // Generic variables already set
            }
        }

        // CDN URL if provided
        if (cdnUrl != null && !cdnUrl.isEmpty()) {
            env.put("MEDIA_CDN_URL", cdnUrl);
        }

        return env;
    }

    /**
     * Create bucket policy for CloudFront OAI/OAC access.
     *
     * @param bucket the S3 bucket
     * @param cloudFrontOaiArn CloudFront Origin Access Identity ARN
     * @return policy statement for CloudFront access
     */
    public static PolicyStatement createCloudFrontAccessPolicy(
            Bucket bucket,
            String cloudFrontOaiArn) {

        return PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .principals(List.of(
                new software.amazon.awscdk.services.iam.ArnPrincipal(cloudFrontOaiArn)
            ))
            .actions(List.of("s3:GetObject"))
            .resources(List.of(bucket.getBucketArn() + "/*"))
            .build();
    }
}
