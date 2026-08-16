package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.MagentoOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Magento 2 E-commerce ApplicationSpec implementation.
 *
 * <p>Magento is an e-commerce platform owned by Adobe with B2B and B2C
 * catalog, order, and storefront features.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2 with advanced extensions</li>
 *   <li>MySQL 8.0 / MariaDB 10.6+ required</li>
 *   <li>Elasticsearch/OpenSearch for catalog search</li>
 *   <li>Redis for cache and sessions</li>
 *   <li>Varnish for full-page caching</li>
 *   <li>RabbitMQ for message queues (optional)</li>
 * </ul>
 *
 * <h2>Resource Requirements:</h2>
 * <p>Magento is resource-intensive and requires significant CPU,
 * memory, and storage compared to other PHP platforms.</p>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "magento",
    category = "ecommerce",
    displayName = "Magento 2 / Adobe Commerce",
    description = "E-commerce platform for B2B and B2C online stores",
    phpVersion = "8.2",
    defaultCpu = 4096,
    defaultMemory = 8192,
    defaultInstanceType = "t3.xlarge",
    supportsOidc = true,
    oidcMethod = "miniOrange OIDC Module",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://business.adobe.com/products/magento/magento-commerce.html",
    defaultImage = "magento/magento-cloud-docker-php:8.2-fpm"
)
public class MagentoApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "magento";
    protected static final String DEFAULT_IMAGE = "magento/magento-cloud-docker-php:8.2-fpm";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/magento";
    protected static final String VOLUME_NAME = "magentoData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "bcmath", "ctype", "curl", "dom", "fileinfo", "gd", "hash",
        "iconv", "intl", "json", "libxml", "mbstring", "openssl",
        "pdo_mysql", "simplexml", "soap", "sockets", "sodium",
        "spl", "tokenizer", "xmlwriter", "xsl", "zip", "zlib",
        "opcache", "redis", "imagick"
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
        return "/health_check.php";
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 600; // 10 minutes - Magento startup is slow
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
            "pm.max_children", "100",
            "pm.start_servers", "20",
            "pm.min_spare_servers", "10",
            "pm.max_spare_servers", "50",
            "pm.max_requests", "1000"
        );
    }

    @Override
    public Map<String, String> opcacheConfig() {
        return Map.of(
            "opcache.enable", "1",
            "opcache.memory_consumption", "512",
            "opcache.interned_strings_buffer", "64",
            "opcache.max_accelerated_files", "60000",
            "opcache.revalidate_freq", "0",
            "opcache.validate_timestamps", "0",
            "opcache.save_comments", "1",
            "opcache.fast_shutdown", "1"
        );
    }

    @Override
    public int phpMemoryLimit() {
        return 2048; // 2GB for Magento
    }

    @Override
    public int phpMaxExecutionTime() {
        return 18000; // 5 hours for reindexing
    }

    @Override
    public int phpUploadMaxFilesize() {
        return 128;
    }

    @Override
    public int phpPostMaxSize() {
        return 128;
    }

    @Override
    public boolean supportsS3MediaStorage() {
        return true;
    }

    @Override
    public String s3MediaPlugin() {
        return "magento/module-aws-s3";
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/pub/media";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/pub/media/*", "/media/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of("/pub/static/*", "/static/*");
    }

    @Override
    public List<String> cdnAdminPaths() {
        // Admin path is customizable in real Magento installs; override this method if you've changed it.
        return List.of("/admin/*", "/backend/*", "/setup/*");
    }

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        // Default cache backend (database 0)
        env.put("MAGENTO_CACHE_BACKEND_REDIS_SERVER", host);
        env.put("MAGENTO_CACHE_BACKEND_REDIS_PORT", String.valueOf(port));
        env.put("MAGENTO_CACHE_BACKEND_REDIS_DATABASE", "0");
        // Full-page cache backend (database 1)
        env.put("MAGENTO_PAGE_CACHE_BACKEND_REDIS_SERVER", host);
        env.put("MAGENTO_PAGE_CACHE_BACKEND_REDIS_PORT", String.valueOf(port));
        env.put("MAGENTO_PAGE_CACHE_BACKEND_REDIS_DATABASE", "1");
        // Session backend (database 2)
        env.put("MAGENTO_SESSION_BACKEND_REDIS_SERVER", host);
        env.put("MAGENTO_SESSION_BACKEND_REDIS_PORT", String.valueOf(port));
        env.put("MAGENTO_SESSION_BACKEND_REDIS_DATABASE", "2");
        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        env.put("MAGENTO_DATABASE_HOST", host);
        env.put("MAGENTO_DATABASE_PORT", String.valueOf(port));
        env.put("MAGENTO_DATABASE_NAME", name);
        env.put("MAGENTO_DATABASE_USER", user);
        return env;
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
        return null; // Native Redis support
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
        Map<String, String> crons = new HashMap<>();
        // Magento cron groups
        crons.put("* * * * *", "php /var/www/html/bin/magento cron:run --group=default");
        crons.put("* * * * *", "php /var/www/html/bin/magento cron:run --group=index");
        crons.put("*/5 * * * *", "php /var/www/html/bin/magento cron:run --group=consumers");
        crons.put("*/15 * * * *", "php /var/www/html/bin/magento cron:run --group=ddg_automation");
        return crons;
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "domain"; // Magento uses store views with separate domains
    }

    @Override
    public String cmsCategory() {
        return "ecommerce";
    }

    @Override
    public String preferredWebServer() {
        return "nginx";
    }

    @Override
    public String documentRoot() {
        return CONTAINER_DATA_PATH + "/pub";
    }

    @Override
    public String cliTool() {
        return "bin/magento";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // Magento CLI is part of the installation
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.r6g.large")
            .withStorage(100)
            .withDatabaseName("magento");
    }

    @Override
    public int backupRetentionDays() {
        return 14;
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
            "/var/www/html/var/log/system.log",
            "/var/www/html/var/log/exception.log",
            "/var/www/html/var/log/debug.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        // Install PHP
        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.forMagento();
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);
        PhpUserDataBuilder.installComposer(builder);

        // Install Elasticsearch
        builder.addCommands(
            "# Install Elasticsearch",
            "rpm --import https://artifacts.elastic.co/GPG-KEY-elasticsearch",
            "cat > /etc/yum.repos.d/elasticsearch.repo << 'EOF'",
            "[elasticsearch]",
            "name=Elasticsearch repository",
            "baseurl=https://artifacts.elastic.co/packages/8.x/yum",
            "gpgcheck=1",
            "gpgkey=https://artifacts.elastic.co/GPG-KEY-elasticsearch",
            "enabled=1",
            "autorefresh=1",
            "type=rpm-md",
            "EOF",
            "dnf install -y elasticsearch",
            "systemctl enable elasticsearch",
            "systemctl start elasticsearch",
            "echo 'Elasticsearch installed' >> /var/log/userdata.log"
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

        // Set permissions
        builder.addCommands(
            "# Set Magento permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "find " + ec2DataPath() + " -type d -exec chmod 755 {} \\;",
            "find " + ec2DataPath() + " -type f -exec chmod 644 {} \\;",
            "chmod -R 775 " + ec2DataPath() + "/var",
            "chmod -R 775 " + ec2DataPath() + "/pub/static",
            "chmod -R 775 " + ec2DataPath() + "/pub/media",
            "chmod -R 775 " + ec2DataPath() + "/generated",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'Magento installation complete' >> /var/log/userdata.log"
        );

        // Install CloudWatch agent
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        // Magento mode
        env.put("MAGE_MODE", "production");

        // Base URLs
        if (fqdn != null && !fqdn.isBlank()) {
            String baseUrl = (sslEnabled ? "https://" : "http://") + fqdn + "/";
            env.put("MAGENTO_BACKEND_FRONTNAME", "admin");
            env.put("MAGENTO_BASE_URL", baseUrl);
            env.put("MAGENTO_BASE_URL_SECURE", sslEnabled ? baseUrl : "");
            env.put("MAGENTO_USE_SECURE", sslEnabled ? "1" : "0");
            env.put("MAGENTO_USE_SECURE_ADMIN", sslEnabled ? "1" : "0");
        }

        // Performance settings
        env.put("MAGENTO_CACHE_BACKEND", "redis");
        env.put("MAGENTO_SESSION_SAVE", "redis");

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new MagentoOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("application-oidc", "alb-oidc", "none");
    }

    // ========== Magento Specific ==========

    /**
     * Returns whether Elasticsearch/OpenSearch is required.
     *
     * @return true (Magento 2.4+ requires search engine)
     */
    public boolean requiresSearchEngine() {
        return true;
    }

    /**
     * Returns the preferred search engine.
     *
     * @return "opensearch" or "elasticsearch"
     */
    public String preferredSearchEngine() {
        return "opensearch";
    }

    /**
     * Returns whether Varnish full-page cache is recommended.
     *
     * @return true for production deployments
     */
    public boolean supportsVarnish() {
        return true;
    }

    /**
     * Returns whether RabbitMQ is supported for async operations.
     *
     * @return true
     */
    public boolean supportsRabbitMq() {
        return true;
    }

    /**
     * Returns PhpRuntimeConfig optimized for Magento.
     *
     * @return PHP runtime configuration
     */
    public static PhpRuntimeConfig getPhpConfig() {
        return PhpRuntimeConfig.forMagento();
    }
}
