package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.MyBBOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBB Forum ApplicationSpec implementation.
 *
 * <p>MyBB is a free, open-source forum software with a focus on
 * user-friendliness and extensibility. It has a loyal community
 * and extensive plugin system.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 7.3+ support (8.x recommended)</li>
 *   <li>MySQL/MariaDB/PostgreSQL/SQLite database</li>
 *   <li>Plugin and theme system</li>
 *   <li>Database failover support</li>
 *   <li>Task scheduler</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "mybb",
    category = "forum",
    displayName = "MyBB",
    description = "Free and open-source forum software",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "OAuth Plugin / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql", "sqlite"},
    supportsS3Media = false,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://mybb.com",
    defaultImage = "php:8.2-apache"
)
public class MyBBApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "php:8.2-apache";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl",
        "mbstring", "xml", "dom", "zip", "json", "opcache",
        "redis", "imagick", "fileinfo", "simplexml"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public String healthCheckPath() {
        return "/index.php";
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
    public boolean supportsS3MediaStorage() {
        return false;
    }

    @Override
    public String s3MediaPlugin() {
        return null;
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/uploads";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/images/*",
            "/jscripts/*",
            "/uploads/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // MyBB has built-in cache handlers
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "*/10 * * * *", String.format("curl -s '%s/task.php' > /dev/null 2>&1", siteUrl)
        );
    }

    @Override
    public String multisiteMode() {
        return "none";
    }

    @Override
    public String preferredWebServer() {
        return "apache";
    }

    @Override
    public String cliTool() {
        return null; // MyBB doesn't have a standard CLI
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of();
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        // MySQL 5.0 is no longer a valid RDS parameter group family; MyBB 1.8 supports 5.7.
        return DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("mybb");
    }

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/httpd/access_log",
            "/var/log/httpd/error_log",
            "/var/log/php-fpm/error.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults().withVersion("8.2");
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        installApache(builder);

        mountStorage(builder, context);

        builder.addCommands(
            "# Download MyBB",
            "cd " + ec2DataPath(),
            "if [ ! -f inc/config.php ]; then",
            "    curl -L https://github.com/mybb/mybb/releases/download/mybb_1836/mybb_1836.zip -o mybb.zip",
            "    unzip mybb.zip",
            "    mv Upload/* .",
            "    rm -rf Upload mybb.zip",
            "    echo 'MyBB downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 777 " + ec2DataPath() + "/cache",
            "chmod -R 777 " + ec2DataPath() + "/uploads",
            "chmod 666 " + ec2DataPath() + "/inc/config.php 2>/dev/null || true",
            "chmod 666 " + ec2DataPath() + "/inc/settings.php 2>/dev/null || true",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'MyBB installation complete' >> /var/log/userdata.log"
        );

        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("MYBB_DOMAIN", fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new MyBBOidcIntegration();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for MyBB when using ALB-level OIDC.
     *
     * <p>MyBB administrative areas:</p>
     * <ul>
     *   <li>/admin - Admin Control Panel</li>
     *   <li>/install - Installation directory</li>
     * </ul>
     *
     * @return list of MyBB administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        return List.of(
            "/admin/*",     // Admin Control Panel
            "/install/*"    // Installation directory
        );
    }
}
