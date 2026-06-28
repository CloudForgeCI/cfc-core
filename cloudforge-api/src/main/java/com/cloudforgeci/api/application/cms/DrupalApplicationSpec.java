package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.DrupalOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drupal CMS ApplicationSpec implementation.
 *
 * <p>Drupal is an enterprise-grade CMS known for its flexibility,
 * security, and strong content architecture. It's widely used in
 * government, education, and enterprise contexts.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2 with native OIDC module support</li>
 *   <li>MySQL/MariaDB/PostgreSQL database support</li>
 *   <li>S3FS module for media offloading</li>
 *   <li>Redis module for object caching</li>
 *   <li>Drush CLI for management</li>
 *   <li>Native OpenID Connect module (no third-party plugin)</li>
 * </ul>
 *
 * <h2>Security:</h2>
 * <p>Drupal has a dedicated security team and regular security
 * advisories. It can be configured toward HIPAA compliance when properly hardened.</p>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "drupal",
    category = "cms",
    displayName = "Drupal",
    description = "Enterprise CMS for complex content architectures",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "Native OpenID Connect Module",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://www.drupal.org",
    defaultImage = "drupal:10-php8.2-fpm-alpine"
)
public class DrupalApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "drupal";
    protected static final String DEFAULT_IMAGE = "drupal:10-php8.2-fpm-alpine";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/drupal";
    protected static final String VOLUME_NAME = "drupalData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "opcache", "redis", "imagick",
        "fileinfo", "json", "tokenizer", "simplexml"
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
        return "/user/login";
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 180;
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
        return "s3fs";
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/sites/default/files";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/sites/default/files/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of("/core/*", "/modules/*", "/themes/*", "/libraries/*");
    }

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        env.put("DRUPAL_REDIS_HOST", host);
        env.put("DRUPAL_REDIS_PORT", String.valueOf(port));
        env.put("DRUPAL_REDIS_BASE", "0");
        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        env.put("DRUPAL_DATABASE_HOST", host);
        env.put("DRUPAL_DATABASE_PORT", String.valueOf(port));
        env.put("DRUPAL_DATABASE_NAME", name);
        env.put("DRUPAL_DATABASE_USER", user);
        env.put("DRUPAL_DATABASE_DRIVER", "mysql");
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
        return "redis";
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
            "*/15 * * * *", String.format("curl -s '%s/cron/%s' > /dev/null 2>&1", siteUrl, "${DRUPAL_CRON_KEY}"),
            "0 * * * *", "drush -r /var/www/html cron"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "subdirectory";
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
        return "drush";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(
            "composer global require drush/drush",
            "ln -s ~/.composer/vendor/bin/drush /usr/local/bin/drush"
        );
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("drupal");
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
            "/var/www/html/sites/default/files/logs/drupal.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        // Install PHP
        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.forDrupal();
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);
        PhpUserDataBuilder.installComposer(builder);
        PhpUserDataBuilder.installDrush(builder);

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

        // Download Drupal
        builder.addCommands(
            "# Download Drupal",
            "cd " + ec2DataPath(),
            "if [ ! -f sites/default/settings.php ]; then",
            "    composer create-project drupal/recommended-project:^10 .",
            "    echo 'Drupal downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/sites/default/files",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'Drupal installation complete' >> /var/log/userdata.log"
        );

        // Install CloudWatch agent
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        // Drupal settings
        env.put("DRUPAL_TRUSTED_HOST_PATTERNS", fqdn != null ? "^" + fqdn.replace(".", "\\.") + "$" : "");

        // Hash salt (should be overridden via secrets)
        env.put("DRUPAL_HASH_SALT", "${DRUPAL_HASH_SALT}");

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new DrupalOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("application-oidc", "alb-oidc", "none");
    }

    /**
     * Returns PhpRuntimeConfig optimized for Drupal.
     *
     * @return PHP runtime configuration
     */
    public static PhpRuntimeConfig getPhpConfig() {
        return PhpRuntimeConfig.forDrupal();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for Drupal when using ALB-level OIDC.
     *
     * <p>Drupal administrative areas:</p>
     * <ul>
     *   <li>/admin/* - Administration pages</li>
     *   <li>/user/* - User login, registration, profiles</li>
     *   <li>/update.php - Update script</li>
     * </ul>
     *
     * @return list of Drupal administrative paths requiring authentication
     */
    @Override
    public List<String> protectedPaths() {
        return List.of(
            "/admin/*",      // Admin pages
            "/user/*",       // User login/registration
            "/update.php"    // Update script
        );
    }
}
