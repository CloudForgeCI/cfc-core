package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.BagistoOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bagisto E-commerce ApplicationSpec implementation.
 *
 * <p>Bagisto is a free and open-source Laravel e-commerce framework
 * built on Laravel and Vue.js. It provides headless, mobile, and
 * classic commerce capabilities.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2 with Laravel 10.x</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>REST &amp; GraphQL APIs</li>
 *   <li>Vue.js Admin SPA</li>
 *   <li>Multi-inventory support</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "bagisto",
    category = "ecommerce",
    displayName = "Bagisto",
    description = "Laravel-based open-source e-commerce platform",
    phpVersion = "8.2",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "Laravel Socialite / Passport",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://bagisto.com",
    defaultImage = "php:8.2-fpm-alpine"
)
public class BagistoApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    // The -apache image bundles Apache + mod_php and serves port 80. The fpm-alpine variant has no
    // web server (PHP-FPM speaks FastCGI on 9000 only), so the ALB health check on port 80 would
    // never pass. Same approach as the ConcreteCms, Dolphin, MyBB, and PhpBB specs.
    protected static final String DEFAULT_IMAGE = "php:8.2-apache";
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "gd", "curl", "mbstring", "xml", "dom",
        "zip", "intl", "json", "opcache", "redis", "imagick",
        "fileinfo", "bcmath", "sodium", "tokenizer", "ctype"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 240;
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
            "pm.max_children", "75",
            "pm.start_servers", "10",
            "pm.min_spare_servers", "5",
            "pm.max_spare_servers", "35",
            "pm.max_requests", "500"
        );
    }

    @Override
    public Map<String, String> opcacheConfig() {
        return Map.of(
            "opcache.enable", "1",
            "opcache.memory_consumption", "256",
            "opcache.interned_strings_buffer", "32",
            "opcache.max_accelerated_files", "16000",
            "opcache.revalidate_freq", "0",
            "opcache.fast_shutdown", "1"
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
    public String s3MediaPlugin() {
        return "league/flysystem-aws-s3-v3"; // Laravel Filesystem
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/storage/app/public";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/storage/*",
            "/vendor/*",
            "/themes/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // Laravel native Redis support
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "php /var/www/html/artisan schedule:run >> /dev/null 2>&1 && php /var/www/html/artisan queue:work --stop-when-empty"
        );
    }

    @Override
    public String multisiteMode() {
        return "domain"; // Multi-inventory/channel support
    }

    @Override
    public String documentRoot() {
        return CONTAINER_DATA_PATH + "/public";
    }

    @Override
    public String cliTool() {
        return "artisan";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // Artisan is part of Laravel
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("bagisto");
    }

    @Override
    public int backupRetentionDays() {
        return 14;
    }

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/nginx/access.log",
            "/var/log/nginx/error.log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/storage/logs/laravel.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.forEcommerce();
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);
        PhpUserDataBuilder.installComposer(builder);

        // Install Node.js for Vue.js assets
        builder.addCommands(
            "# Install Node.js",
            "curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -",
            "dnf install -y nodejs",
            "echo 'Node.js installed' >> /var/log/userdata.log"
        );

        mountStorage(builder, context);

        builder.addCommands(
            "# Install Bagisto",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    composer create-project bagisto/bagisto .",
            "    npm install && npm run build",
            "    echo 'Bagisto downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/storage",
            "chmod -R 775 " + ec2DataPath() + "/bootstrap/cache",
            "",
            "# Create storage link",
            "php artisan storage:link",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'Bagisto installation complete' >> /var/log/userdata.log"
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

        // Laravel cache/session
        env.put("CACHE_DRIVER", "redis");
        env.put("SESSION_DRIVER", "redis");
        env.put("QUEUE_CONNECTION", "redis");

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new BagistoOidcIntegration();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for Bagisto when using ALB-level OIDC.
     *
     * <p>Bagisto administrative areas:</p>
     * <ul>
     *   <li>/admin - Store administration panel</li>
     * </ul>
     *
     * @return list of Bagisto administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        return List.of(
            "/admin/*"    // Store administration panel
        );
    }
}
