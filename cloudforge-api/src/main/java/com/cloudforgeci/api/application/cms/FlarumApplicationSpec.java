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
public class FlarumApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    // The -apache image bundles Apache + mod_php and serves port 80. The fpm-alpine variant has no
    // web server (PHP-FPM speaks FastCGI on 9000 only), so the ALB health check on port 80 would
    // never pass. Same approach as the ConcreteCms, Dolphin, MyBB, and PhpBB specs.
    protected static final String DEFAULT_IMAGE = "php:8.2-apache";
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "gd", "curl", "mbstring", "xml", "dom",
        "zip", "json", "opcache", "redis", "fileinfo", "tokenizer",
        "openssl"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 120;
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
    public String s3MediaPlugin() {
        return "fof/upload"; // FoF Upload extension with S3 driver
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/public/assets/files";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/assets/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // Laravel native cache
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "php /var/www/html/flarum schedule:run >> /dev/null 2>&1"
        );
    }

    @Override
    public String multisiteMode() {
        return "none";
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
        // MySQL 5.6 is no longer a valid RDS parameter group family; 5.7 is Flarum's next-supported version.
        return DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("flarum");
    }

    // ========== EC2 Configuration ==========

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

        mountStorage(builder, context);

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

        installLogAgent(builder, context);
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
    public List<String> cdnAdminPaths() {
        return List.of(
            "/admin/*"    // Admin dashboard
        );
    }
}
