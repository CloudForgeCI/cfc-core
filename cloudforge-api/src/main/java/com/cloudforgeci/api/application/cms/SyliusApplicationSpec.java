package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.SyliusOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sylius E-commerce ApplicationSpec implementation.
 *
 * <p>Sylius is a modern, headless-ready e-commerce platform built on Symfony.
 * It's designed for developers who need customization and API-first architecture.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.3 with Symfony 6.4/7.x</li>
 *   <li>MySQL/MariaDB/PostgreSQL database</li>
 *   <li>REST &amp; GraphQL APIs (API Platform)</li>
 *   <li>Headless-ready architecture</li>
 *   <li>BDD testing with Behat</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "sylius",
    category = "ecommerce",
    displayName = "Sylius",
    description = "Modern Symfony-based headless e-commerce platform",
    phpVersion = "8.3",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "Symfony Security Bundle / OAuth2",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://sylius.com",
    defaultImage = "sylius/standard:latest"
)
public class SyliusApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "sylius";
    protected static final String DEFAULT_IMAGE = "sylius/standard:latest";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/sylius";
    protected static final String VOLUME_NAME = "syliusData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.3";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "sodium", "apcu", "exif"
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
        return "/";
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 300;
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
            "pm.max_children", "75",
            "pm.start_servers", "10",
            "pm.min_spare_servers", "5",
            "pm.max_spare_servers", "35",
            "pm.max_requests", "1000"
        );
    }

    @Override
    public Map<String, String> opcacheConfig() {
        return Map.of(
            "opcache.enable", "1",
            "opcache.memory_consumption", "256",
            "opcache.interned_strings_buffer", "32",
            "opcache.max_accelerated_files", "20000",
            "opcache.revalidate_freq", "0",
            "opcache.validate_timestamps", "0",
            "opcache.preload", "/var/www/html/var/cache/prod/App_KernelProdContainer.preload.php",
            "opcache.preload_user", "www-data"
        );
    }

    @Override
    public int phpMemoryLimit() {
        return 512;
    }

    @Override
    public int phpMaxExecutionTime() {
        return 600;
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
        return "league/flysystem-aws-s3-v3"; // Symfony Flysystem bundle
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/public/media";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnAssetPaths() {
        return List.of(
            "/media/*",
            "/bundles/*",
            "/build/*"
        );
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
        return "symfony/cache"; // Symfony cache component
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
            "* * * * *", "php /var/www/html/bin/console messenger:consume async --time-limit=60",
            "0 * * * *", "php /var/www/html/bin/console cache:warmup --env=prod"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "domain"; // Multi-channel support
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
        return CONTAINER_DATA_PATH + "/public";
    }

    @Override
    public String cliTool() {
        return "bin/console";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of Symfony
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("sylius");
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
            "/var/www/html/var/log/prod.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults()
            .withVersion("8.3")
            .withExtensions(PHP_EXTENSIONS);
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);
        PhpUserDataBuilder.installComposer(builder);

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

        builder.addCommands(
            "# Install Sylius",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    composer create-project sylius/sylius-standard .",
            "    echo 'Sylius downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/var",
            "chmod -R 775 " + ec2DataPath() + "/public/media",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'Sylius installation complete' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        env.put("APP_ENV", "prod");
        env.put("APP_DEBUG", "0");

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("SYLIUS_HOST", fqdn);
            env.put("SYLIUS_SCHEME", sslEnabled ? "https" : "http");
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new SyliusOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for Sylius when using ALB-level OIDC.
     *
     * <p>Sylius administrative areas:</p>
     * <ul>
     *   <li>/admin - Store administration panel</li>
     * </ul>
     *
     * @return list of Sylius administrative paths requiring authentication
     */
    @Override
    public List<String> protectedPaths() {
        return List.of(
            "/admin/*"    // Store administration panel
        );
    }
}
