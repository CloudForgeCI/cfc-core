package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.OctoberCmsOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * October CMS ApplicationSpec implementation.
 *
 * <p>October CMS is a Laravel-based CMS designed for developers.
 * It offers a clean architecture and is highly customizable.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2+ with Laravel 12 (v4)</li>
 *   <li>MySQL/MariaDB/PostgreSQL/SQLite database</li>
 *   <li>File-based templating (Twig)</li>
 *   <li>Plugin marketplace</li>
 *   <li>Developer-friendly architecture</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "october-cms",
    category = "cms",
    displayName = "October CMS",
    description = "Laravel-based developer-friendly CMS",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "Laravel Socialite / OAuth Plugin",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql", "sqlite"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://octobercms.com",
    defaultImage = "php:8.2-fpm-alpine"
)
public class OctoberCmsApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    // The -apache image bundles Apache + mod_php and serves port 80. The fpm-alpine variant has no
    // web server (PHP-FPM speaks FastCGI on 9000 only), so the ALB health check on port 80 would
    // never pass. Same approach as the ConcreteCms, Dolphin, MyBB, and PhpBB specs.
    protected static final String DEFAULT_IMAGE = "php:8.2-apache";
    protected static final String EFS_DATA_PATH = "/october";
    protected static final String VOLUME_NAME = "octoberData";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "ctype", "tokenizer"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
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
    public int defaultHealthCheckGracePeriod() {
        return 180;
    }

    // ========== CmsSpec Implementation ==========

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
    public int phpMaxExecutionTime() {
        return 300;
    }

    @Override
    public String s3MediaPlugin() {
        return "league/flysystem-aws-s3-v3"; // Laravel Filesystem
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/storage/app/media";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/storage/app/media/*",
            "/themes/*/assets/*",
            "/plugins/*/assets/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // Laravel native cache
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "php /var/www/html/artisan schedule:run >> /dev/null 2>&1"
        );
    }

    @Override
    public String multisiteMode() {
        return "domain";
    }

    @Override
    public String cliTool() {
        return "artisan";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // Artisan is part of Laravel/October
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("october");
    }

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/nginx/access.log",
            "/var/log/nginx/error.log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/storage/logs/october.log",
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

        mountStorage(builder, context);

        builder.addCommands(
            "# Install October CMS",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    composer create-project october/october .",
            "    echo 'October CMS downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/storage",
            "chmod -R 775 " + ec2DataPath() + "/bootstrap/cache",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'October CMS installation complete' >> /var/log/userdata.log"
        );

        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        env.put("APP_ENV", "production");
        env.put("APP_DEBUG", "false");

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("APP_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        env.put("CACHE_DRIVER", "redis");
        env.put("SESSION_DRIVER", "redis");

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new OctoberCmsOidcIntegration();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for October CMS when using ALB-level OIDC.
     *
     * <p>October CMS administrative areas:</p>
     * <ul>
     *   <li>/backend - Backend administration panel</li>
     * </ul>
     *
     * @return list of October CMS administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        return List.of(
            "/backend/*"    // Backend administration panel
        );
    }
}
