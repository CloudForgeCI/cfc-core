package com.cloudforge.core.interfaces;

import java.util.Map;

/**
 * Media storage configuration for CMS platforms.
 *
 * <p>Defines S3 offloading capabilities and plugin configurations for
 * CMS media uploads. When enabled, media files are stored in S3 instead
 * of the local filesystem, enabling:</p>
 * <ul>
 *   <li>Horizontal scaling (multiple container instances)</li>
 *   <li>CDN integration via CloudFront</li>
 *   <li>Reduced EFS/EBS storage costs</li>
 *   <li>Better performance through S3's distributed storage</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * if (spec instanceof CmsMediaStorageSpec mediaSpec) {
 *     if (mediaSpec.isS3OffloadingEnabled()) {
 *         String bucket = mediaSpec.getMediaBucketName();
 *         Map<String, String> env = mediaSpec.getS3PluginEnvironment();
 *     }
 * }
 * }</pre>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
public interface CmsMediaStorageSpec {

    /**
     * Returns whether S3 media offloading is enabled.
     *
     * <p>When enabled, media uploads are stored in S3 instead of
     * local filesystem. This requires proper IAM permissions and
     * plugin configuration.</p>
     *
     * @return true if S3 offloading is enabled
     */
    boolean isS3OffloadingEnabled();

    /**
     * Returns the S3 bucket name for media storage.
     *
     * <p>This bucket should be provisioned with appropriate policies:</p>
     * <ul>
     *   <li>Block public access enabled</li>
     *   <li>Versioning enabled for recovery</li>
     *   <li>Lifecycle rules for old version cleanup</li>
     *   <li>CORS configuration for browser uploads</li>
     * </ul>
     *
     * @return S3 bucket name for media storage
     */
    String getMediaBucketName();

    /**
     * Returns the S3 key prefix for media files.
     *
     * <p>Organizes media files within the bucket. Common patterns:</p>
     * <ul>
     *   <li>"uploads/" - WordPress standard</li>
     *   <li>"media/" - Generic pattern</li>
     *   <li>"pub/media/" - Magento pattern</li>
     * </ul>
     *
     * @return S3 key prefix (default: "uploads/")
     */
    default String getMediaKeyPrefix() {
        return "uploads/";
    }

    /**
     * Returns the S3 region for the media bucket.
     *
     * <p>Should match the deployment region for optimal performance.</p>
     *
     * @return AWS region (e.g., "us-east-1")
     */
    String getMediaBucketRegion();

    /**
     * Returns environment variables for S3 plugin configuration.
     *
     * <p>CMS-specific environment variables for configuring S3 media plugins:</p>
     *
     * <p><b>WordPress (WP Offload Media):</b></p>
     * <ul>
     *   <li>AS3CF_SETTINGS - JSON configuration</li>
     *   <li>WP_OFFLOAD_MEDIA_BUCKET - Bucket name</li>
     *   <li>WP_OFFLOAD_MEDIA_REGION - AWS region</li>
     * </ul>
     *
     * <p><b>Magento:</b></p>
     * <ul>
     *   <li>AWS_S3_BUCKET - Bucket name</li>
     *   <li>AWS_S3_REGION - AWS region</li>
     *   <li>AWS_S3_PREFIX - Key prefix</li>
     * </ul>
     *
     * <p><b>Drupal (S3FS):</b></p>
     * <ul>
     *   <li>S3FS_BUCKET - Bucket name</li>
     *   <li>S3FS_REGION - AWS region</li>
     * </ul>
     *
     * @return map of environment variable key-value pairs
     */
    Map<String, String> getS3PluginEnvironment();

    /**
     * Returns whether to delete local files after S3 upload.
     *
     * <p>When true, files are removed from local storage after
     * successful S3 upload. This saves local storage but requires
     * reliable S3 connectivity.</p>
     *
     * <p>Recommended settings:</p>
     * <ul>
     *   <li>Fargate: true (ephemeral storage)</li>
     *   <li>EC2 with EFS: true (saves EFS costs)</li>
     *   <li>EC2 with EBS: false (keep local backup)</li>
     * </ul>
     *
     * @return true to delete local files after upload (default: false)
     */
    default boolean deleteLocalAfterUpload() {
        return false;
    }

    /**
     * Returns the CloudFront URL for media if CDN is enabled.
     *
     * <p>When a CloudFront distribution is configured for the media bucket,
     * this URL is used for serving media files to visitors.</p>
     *
     * @return CloudFront URL (e.g., "https://d1234.cloudfront.net") or null
     */
    String getCdnMediaUrl();

    /**
     * Returns whether to rewrite URLs to use CDN.
     *
     * <p>When enabled, media URLs in content are rewritten to use
     * the CDN domain instead of S3 or local URLs.</p>
     *
     * @return true to rewrite URLs (default: true if CDN URL is set)
     */
    default boolean rewriteUrlsForCdn() {
        return getCdnMediaUrl() != null && !getCdnMediaUrl().isEmpty();
    }

    /**
     * Returns the allowed MIME types for media uploads.
     *
     * <p>Used for S3 bucket policy and upload validation.
     * Empty list means all types are allowed.</p>
     *
     * @return list of allowed MIME types, or empty for all
     */
    default java.util.List<String> allowedMimeTypes() {
        return java.util.List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "video/mp4",
            "video/webm",
            "audio/mpeg",
            "audio/ogg",
            "application/pdf",
            "application/zip"
        );
    }

    /**
     * Returns the maximum file size for uploads in megabytes.
     *
     * <p>Used for S3 bucket policy and CMS configuration.</p>
     *
     * @return max file size in MB (default: 128MB)
     */
    default int maxUploadSizeMb() {
        return 128;
    }

    /**
     * Returns whether to enable S3 Transfer Acceleration.
     *
     * <p>Transfer Acceleration uses CloudFront edge locations for
     * faster uploads, useful for global teams.</p>
     *
     * @return true to enable Transfer Acceleration (default: false)
     */
    default boolean enableTransferAcceleration() {
        return false;
    }

    /**
     * Returns the storage class for media files.
     *
     * <p>S3 storage classes:</p>
     * <ul>
     *   <li>STANDARD - Frequently accessed</li>
     *   <li>INTELLIGENT_TIERING - Auto-tiering</li>
     *   <li>STANDARD_IA - Infrequent access</li>
     * </ul>
     *
     * @return S3 storage class (default: "STANDARD")
     */
    default String storageClass() {
        return "STANDARD";
    }
}
