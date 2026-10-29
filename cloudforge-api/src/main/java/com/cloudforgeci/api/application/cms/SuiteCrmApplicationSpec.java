package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.SuiteCrmOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SuiteCRM ApplicationSpec implementation.
 *
 * <p>SuiteCRM is a free, open-source CRM (Customer Relationship Management)
 * platform. It's a fork of SugarCRM and provides enterprise-level CRM
 * capabilities without licensing costs.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.1-8.3 support (v8.x)</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>REST API v8</li>
 *   <li>Workflow automation</li>
 *   <li>Reporting and analytics</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "suitecrm",
    category = "crm",
    displayName = "SuiteCRM",
    description = "Open-source enterprise CRM platform",
    phpVersion = "8.2",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "OAuth2 / LDAP / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = false,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://suitecrm.com",
    defaultImage = "bitnami/suitecrm:8"
)
public class SuiteCrmApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "bitnami/suitecrm:8";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "imap"
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
            "opcache.revalidate_freq", "60",
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
    public boolean supportsS3MediaStorage() {
        return false; // SuiteCRM stores files locally
    }

    @Override
    public String s3MediaPlugin() {
        return null;
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/upload";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/themes/*",
            "/include/*",
            "/cache/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // SuiteCRM has built-in cache handlers
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "cd /var/www/html && php -f cron.php > /dev/null 2>&1"
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
        return "bin/console"; // SuiteCRM 8 uses Symfony console
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of SuiteCRM
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("suitecrm");
    }

    @Override
    public int backupRetentionDays() {
        return 14;
    }

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/httpd/access_log",
            "/var/log/httpd/error_log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/logs/suitecrm.log",
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

        installApache(builder);

        PhpUserDataBuilder.installComposer(builder);

        mountStorage(builder, context);

        builder.addCommands(
            "# Download SuiteCRM",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    curl -L https://suitecrm.com/download/suitecrm-8-latest -o suitecrm.zip",
            "    unzip suitecrm.zip",
            "    rm suitecrm.zip",
            "    composer install --no-dev",
            "    echo 'SuiteCRM downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/cache",
            "chmod -R 775 " + ec2DataPath() + "/upload",
            "chmod -R 775 " + ec2DataPath() + "/logs",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'SuiteCRM installation complete' >> /var/log/userdata.log"
        );

        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("SUITECRM_HOST", fqdn);
            env.put("SUITECRM_SITE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new SuiteCrmOidcIntegration();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for SuiteCRM when using ALB-level OIDC.
     *
     * <p>SuiteCRM administrative areas:</p>
     * <ul>
     *   <li>/api - Backend API (SuiteCRM 8.x)</li>
     *   <li>/install.php - Installation script</li>
     * </ul>
     *
     * <p>Note: SuiteCRM 8.x uses an Angular SPA with hash-based routing (/#/admin).
     * ALB path patterns cannot match hash routes, so API-level protection is used.
     * Full admin protection may require application-level authentication.</p>
     *
     * @return list of SuiteCRM administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        // /api/* belongs here rather than cdnStaticPaths: API responses must never be
        // edge-cached and need full session/header forwarding.
        return List.of(
            "/api/*",         // Backend API
            "/install.php"    // Installation script
        );
    }
}
