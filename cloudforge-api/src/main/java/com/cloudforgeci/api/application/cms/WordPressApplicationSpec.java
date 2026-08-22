package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.WordPressOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WordPress CMS ApplicationSpec implementation.
 *
 * <p>WordPress is an open-source CMS for websites and blogs. This specification
 * can be extended for WooCommerce e-commerce deployments.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>PHP 8.2 with OPcache optimization</li>
 *   <li>MySQL/MariaDB database support</li>
 *   <li>S3 media offloading via WP Offload Media plugin</li>
 *   <li>Redis object caching via Redis Object Cache plugin</li>
 *   <li>OIDC authentication via OpenID Connect Generic plugin</li>
 *   <li>Multi-site network support</li>
 *   <li>WP-CLI for management</li>
 * </ul>
 *
 * <h2>Recommended Plugins:</h2>
 * <ul>
 *   <li>Redis Object Cache - Object caching</li>
 *   <li>WP Offload Media - S3 media storage</li>
 *   <li>OpenID Connect Generic - OIDC authentication</li>
 *   <li>Wordfence - Security</li>
 * </ul>
 *
 * @since 3.1.0
 * @see WooCommerceApplicationSpec
 */
@CmsPlugin(
    value = "wordpress",
    category = "cms",
    displayName = "WordPress",
    description = "Open-source CMS for websites and blogs",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "OpenID Connect Generic Plugin",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://wordpress.org",
    defaultImage = "wordpress:php8.2-apache"
)
public class WordPressApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "wordpress";
    // The fpm-alpine variant only speaks FastCGI on 9000 — nothing listens on
    // APPLICATION_PORT (80) at all, so applicationSpec-driven single-container Fargate/MiniStack/
    // LocalStack deploys were never actually reachable (confirmed live: CloudFormation reported
    // CREATE_COMPLETE, but the container had nothing bound to port 80). The apache variant bakes
    // in the same WordPress core files and listens on 80 directly — a pure drop-in swap, not an
    // architecture change. (The EC2 runtime path is unaffected either way — see webServer()/the
    // userData installCommands() below, which already correctly install and run nginx+php-fpm
    // together on the instance regardless of this constant.)
    protected static final String DEFAULT_IMAGE = "wordpress:php8.2-apache";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/wordpress";
    protected static final String VOLUME_NAME = "wordpressData";
    protected static final String CONTAINER_USER = "33:33"; // www-data UID:GID
    protected static final String EFS_PERMISSIONS = "755";

    // PHP Configuration
    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml", "dom",
        "zip", "intl", "soap", "bcmath", "opcache", "redis", "imagick",
        "exif", "fileinfo"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String applicationId() {
        return APPLICATION_ID;
    }

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public int applicationPort() {
        return APPLICATION_PORT;
    }

    @Override
    public String containerDataPath() {
        return CONTAINER_DATA_PATH;
    }

    @Override
    public String efsDataPath() {
        return EFS_DATA_PATH;
    }

    @Override
    public String volumeName() {
        return VOLUME_NAME;
    }

    @Override
    public String containerUser() {
        return CONTAINER_USER;
    }

    @Override
    public String efsPermissions() {
        return EFS_PERMISSIONS;
    }

    @Override
    public String healthCheckPath() {
        return "/wp-admin/install.php";
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 180; // 3 minutes for WordPress startup
    }

    // ========== CmsSpec Implementation ==========

    @Override
    public String phpVersion() {
        return PHP_VERSION;
    }

    @Override
    public List<String> requiredPhpExtensions() {
        return PHP_EXTENSIONS;
    }

    @Override
    public Map<String, String> phpFpmConfig() {
        return Map.of(
            "pm", "dynamic",
            "pm.max_children", "50",
            "pm.start_servers", "5",
            "pm.min_spare_servers", "5",
            "pm.max_spare_servers", "35",
            "pm.max_requests", "500"
        );
    }

    @Override
    public Map<String, String> opcacheConfig() {
        return Map.of(
            "opcache.enable", "1",
            "opcache.memory_consumption", "128",
            "opcache.interned_strings_buffer", "16",
            "opcache.max_accelerated_files", "10000",
            "opcache.revalidate_freq", "60",
            "opcache.fast_shutdown", "1"
        );
    }

    @Override
    public int phpMemoryLimit() {
        return 256;
    }

    @Override
    public int phpMaxExecutionTime() {
        return 300;
    }

    @Override
    public int phpUploadMaxFilesize() {
        return 64;
    }

    @Override
    public int phpPostMaxSize() {
        return 64;
    }

    @Override
    public boolean supportsS3MediaStorage() {
        return true;
    }

    @Override
    public String s3MediaPlugin() {
        return "wp-offload-media";
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/wp-content/uploads";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/wp-content/uploads/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/wp-content/themes/*",
            "/wp-content/plugins/*",
            "/wp-includes/*"
        );
    }

    @Override
    public List<String> cdnAdminPaths() {
        return List.of("/wp-admin/*", "/wp-login.php");
    }

    @Override
    public boolean supportsObjectCache() {
        return true;
    }

    @Override
    public String preferredCacheBackend() {
        return "redis";
    }

    @Override
    public String objectCachePlugin() {
        return "redis-cache";
    }

    @Override
    public boolean hasScheduledTasks() {
        return true;
    }

    @Override
    public boolean useSystemCron() {
        return true;
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "*/15 * * * *", String.format("curl -s '%s/wp-cron.php' > /dev/null 2>&1", siteUrl)
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "subdirectory"; // or "subdomain"
    }

    @Override
    public String cmsCategory() {
        return "cms";
    }

    @Override
    public String preferredWebServer() {
        return "nginx";
    }

    @Override
    public String documentRoot() {
        return CONTAINER_DATA_PATH;
    }

    @Override
    public String cliTool() {
        return "wp";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(
            "curl -O https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar",
            "chmod +x wp-cli.phar",
            "mv wp-cli.phar /usr/local/bin/wp"
        );
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("wordpress");
    }

    @Override
    public int backupRetentionDays() {
        return 7;
    }

    // ========== EC2 Configuration ==========

    @Override
    public String ebsDeviceName() {
        return "/dev/xvdh";
    }

    @Override
    public String ec2DataPath() {
        return "/var/www/html";
    }

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/nginx/access.log",
            "/var/log/nginx/error.log",
            "/var/log/php-fpm/error.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        // Install PHP
        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.forWordPress();
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);

        // Install WP-CLI
        builder.addCommands(
            "# Install WP-CLI",
            "curl -O https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar",
            "chmod +x wp-cli.phar",
            "mv wp-cli.phar /usr/local/bin/wp",
            "echo 'WP-CLI installed' >> /var/log/userdata.log"
        );

        // Mount storage
        String[] userParts = containerUser().split(":");
        if (context.hasEfs()) {
            builder.mountEfs(
                context.efsId().orElseThrow(),
                context.accessPointId().orElseThrow(),
                ec2DataPath(),
                userParts[0], userParts[1]
            );
        } else {
            builder.mountEbs(ebsDeviceName(), ec2DataPath(), userParts[0], userParts[1]);
        }

        // Download WordPress
        builder.addCommands(
            "# Download WordPress",
            "cd " + ec2DataPath(),
            "if [ ! -f wp-config.php ]; then",
            "    wp core download --allow-root",
            "    echo 'WordPress downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'WordPress installation complete' >> /var/log/userdata.log"
        );

        // Install CloudWatch agent
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        // WordPress configuration
        if (fqdn != null && !fqdn.isBlank()) {
            String siteUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            env.put("WORDPRESS_CONFIG_EXTRA",
                String.format("define('WP_HOME', '%s'); define('WP_SITEURL', '%s'); define('FORCE_SSL_ADMIN', %s);",
                    siteUrl, siteUrl, sslEnabled ? "true" : "false"));
        }

        // Disable WP-Cron (use system cron)
        String configExtra = env.getOrDefault("WORDPRESS_CONFIG_EXTRA", "");
        env.put("WORDPRESS_CONFIG_EXTRA", configExtra + " define('DISABLE_WP_CRON', true);");

        // Security settings
        env.put("WORDPRESS_CONFIG_EXTRA", env.get("WORDPRESS_CONFIG_EXTRA") +
            " define('DISALLOW_FILE_EDIT', true);");

        return env;
    }

    // ========== Cache / Database Environment Variables ==========

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        env.put("WP_REDIS_HOST", host);
        env.put("WP_REDIS_PORT", String.valueOf(port));
        env.put("WP_REDIS_DATABASE", "0");
        env.put("WP_CACHE_KEY_SALT", applicationId() + "_");
        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        env.put("WORDPRESS_DB_HOST", host + ":" + port);
        env.put("WORDPRESS_DB_NAME", name);
        env.put("WORDPRESS_DB_USER", user);
        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new WordPressOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("application-oidc", "alb-oidc", "none");
    }
}
