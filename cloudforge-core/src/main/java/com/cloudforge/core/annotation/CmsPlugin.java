package com.cloudforge.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a pluggable CMS/e-commerce application specification.
 *
 * <p>CMS specifications annotated with this annotation are automatically discovered
 * and loaded by the CloudForge CMS deployment system via Java ServiceLoader. This enables
 * support for content management systems, e-commerce platforms, and social networking
 * applications without modifying core code.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @CmsPlugin(
 *     value = "wordpress",
 *     category = "cms",
 *     displayName = "WordPress",
 *     description = "World's most popular CMS for websites and blogs",
 *     phpVersion = "8.2",
 *     defaultCpu = 1024,
 *     defaultMemory = 2048,
 *     supportsOidc = true,
 *     oidcMethod = "OpenID Connect Generic Plugin",
 *     requiresDatabase = true,
 *     supportedDatabases = {"mysql", "mariadb"},
 *     supportsS3Media = true,
 *     supportsObjectCache = true,
 *     supportsMultisite = true
 * )
 * public class WordPressApplicationSpec implements CmsSpec {
 *     // Implementation
 * }
 * }</pre>
 *
 * <h2>CMS Categories:</h2>
 * <ul>
 *   <li><strong>cms:</strong> Content Management Systems (WordPress, Joomla, Drupal, Concrete5, TYPO3)</li>
 *   <li><strong>ecommerce:</strong> E-commerce Platforms (WooCommerce, Magento, PrestaShop, OpenCart, Shopware)</li>
 *   <li><strong>social:</strong> Social Networking (Dolphin/UNA, HumHub, Elgg, phpFox)</li>
 *   <li><strong>forum:</strong> Forum Software (phpBB, Discourse, Flarum, MyBB, XenForo)</li>
 *   <li><strong>wiki:</strong> Wiki Platforms (MediaWiki, DokuWiki, BookStack)</li>
 *   <li><strong>lms:</strong> Learning Management (Moodle, Canvas, Chamilo)</li>
 * </ul>
 *
 * @since 3.1.0
 * @see com.cloudforge.core.interfaces.CmsSpec
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface CmsPlugin {

    /**
     * CMS identifier used in deployment configuration.
     *
     * <p>This value is used in deployment-context.json to specify which CMS to deploy:</p>
     * <pre>{@code
     * {
     *   "application": "wordpress",
     *   "runtimeType": "FARGATE"
     * }
     * }</pre>
     *
     * <p>Examples: "wordpress", "woocommerce", "magento", "drupal", "joomla",
     * "prestashop", "dolphin", "moodle", "mediawiki"</p>
     *
     * @return the CMS identifier (lowercase, kebab-case)
     */
    String value();

    /**
     * CMS category for grouping and discovery.
     *
     * <p>Standard categories:</p>
     * <ul>
     *   <li>cms - Content management systems</li>
     *   <li>ecommerce - E-commerce platforms</li>
     *   <li>social - Social networking platforms</li>
     *   <li>forum - Forum and discussion boards</li>
     *   <li>wiki - Wiki and documentation platforms</li>
     *   <li>lms - Learning management systems</li>
     * </ul>
     *
     * @return the CMS category (default: "cms")
     */
    String category() default "cms";

    /**
     * Human-readable display name for the CMS.
     *
     * <p>Used in CLI tools, logging, and documentation.</p>
     *
     * <p>Examples: "WordPress", "Magento 2", "Drupal", "Dolphin"</p>
     *
     * @return the display name (defaults to capitalized value if empty)
     */
    String displayName() default "";

    /**
     * Brief description of the CMS platform's purpose.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"World's most popular CMS for websites and blogs"</li>
     *   <li>"Enterprise e-commerce platform by Adobe"</li>
     *   <li>"Social networking and community platform"</li>
     * </ul>
     *
     * @return the CMS description
     */
    String description() default "";

    /**
     * Required PHP version for this CMS.
     *
     * <p>Common versions:</p>
     * <ul>
     *   <li>"8.2" - WordPress 6.4+, Magento 2.4.6+, Drupal 10</li>
     *   <li>"8.1" - PrestaShop 8.x, Joomla 5</li>
     *   <li>"8.0" - Legacy support</li>
     * </ul>
     *
     * @return PHP version string (default: "8.2")
     */
    String phpVersion() default "8.2";

    /**
     * Default Fargate CPU units when not specified in deployment context.
     *
     * <p>Recommended values by CMS type:</p>
     * <ul>
     *   <li>Simple CMS (WordPress, Joomla): 1024 (1 vCPU)</li>
     *   <li>E-commerce (WooCommerce, PrestaShop): 2048 (2 vCPU)</li>
     *   <li>Enterprise (Magento): 4096 (4 vCPU)</li>
     * </ul>
     *
     * @return default CPU units (256, 512, 1024, 2048, 4096)
     */
    int defaultCpu() default 1024;

    /**
     * Default Fargate memory in MB when not specified in deployment context.
     *
     * <p>Recommended values by CMS type:</p>
     * <ul>
     *   <li>Simple CMS: 2048 MB</li>
     *   <li>E-commerce: 4096 MB</li>
     *   <li>Enterprise: 8192 MB</li>
     * </ul>
     *
     * @return default memory in MB
     */
    int defaultMemory() default 2048;

    /**
     * Default EC2 instance type when not specified in deployment context.
     *
     * <p>Recommended types:</p>
     * <ul>
     *   <li>t3.small - Simple CMS, low traffic</li>
     *   <li>t3.medium - E-commerce, moderate traffic</li>
     *   <li>t3.large - Enterprise, high traffic</li>
     *   <li>m5.large - Production e-commerce</li>
     * </ul>
     *
     * @return EC2 instance type (default: "t3.small")
     */
    String defaultInstanceType() default "t3.small";

    /**
     * Whether this CMS supports OIDC authentication integration.
     *
     * <p>OIDC support enables single sign-on with:</p>
     * <ul>
     *   <li>AWS Cognito</li>
     *   <li>IAM Identity Center</li>
     *   <li>External OIDC providers (Okta, Auth0, etc.)</li>
     * </ul>
     *
     * @return true if OIDC integration is supported (default: false)
     */
    boolean supportsOidc() default false;

    /**
     * OIDC integration method description.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"OpenID Connect Generic Plugin" (WordPress)</li>
     *   <li>"miniOrange OAuth module" (Magento)</li>
     *   <li>"Native OpenID Connect module" (Drupal)</li>
     *   <li>"OAuth2 module" (Dolphin)</li>
     * </ul>
     *
     * @return description of OIDC integration method
     */
    String oidcMethod() default "plugin";

    /**
     * Whether this CMS requires an external database (RDS).
     *
     * <p>Most CMS platforms require a database. Exceptions might include
     * flat-file CMS systems like Grav or Statamic.</p>
     *
     * @return true if external database is required (default: true)
     */
    boolean requiresDatabase() default true;

    /**
     * Supported database engines for this CMS.
     *
     * <p>Common engines:</p>
     * <ul>
     *   <li>"mysql" - MySQL 8.0+</li>
     *   <li>"mariadb" - MariaDB 10.6+</li>
     *   <li>"postgres" - PostgreSQL 14+ (Drupal, some enterprise CMS)</li>
     * </ul>
     *
     * @return array of supported database engine identifiers
     */
    String[] supportedDatabases() default {"mysql", "mariadb"};

    /**
     * Whether S3 media offloading is supported.
     *
     * <p>S3 media storage enables:</p>
     * <ul>
     *   <li>Horizontal scaling (multiple instances)</li>
     *   <li>CDN integration</li>
     *   <li>Reduced EFS/EBS storage costs</li>
     * </ul>
     *
     * @return true if S3 media storage is supported (default: false)
     */
    boolean supportsS3Media() default false;

    /**
     * Whether Redis/Memcached object caching is supported.
     *
     * <p>Object caching improves performance by caching:</p>
     * <ul>
     *   <li>Database query results</li>
     *   <li>Computed page fragments</li>
     *   <li>Session data</li>
     * </ul>
     *
     * @return true if object caching is supported (default: false)
     */
    boolean supportsObjectCache() default false;

    /**
     * Whether multi-site/multi-store functionality is supported.
     *
     * <p>Multi-site features:</p>
     * <ul>
     *   <li>WordPress Multisite Network</li>
     *   <li>Magento Multi-store</li>
     *   <li>Drupal Multi-site</li>
     *   <li>PrestaShop Multi-shop</li>
     * </ul>
     *
     * @return true if multi-site is supported (default: false)
     */
    boolean supportsMultisite() default false;

    /**
     * Whether this CMS supports Fargate deployment.
     *
     * <p>Most PHP CMS platforms support Fargate containerization.</p>
     *
     * @return true if Fargate is supported (default: true)
     */
    boolean supportsFargate() default true;

    /**
     * Whether this CMS supports EC2 deployment.
     *
     * <p>EC2 deployment provides more flexibility for complex
     * configurations and legacy requirements.</p>
     *
     * @return true if EC2 is supported (default: true)
     */
    boolean supportsEc2() default true;

    /**
     * Official website URL for the CMS.
     *
     * <p>Used for documentation and reference links.</p>
     *
     * @return CMS official website URL
     */
    String websiteUrl() default "";

    /**
     * Docker Hub image or registry path for the official container image.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"wordpress:php8.2-fpm-alpine"</li>
     *   <li>"magento/magento-cloud-docker-php:8.2-fpm"</li>
     *   <li>"drupal:10-php8.2-fpm-alpine"</li>
     * </ul>
     *
     * @return container image reference
     */
    String defaultImage() default "";
}
