package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.Typo3OidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TYPO3 CMS ApplicationSpec implementation.
 *
 * <p>TYPO3 is a CMS with multisite, role-based access, and long-term-support
 * release options.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.1-8.4 support (TYPO3 v12)</li>
 *   <li>MySQL/MariaDB/PostgreSQL/SQLite database</li>
 *   <li>Multi-site support</li>
 *   <li>Long-term support (ELTS) versions</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "typo3",
    category = "cms",
    displayName = "TYPO3",
    description = "CMS with multisite and long-term-support options",
    phpVersion = "8.2",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "miniOrange OIDC Extension / Causal OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql", "sqlite"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://typo3.org",
    defaultImage = "martinhelmich/typo3:12"
)
public class Typo3ApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "martinhelmich/typo3:12";
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "simplexml", "soap", "sodium"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public String healthCheckPath() {
        return "/typo3/";
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
            "opcache.max_accelerated_files", "20000",
            "opcache.revalidate_freq", "60",
            "opcache.fast_shutdown", "1",
            "pcre.jit", "1"  // Required by TYPO3
        );
    }

    @Override
    public int phpMemoryLimit() {
        return 512;
    }

    @Override
    public int phpMaxExecutionTime() {
        return 240;
    }

    @Override
    public String s3MediaPlugin() {
        return "t3g/aws-sdk"; // TYPO3 AWS SDK extension
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/fileadmin";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/fileadmin/*",
            "/typo3temp/*",
            "/typo3conf/ext/*/Resources/Public/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // TYPO3 has native Redis support
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "php /var/www/html/vendor/bin/typo3 scheduler:run"
        );
    }

    @Override
    public String multisiteMode() {
        return "domain"; // TYPO3 supports multi-domain setups
    }

    @Override
    public String documentRoot() {
        return CONTAINER_DATA_PATH + "/public";
    }

    @Override
    public String cliTool() {
        return "vendor/bin/typo3";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of TYPO3
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("typo3");
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
            "/var/www/html/var/log/typo3_*.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults()
            .withVersion("8.2")
            .withMemoryLimit(512);
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);
        PhpUserDataBuilder.installComposer(builder);

        mountStorage(builder, context);

        builder.addCommands(
            "# Install TYPO3",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    composer create-project typo3/cms-base-distribution .",
            "    echo 'TYPO3 downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/var",
            "chmod -R 775 " + ec2DataPath() + "/public/fileadmin",
            "chmod -R 775 " + ec2DataPath() + "/public/typo3temp",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'TYPO3 installation complete' >> /var/log/userdata.log"
        );

        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        env.put("TYPO3_CONTEXT", "Production");

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("TYPO3_BASE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new Typo3OidcIntegration();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for TYPO3 when using ALB-level OIDC.
     *
     * <p>TYPO3 administrative areas:</p>
     * <ul>
     *   <li>/typo3 - Backend administration</li>
     * </ul>
     *
     * @return list of TYPO3 administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        return List.of(
            "/typo3/*"    // Backend administration
        );
    }
}
