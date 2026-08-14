package com.cloudforge.core.interfaces;

import com.cloudforge.core.annotation.CmsPlugin;

import java.util.List;
import java.util.Map;

/**
 * CMS/E-commerce specification interface extending ApplicationSpec.
 *
 * <p>Provides CMS-specific configuration for PHP-based content management
 * and e-commerce platforms including WordPress, Magento, Joomla, PrestaShop,
 * and Drupal.</p>
 *
 * <p>Example implementations:</p>
 * <ul>
 *   <li>WordPressApplicationSpec: PHP-based CMS and blogging platform</li>
 *   <li>WooCommerceApplicationSpec: WordPress-based e-commerce</li>
 *   <li>MagentoApplicationSpec: Adobe Commerce-compatible e-commerce platform</li>
 *   <li>JoomlaApplicationSpec: General-purpose CMS for websites</li>
 *   <li>PrestaShopApplicationSpec: Open-source e-commerce</li>
 *   <li>DrupalApplicationSpec: CMS with OIDC support</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>PHP runtime configuration (version, extensions, OPcache, PHP-FPM)</li>
 *   <li>S3 media storage offloading</li>
 *   <li>CDN integration for static assets</li>
 *   <li>Redis/Memcached object caching</li>
 *   <li>Scheduled task management (system cron vs internal)</li>
 *   <li>Multi-site/multi-store support</li>
 *   <li>OIDC authentication via plugins</li>
 * </ul>
 *
 * @since 3.1.0
 * @see ApplicationSpec
 * @see com.cloudforge.core.annotation.CmsPlugin
 */
public interface CmsSpec extends ApplicationSpec {

    // ========== PHP Runtime Configuration ==========

    /**
     * Returns the required PHP version for this CMS.
     *
     * <p>Common versions:</p>
     * <ul>
     *   <li>8.2 - WordPress, Magento 2.4.6+, Drupal 10</li>
     *   <li>8.1 - PrestaShop 8.x, Joomla 5</li>
     *   <li>7.4 - Legacy support (not recommended)</li>
     * </ul>
     *
     * @return PHP version string (e.g., "8.2", "8.1")
     */
    String phpVersion();

    /**
     * Returns required PHP extensions for this CMS.
     *
     * <p>Common extensions include:</p>
     * <ul>
     *   <li>mysqli, pdo_mysql - MySQL database connectivity</li>
     *   <li>gd, imagick - Image processing</li>
     *   <li>curl - HTTP client</li>
     *   <li>mbstring - Multibyte string handling</li>
     *   <li>xml, dom - XML processing</li>
     *   <li>zip - Archive handling</li>
     *   <li>intl - Internationalization</li>
     *   <li>opcache - Bytecode caching</li>
     *   <li>redis - Redis client</li>
     * </ul>
     *
     * @return List of PHP extension names
     */
    List<String> requiredPhpExtensions();

    /**
     * Returns PHP-FPM pool configuration overrides.
     *
     * <p>Common settings:</p>
     * <ul>
     *   <li>pm - Process manager (static, dynamic, ondemand)</li>
     *   <li>pm.max_children - Maximum worker processes</li>
     *   <li>pm.start_servers - Initial workers (dynamic mode)</li>
     *   <li>pm.min_spare_servers - Minimum idle workers</li>
     *   <li>pm.max_spare_servers - Maximum idle workers</li>
     *   <li>pm.max_requests - Requests before worker recycle</li>
     * </ul>
     *
     * @return Map of PHP-FPM configuration key-value pairs
     */
    default Map<String, String> phpFpmConfig() {
        return Map.of(
            "pm", "dynamic",
            "pm.max_children", "50",
            "pm.start_servers", "5",
            "pm.min_spare_servers", "5",
            "pm.max_spare_servers", "35",
            "pm.max_requests", "500"
        );
    }

    /**
     * Returns OPcache configuration for PHP bytecode caching.
     *
     * <p>Recommended production settings:</p>
     * <ul>
     *   <li>opcache.enable=1 - Enable OPcache</li>
     *   <li>opcache.memory_consumption=128 - Cache memory (MB)</li>
     *   <li>opcache.max_accelerated_files=10000 - Cached file limit</li>
     *   <li>opcache.revalidate_freq=60 - File check interval (seconds)</li>
     *   <li>opcache.validate_timestamps=0 - Disable for production</li>
     * </ul>
     *
     * @return Map of OPcache configuration key-value pairs
     */
    default Map<String, String> opcacheConfig() {
        return Map.of(
            "opcache.enable", "1",
            "opcache.memory_consumption", "128",
            "opcache.interned_strings_buffer", "16",
            "opcache.max_accelerated_files", "10000",
            "opcache.revalidate_freq", "60",
            "opcache.fast_shutdown", "1"
        );
    }

    /**
     * Returns PHP memory limit in megabytes.
     *
     * <p>Recommended values:</p>
     * <ul>
     *   <li>WordPress: 256MB</li>
     *   <li>WooCommerce: 512MB</li>
     *   <li>Magento: 756MB-2GB</li>
     *   <li>Drupal: 256MB</li>
     * </ul>
     *
     * @return Memory limit in MB
     */
    default int phpMemoryLimit() {
        return 256;
    }

    /**
     * Returns PHP max execution time in seconds.
     *
     * @return Max execution time (default: 300 seconds)
     */
    default int phpMaxExecutionTime() {
        return 300;
    }

    /**
     * Returns PHP upload max filesize in megabytes.
     *
     * @return Upload max filesize in MB (default: 64MB)
     */
    default int phpUploadMaxFilesize() {
        return 64;
    }

    /**
     * Returns PHP post max size in megabytes.
     *
     * @return Post max size in MB (default: 64MB)
     */
    default int phpPostMaxSize() {
        return 64;
    }

    // ========== Media Storage Configuration ==========

    /**
     * Returns whether S3 media offloading is supported.
     *
     * <p>When enabled, media uploads are stored in S3 instead of local
     * filesystem, enabling horizontal scaling and CDN integration.</p>
     *
     * @return true if S3 media storage is supported
     */
    default boolean supportsS3MediaStorage() {
        return false;
    }

    /**
     * Returns the plugin/module identifier for S3 media integration.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: "wp-offload-media" or "amazon-s3-and-cloudfront"</li>
     *   <li>Magento: "magento/module-aws-s3"</li>
     *   <li>Drupal: "s3fs"</li>
     * </ul>
     *
     * @return Plugin identifier, or null if native S3 support
     */
    default String s3MediaPlugin() {
        return null;
    }

    /**
     * Returns the local media upload path within the container.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: "/var/www/html/wp-content/uploads"</li>
     *   <li>Magento: "/var/www/html/pub/media"</li>
     *   <li>Drupal: "/var/www/html/sites/default/files"</li>
     * </ul>
     *
     * @return Absolute path to media upload directory
     */
    String mediaUploadPath();

    // ========== CDN Configuration ==========

    /**
     * Returns whether CDN integration is supported.
     *
     * <p>CDN integration via CloudFront enables:</p>
     * <ul>
     *   <li>Edge caching for static assets</li>
     *   <li>Global content delivery</li>
     *   <li>SSL termination at edge</li>
     *   <li>DDoS protection</li>
     * </ul>
     *
     * @return true if CDN integration is supported (default: true)
     */
    default boolean supportsCdnIntegration() {
        return true;
    }

    /**
     * Returns static asset paths to be served via CDN.
     *
     * <p>These paths will be configured as CloudFront cache behaviors
     * with optimized caching policies. Implementations should override
     * {@link #cdnStaticPaths()} instead; this method is kept for
     * backwards compatibility and defaults to {@code cdnStaticPaths()}.</p>
     *
     * @return List of URL path patterns (e.g., "/wp-content/themes/*")
     * @deprecated Override {@link #cdnStaticPaths()} instead
     */
    @Deprecated
    default List<String> cdnAssetPaths() {
        return cdnStaticPaths();
    }

    /**
     * Returns paths served from the S3 media origin in CloudFront.
     *
     * <p>These paths correspond to user-uploaded media files (images,
     * videos, documents) that are offloaded to S3. They will be routed
     * to the S3 origin with a long-TTL media cache policy.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: {@code ["/wp-content/uploads/*"]}</li>
     *   <li>Magento: {@code ["/media/*"]}</li>
     *   <li>Drupal: {@code ["/sites/default/files/*"]}</li>
     * </ul>
     *
     * @return List of URL path patterns for S3 media origin (default: empty)
     */
    default List<String> cdnMediaPaths() {
        return List.of();
    }

    /**
     * Returns static asset paths cached at the edge in CloudFront.
     *
     * <p>These paths (CSS, JS, fonts, theme assets) are served from the
     * ALB origin with a long-TTL static cache policy.  They do not include
     * user-uploaded media; see {@link #cdnMediaPaths()} for that.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: {@code ["/wp-content/themes/*", "/wp-content/plugins/*", "/wp-includes/*"]}</li>
     *   <li>Magento: {@code ["/static/*"]}</li>
     *   <li>Drupal: {@code ["/core/*", "/modules/*", "/themes/*"]}</li>
     * </ul>
     *
     * @return List of URL path patterns for edge-cached static assets (default: empty)
     */
    default List<String> cdnStaticPaths() {
        return List.of();
    }

    /**
     * Returns admin/back-office paths that bypass CDN caching entirely.
     *
     * <p>Requests to these paths are forwarded to the ALB with caching
     * disabled and all headers/cookies forwarded, ensuring the CMS admin
     * panel receives full session state.</p>
     *
     * <p>This is also the single source of truth for ALB OIDC path-based
     * authentication. The default {@link #protectedPaths()} implementation
     * below delegates here, so CMS implementations only need to override
     * {@code cdnAdminPaths()} and both CDN routing and ALB auth stay in sync.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: {@code ["/wp-admin/*", "/wp-login.php"]}</li>
     *   <li>Magento: {@code ["/admin/*", "/backend/*"]}</li>
     *   <li>Joomla: {@code ["/administrator/*"]}</li>
     * </ul>
     *
     * @return List of URL path patterns that must not be cached (default: empty)
     */
    default List<String> cdnAdminPaths() {
        return List.of();
    }

    /**
     * Returns paths requiring ALB OIDC authentication for this CMS.
     *
     * <p>Defaults to {@link #cdnAdminPaths()} so that CDN routing and
     * ALB-level Cognito authentication always protect the same paths.
     * Override only when the protected path set differs from the CDN admin paths.</p>
     *
     * @return list of path patterns requiring authentication
     */
    @Override
    default List<String> protectedPaths() {
        return cdnAdminPaths();
    }

    /**
     * Returns Redis connection environment variables for this CMS.
     *
     * <p>The default implementation returns the generic
     * {@code REDIS_HOST} / {@code REDIS_PORT} pair.  CMS implementations
     * should override to add their plugin-specific variable names.</p>
     *
     * <p>Examples of CMS-specific additions:</p>
     * <ul>
     *   <li>WordPress: {@code WP_REDIS_HOST}, {@code WP_REDIS_PORT}, {@code WP_REDIS_DATABASE}</li>
     *   <li>Magento: {@code MAGENTO_CACHE_BACKEND_REDIS_SERVER} (+ page-cache and session variants)</li>
     *   <li>Drupal: {@code DRUPAL_REDIS_HOST}, {@code DRUPAL_REDIS_PORT}</li>
     * </ul>
     *
     * @param host Redis primary endpoint hostname
     * @param port Redis port (usually 6379)
     * @return mutable map of environment variable key-value pairs
     */
    default Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new java.util.HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        return env;
    }

    /**
     * Returns database connection environment variables for this CMS.
     *
     * <p>The default implementation returns generic
     * {@code DB_HOST} / {@code DB_PORT} / {@code DB_NAME} / {@code DB_USER}.
     * CMS implementations should override to add their native variable names.</p>
     *
     * <p>Examples of CMS-specific additions:</p>
     * <ul>
     *   <li>WordPress: {@code WORDPRESS_DB_HOST}, {@code WORDPRESS_DB_NAME}, {@code WORDPRESS_DB_USER}</li>
     *   <li>Magento: {@code MAGENTO_DATABASE_HOST}, {@code MAGENTO_DATABASE_NAME}</li>
     *   <li>Drupal: {@code DRUPAL_DATABASE_HOST}, {@code DRUPAL_DATABASE_DRIVER}</li>
     * </ul>
     *
     * @param host   RDS endpoint hostname
     * @param port   database port (3306 for MySQL/MariaDB, 5432 for Postgres)
     * @param name   database name
     * @param user   database username
     * @return mutable map of environment variable key-value pairs
     */
    default Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new java.util.HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        return env;
    }

    // ========== Object Caching ==========

    /**
     * Returns whether Redis/Memcached object caching is supported.
     *
     * <p>Object caching stores database query results and computed
     * values in memory for faster retrieval.</p>
     *
     * @return true if object caching is supported
     */
    default boolean supportsObjectCache() {
        return false;
    }

    /**
     * Returns the preferred caching backend.
     *
     * @return "redis", "memcached", or "none"
     */
    default String preferredCacheBackend() {
        return "redis";
    }

    /**
     * Returns the object cache plugin/module identifier.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: "redis-cache" (Redis Object Cache plugin)</li>
     *   <li>Magento: Built-in Redis support</li>
     *   <li>Drupal: "redis" module</li>
     * </ul>
     *
     * @return Plugin identifier for object caching
     */
    default String objectCachePlugin() {
        return null;
    }

    // ========== Scheduled Tasks ==========

    /**
     * Returns whether the CMS has scheduled tasks (cron jobs).
     *
     * <p>Most CMS platforms have internal task schedulers:</p>
     * <ul>
     *   <li>WordPress: WP-Cron</li>
     *   <li>Magento: Cron groups (index, default, consumers)</li>
     *   <li>Drupal: Cron module</li>
     * </ul>
     *
     * @return true if scheduled tasks exist
     */
    default boolean hasScheduledTasks() {
        return false;
    }

    /**
     * Returns whether to use system cron instead of internal scheduler.
     *
     * <p>System cron is recommended for production because:</p>
     * <ul>
     *   <li>More reliable execution timing</li>
     *   <li>Reduced page load overhead</li>
     *   <li>Better control over resource usage</li>
     * </ul>
     *
     * @return true to disable internal cron and use system cron
     */
    default boolean useSystemCron() {
        return true;
    }

    /**
     * Returns scheduled task commands for system cron.
     *
     * <p>Map keys are cron schedule expressions, values are commands.</p>
     *
     * <p>Example for WordPress:</p>
     * <pre>
     * "* /15 * * * *" -> "curl -s https://example.com/wp-cron.php"
     * </pre>
     *
     * @param siteUrl The site URL for cron execution
     * @return Map of cron schedule to command
     */
    default Map<String, String> cronCommands(String siteUrl) {
        return Map.of();
    }

    // ========== Multi-site Support ==========

    /**
     * Returns whether multi-site/multi-store is supported.
     *
     * <p>Multi-site capabilities:</p>
     * <ul>
     *   <li>WordPress: Multisite network</li>
     *   <li>Magento: Multi-store views</li>
     *   <li>Drupal: Multi-site configuration</li>
     *   <li>PrestaShop: Multi-shop</li>
     * </ul>
     *
     * @return true if multi-site is available
     */
    default boolean supportsMultisite() {
        return false;
    }

    /**
     * Returns the multi-site configuration mode.
     *
     * <p>Modes:</p>
     * <ul>
     *   <li>"subdomain" - sites.example.com</li>
     *   <li>"subdirectory" - example.com/sites/</li>
     *   <li>"domain" - separate domains per site</li>
     *   <li>"none" - multi-site not enabled</li>
     * </ul>
     *
     * @return Multi-site mode string
     */
    default String multisiteMode() {
        return "none";
    }

    // ========== CMS Category ==========

    /**
     * Returns the CMS category.
     *
     * <p>Categories:</p>
     * <ul>
     *   <li>"cms" - Content management (WordPress, Joomla, Drupal)</li>
     *   <li>"ecommerce" - E-commerce (WooCommerce, Magento, PrestaShop)</li>
     * </ul>
     *
     * @return "cms" or "ecommerce"
     */
    default String cmsCategory() {
        return "cms";
    }

    /**
     * Returns whether this is an e-commerce platform.
     *
     * <p>E-commerce platforms may require:</p>
     * <ul>
     *   <li>PCI-DSS compliance</li>
     *   <li>Higher resource allocation</li>
     *   <li>Payment gateway integration</li>
     *   <li>Inventory management</li>
     * </ul>
     *
     * @return true for e-commerce platforms
     */
    default boolean isEcommerce() {
        return "ecommerce".equals(cmsCategory());
    }

    // ========== CmsPlugin Metadata Methods ==========

    /**
     * Get the application category from @CmsPlugin.
     *
     * <p>Overrides {@link ApplicationSpec#category()} to read from
     * {@link CmsPlugin} annotation instead of ApplicationPlugin.</p>
     *
     * @return the category from @CmsPlugin (e.g., "cms", "ecommerce", "forum", "wiki", "lms", "social")
     */
    @Override
    default String category() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return cmsCategory(); // Fall back to cmsCategory()
        }
        return annotation.category();
    }

    /**
     * Get the human-readable display name for this CMS.
     *
     * <p>Overrides {@link ApplicationSpec#displayName()} to read from
     * {@link CmsPlugin} annotation instead of ApplicationPlugin.</p>
     *
     * @return the display name from @CmsPlugin, or capitalized applicationId() if not specified
     */
    @Override
    default String displayName() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            String id = applicationId();
            return id.substring(0, 1).toUpperCase() + id.substring(1);
        }
        String displayName = annotation.displayName();
        if (displayName.isEmpty()) {
            String id = applicationId();
            return id.substring(0, 1).toUpperCase() + id.substring(1);
        }
        return displayName;
    }

    /**
     * Get the CMS description.
     *
     * <p>Overrides {@link ApplicationSpec#description()} to read from
     * {@link CmsPlugin} annotation instead of ApplicationPlugin.</p>
     *
     * @return the description from @CmsPlugin
     */
    @Override
    default String description() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return "";
        }
        return annotation.description();
    }

    /**
     * Get the default Fargate CPU units from @CmsPlugin.
     *
     * @return the default CPU units
     */
    @Override
    default int defaultCpu() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return 1024;
        }
        return annotation.defaultCpu();
    }

    /**
     * Get the default Fargate memory from @CmsPlugin.
     *
     * @return the default memory in MB
     */
    @Override
    default int defaultMemory() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return 2048;
        }
        return annotation.defaultMemory();
    }

    /**
     * Get the default EC2 instance type from @CmsPlugin.
     *
     * @return the default instance type
     */
    @Override
    default String defaultInstanceType() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return "t3.small";
        }
        return annotation.defaultInstanceType();
    }

    /**
     * Check if this CMS supports Fargate deployment.
     *
     * @return true if Fargate is supported
     */
    @Override
    default boolean supportsFargate() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return true;
        }
        return annotation.supportsFargate();
    }

    /**
     * Check if this CMS supports EC2 deployment.
     *
     * @return true if EC2 is supported
     */
    @Override
    default boolean supportsEc2() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return true;
        }
        return annotation.supportsEc2();
    }

    /**
     * CMS apps declare database requirement on {@link CmsPlugin#requiresDatabase()}.
     */
    @Override
    default boolean requiresDatabase() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return false;
        }
        return annotation.requiresDatabase();
    }

    /**
     * Check if this CMS supports OIDC integration.
     *
     * <p>Overrides {@link ApplicationSpec#supportsOidcIntegration()} to read from
     * {@link CmsPlugin} annotation instead of ApplicationPlugin.</p>
     *
     * @return true if OIDC is supported based on @CmsPlugin.supportsOidc()
     */
    @Override
    default boolean supportsOidcIntegration() {
        CmsPlugin annotation = getClass().getAnnotation(CmsPlugin.class);
        if (annotation == null) {
            return false;
        }
        return annotation.supportsOidc();
    }

    // ========== Web Server Configuration ==========

    /**
     * Returns the preferred web server.
     *
     * @return "nginx", "apache", or "caddy"
     */
    default String preferredWebServer() {
        return "nginx";
    }

    /**
     * Returns the document root path within the container.
     *
     * <p>This is where the web server serves files from.</p>
     *
     * @return Document root path (e.g., "/var/www/html")
     */
    default String documentRoot() {
        return containerDataPath();
    }

    // ========== Container Configuration ==========

    /**
     * Returns the container startup command for this CMS.
     *
     * <p>Use this to override the default container entrypoint/command.
     * Common uses include:</p>
     * <ul>
     *   <li>Configuring web server to listen on non-privileged ports</li>
     *   <li>Setting up environment-specific configuration</li>
     *   <li>Running initialization scripts before the main process</li>
     * </ul>
     *
     * <p>Example for Apache on port 8080:</p>
     * <pre>
     * return List.of("/bin/sh", "-c",
     *     "sed -i 's/Listen 80/Listen 8080/' /etc/apache2/ports.conf &amp;&amp; " +
     *     "sed -i 's/:80/:8080/' /etc/apache2/sites-available/*.conf &amp;&amp; " +
     *     "apache2-foreground");
     * </pre>
     *
     * @return List of command arguments, or null to use the image default
     */
    default List<String> containerCommand() {
        return null;
    }

    // ========== CLI Tool Support ==========

    /**
     * Returns the CLI tool for this CMS, if available.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>WordPress: "wp" (WP-CLI)</li>
     *   <li>Magento: "bin/magento"</li>
     *   <li>Drupal: "drush"</li>
     *   <li>Joomla: "cli/joomla.php"</li>
     * </ul>
     *
     * @return CLI tool command, or null if not available
     */
    default String cliTool() {
        return null;
    }

    /**
     * Returns commands to install the CLI tool.
     *
     * @return List of shell commands to install CLI tool
     */
    default List<String> cliToolInstallCommands() {
        return List.of();
    }
}
