package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.interfaces.CmsSpec;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.CacheQueryStringBehavior;
import software.amazon.awscdk.services.cloudfront.CacheHeaderBehavior;
import software.amazon.awscdk.services.cloudfront.CacheCookieBehavior;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.OriginProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.PriceClass;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.IOrigin;
import software.amazon.awscdk.services.cloudfront.origins.HttpOrigin;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.Bucket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudFront CDN configuration for CMS platforms.
 *
 * <p>Configures CloudFront distributions for CMS static assets and media delivery,
 * including:</p>
 * <ul>
 *   <li>S3 origin for media files</li>
 *   <li>ALB origin for dynamic content</li>
 *   <li>Optimized cache behaviors per path</li>
 *   <li>SSL certificate integration</li>
 *   <li>Route53 alias records</li>
 * </ul>
 *
 * <h2>Cache Strategy:</h2>
 * <ul>
 *   <li><strong>Static assets</strong> (CSS, JS, images): Long TTL, cached at edge</li>
 *   <li><strong>Media uploads</strong>: Cached, served from S3</li>
 *   <li><strong>Dynamic content</strong>: No caching, forward all headers</li>
 *   <li><strong>Admin pages</strong>: No caching, bypass CDN</li>
 * </ul>
 *
 * @since 3.1.0
 */
public final class CmsCdnConfiguration {

    private CmsCdnConfiguration() {
        // Utility class
    }

    /**
     * Create CloudFront distribution for CMS with media bucket and ALB origins.
     *
     * @param ctx the SystemContext
     * @param spec the CMS specification
     * @param mediaBucket the S3 media bucket
     * @param albDnsName the ALB DNS name for dynamic content
     * @return CloudFront distribution
     */
    public static Distribution createCmsDistribution(
            SystemContext ctx,
            CmsSpec spec,
            Bucket mediaBucket,
            String albDnsName) {

        // S3 origin for media files
        IOrigin mediaOrigin = S3BucketOrigin.withOriginAccessControl(mediaBucket);

        // ALB origin for dynamic content
        HttpOrigin albOrigin = HttpOrigin.Builder.create(albDnsName)
            .protocolPolicy(OriginProtocolPolicy.HTTPS_ONLY)
            .connectionAttempts(3)
            .connectionTimeout(Duration.seconds(10))
            .readTimeout(Duration.seconds(30))
            .build();

        // Create cache policies
        CachePolicy staticAssetsCachePolicy = createStaticAssetsCachePolicy(ctx);
        CachePolicy mediaCachePolicy = createMediaCachePolicy(ctx);

        // Build behavior map based on CMS type
        Map<String, BehaviorOptions> additionalBehaviors = createCmsBehaviors(
            spec, mediaOrigin, albOrigin, staticAssetsCachePolicy, mediaCachePolicy);

        // Determine domain names
        List<String> domainNames = resolveDomainNames(ctx);

        var builder = Distribution.Builder.create(ctx, "CmsCdn")
            .defaultBehavior(BehaviorOptions.builder()
                .origin(albOrigin)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .allowedMethods(AllowedMethods.ALLOW_ALL)
                .cachePolicy(CachePolicy.CACHING_DISABLED)
                .originRequestPolicy(OriginRequestPolicy.ALL_VIEWER)
                .build())
            .additionalBehaviors(additionalBehaviors)
            .priceClass(determinePriceClass(ctx))
            .enabled(true)
            .comment(String.format("CloudForge CDN for %s", spec.displayName()));

        // Add certificate if available
        ctx.cert.get().ifPresent(builder::certificate);

        // Add domain names if available
        if (!domainNames.isEmpty()) {
            builder.domainNames(domainNames);
        }

        return builder.build();
    }

    /**
     * Create CloudFront distribution for media-only CDN (no ALB).
     *
     * @param ctx the SystemContext
     * @param spec the CMS specification
     * @param mediaBucket the S3 media bucket
     * @return CloudFront distribution for media
     */
    public static Distribution createMediaOnlyDistribution(
            SystemContext ctx,
            CmsSpec spec,
            Bucket mediaBucket) {

        IOrigin mediaOrigin = S3BucketOrigin.withOriginAccessControl(mediaBucket);
        CachePolicy mediaCachePolicy = createMediaCachePolicy(ctx);

        List<String> domainNames = resolveMediaDomainNames(ctx);

        var builder = Distribution.Builder.create(ctx, "CmsMediaCdn")
            .defaultBehavior(BehaviorOptions.builder()
                .origin(mediaOrigin)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .cachePolicy(mediaCachePolicy)
                .build())
            .priceClass(determinePriceClass(ctx))
            .enabled(true)
            .comment(String.format("CloudForge Media CDN for %s", spec.displayName()));

        ctx.cert.get().ifPresent(builder::certificate);

        if (!domainNames.isEmpty()) {
            builder.domainNames(domainNames);
        }

        return builder.build();
    }

    /**
     * Create cache policy optimized for static assets.
     *
     * @param ctx the SystemContext
     * @return cache policy for static assets
     */
    public static CachePolicy createStaticAssetsCachePolicy(SystemContext ctx) {
        return CachePolicy.Builder.create(ctx, "CmsStaticAssetsCachePolicy")
            .cachePolicyName("CloudForge-CMS-StaticAssets")
            .comment("Cache policy for CMS static assets (CSS, JS, fonts)")
            .defaultTtl(Duration.days(1))
            .maxTtl(Duration.days(365))
            .minTtl(Duration.seconds(1))
            .enableAcceptEncodingGzip(true)
            .enableAcceptEncodingBrotli(true)
            .headerBehavior(CacheHeaderBehavior.none())
            .cookieBehavior(CacheCookieBehavior.none())
            .queryStringBehavior(CacheQueryStringBehavior.none())
            .build();
    }

    /**
     * Create cache policy optimized for media files.
     *
     * @param ctx the SystemContext
     * @return cache policy for media files
     */
    public static CachePolicy createMediaCachePolicy(SystemContext ctx) {
        return CachePolicy.Builder.create(ctx, "CmsMediaCachePolicy")
            .cachePolicyName("CloudForge-CMS-Media")
            .comment("Cache policy for CMS media uploads (images, videos)")
            .defaultTtl(Duration.days(7))
            .maxTtl(Duration.days(365))
            .minTtl(Duration.hours(1))
            .enableAcceptEncodingGzip(true)
            .enableAcceptEncodingBrotli(true)
            .headerBehavior(CacheHeaderBehavior.none())
            .cookieBehavior(CacheCookieBehavior.none())
            .queryStringBehavior(CacheQueryStringBehavior.allowList("v", "ver", "version"))
            .build();
    }

    /**
     * Create CMS-specific cache behaviors driven by the spec's declared path lists.
     *
     * <p>Uses three path groups from the spec (no hardcoded CMS IDs):</p>
     * <ul>
     *   <li>{@link CmsSpec#cdnMediaPaths()} → S3 origin, media cache policy</li>
     *   <li>{@link CmsSpec#cdnStaticPaths()} → ALB origin, static cache policy (long TTL)</li>
     *   <li>{@link CmsSpec#cdnAdminPaths()} → ALB origin, caching disabled, all headers forwarded</li>
     * </ul>
     *
     * <p>If no paths are declared at all (a CMS has not overridden any of the three methods),
     * generic fallback patterns are applied so CDN still functions for unknown plugins.</p>
     *
     * @param spec         the CMS specification
     * @param mediaOrigin  S3 origin for user-uploaded media
     * @param albOrigin    ALB origin for dynamic content
     * @param staticPolicy cache policy for static assets
     * @param mediaPolicy  cache policy for media files
     * @return map of path patterns to behavior options
     */
    private static Map<String, BehaviorOptions> createCmsBehaviors(
            CmsSpec spec,
            IOrigin mediaOrigin,
            HttpOrigin albOrigin,
            CachePolicy staticPolicy,
            CachePolicy mediaPolicy) {

        Map<String, BehaviorOptions> behaviors = new HashMap<>();

        // --- S3 media origin (user uploads, product images, etc.) ---
        List<String> mediaPaths = spec.cdnMediaPaths();
        if (mediaPaths.isEmpty() && spec.supportsS3MediaStorage()) {
            // Fallback for specs that support S3 but haven't declared paths yet
            mediaPaths = List.of("/uploads/*", "/media/*");
        }
        for (String path : mediaPaths) {
            behaviors.put(path, BehaviorOptions.builder()
                .origin(mediaOrigin)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .cachePolicy(mediaPolicy)
                .build());
        }

        // --- ALB origin, long-TTL static assets (themes, JS, CSS, fonts) ---
        List<String> staticPaths = spec.cdnStaticPaths();
        if (staticPaths.isEmpty() && !spec.cdnMediaPaths().isEmpty()) {
            // Fallback generic static assets
            staticPaths = List.of("/assets/*", "/static/*");
        }
        for (String path : staticPaths) {
            // Skip if already claimed as a media path (e.g., a CMS that mixes paths)
            if (!behaviors.containsKey(path)) {
                behaviors.put(path, BehaviorOptions.builder()
                    .origin(albOrigin)
                    .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                    .cachePolicy(staticPolicy)
                    .build());
            }
        }

        // --- ALB origin, caching disabled — admin / back-office areas ---
        for (String path : spec.cdnAdminPaths()) {
            behaviors.put(path, BehaviorOptions.builder()
                .origin(albOrigin)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .allowedMethods(AllowedMethods.ALLOW_ALL)
                .cachePolicy(CachePolicy.CACHING_DISABLED)
                .originRequestPolicy(OriginRequestPolicy.ALL_VIEWER)
                .build());
        }

        return behaviors;
    }

    /**
     * Resolve domain names from context.
     *
     * @param ctx the SystemContext
     * @return list of domain names
     */
    private static List<String> resolveDomainNames(SystemContext ctx) {
        if (ctx.cfc.fqdn() != null && !ctx.cfc.fqdn().isBlank()) {
            return List.of(ctx.cfc.fqdn());
        }

        if (ctx.cfc.subdomain() != null && ctx.cfc.domain() != null) {
            return List.of(ctx.cfc.subdomain() + "." + ctx.cfc.domain());
        }

        return List.of();
    }

    /**
     * Resolve media-specific domain names.
     *
     * @param ctx the SystemContext
     * @return list of media domain names
     */
    private static List<String> resolveMediaDomainNames(SystemContext ctx) {
        String baseDomain = ctx.cfc.domain();
        if (baseDomain == null || baseDomain.isBlank()) {
            return List.of();
        }

        return List.of("media." + baseDomain);
    }

    /**
     * Determine price class based on context.
     *
     * @param ctx the SystemContext
     * @return CloudFront price class
     */
    private static PriceClass determinePriceClass(SystemContext ctx) {
        // Use all edge locations for production
        if (ctx.cfc.securityProfile() == com.cloudforge.core.enums.SecurityProfile.PRODUCTION) {
            return PriceClass.PRICE_CLASS_ALL;
        }
        // Use only US/EU/Asia for staging/dev to reduce costs
        return PriceClass.PRICE_CLASS_100;
    }

    /**
     * Create Route53 alias records for CloudFront distribution.
     *
     * @param ctx the SystemContext
     * @param distribution the CloudFront distribution
     */
    public static void createDnsRecords(SystemContext ctx, Distribution distribution) {
        ctx.zone.get().ifPresent(zone -> {
            String recordName = ctx.cfc.subdomain() != null
                ? ctx.cfc.subdomain()
                : "";

            // A record (IPv4)
            ARecord.Builder.create(ctx, "CmsCdnARecord")
                .zone(zone)
                .recordName(recordName)
                .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
                .build();

            // AAAA record (IPv6)
            AaaaRecord.Builder.create(ctx, "CmsCdnAaaaRecord")
                .zone(zone)
                .recordName(recordName)
                .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
                .build();
        });
    }
}
