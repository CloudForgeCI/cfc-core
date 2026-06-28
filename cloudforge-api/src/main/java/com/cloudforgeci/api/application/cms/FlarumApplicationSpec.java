package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.FlarumOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flarum Forum ApplicationSpec implementation.
 *
 * <p>Flarum is a modern, lightweight, and fast forum platform built on
 * PHP and Mithril.js. It's designed for simplicity and elegance.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2+ support</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>Modern, responsive UI</li>
 *   <li>Extension ecosystem</li>
 *   <li>Real-time notifications</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "flarum",
    category = "forum",
    displayName = "Flarum",
    description = "Modern, lightweight forum platform",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "FoF OAuth Extension / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://flarum.org",
    defaultImage = "php:8.2-fpm-alpine"
)
public class FlarumApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "flarum";
    protected static final String DEFAULT_IMAGE = "php:8.2-fpm-alpine";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/flarum";
    protected static final String VOLUME_NAME = "flarumData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "gd", "curl", "mbstring", "xml", "dom",
        "zip", "json", "opcache", "redis", "fileinfo", "tokenizer",
        "openssl"
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
        return 120;
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
        return 128;
    }

    @Override
    public int phpMaxExecutionTime() {
        return 60;
    }

    @Override
    public int phpUploadMaxFilesize() {
        return 16;
    }

    @Override
    public int phpPostMaxSize() {
        return 16;
    }

    @Override
    public boolean supportsS3MediaStorage() {
        return true;
    }

    @Override
    public String s3MediaPlugin() {
        return "fof/upload"; // FoF Upload extension with S3 driver
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/public/assets/files";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnAssetPaths() {
        return List.of(
            "/assets/*"
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
        return null; // Laravel native cache
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
            "* * * * *", "php /var/www/html/flarum schedule:run >> /dev/null 2>&1"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return false;
    }

    @Override
    public String multisiteMode() {
        return "none";
    }

    @Override
    public String cmsCategory() {
        return "forum";
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
        return "flarum";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of Flarum
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "5.6")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("flarum");
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
            "/var/www/html/storage/logs/flarum.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults().withVersion("8.2");
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
            "# Install Flarum",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    composer create-project flarum/flarum .",
            "    echo 'Flarum downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/storage",
            "chmod -R 775 " + ec2DataPath() + "/public/assets",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'Flarum installation complete' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        env.put("FLARUM_DEBUG", "false");

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("FLARUM_BASE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new FlarumOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for Flarum when using ALB-level OIDC.
     *
     * <p>Flarum administrative areas:</p>
     * <ul>
     *   <li>/admin - Admin dashboard</li>
     * </ul>
     *
     * @return list of Flarum administrative paths requiring authentication
     */
    @Override
    public List<String> protectedPaths() {
        return List.of(
            "/admin/*"    // Admin dashboard
        );
    }
}
