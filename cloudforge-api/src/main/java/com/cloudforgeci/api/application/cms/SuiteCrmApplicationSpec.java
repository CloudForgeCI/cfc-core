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
public class SuiteCrmApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "suitecrm";
    protected static final String DEFAULT_IMAGE = "bitnami/suitecrm:8";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/suitecrm";
    protected static final String VOLUME_NAME = "suitecrmData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "imap"
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
        return "/index.php";
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
    public int phpUploadMaxFilesize() {
        return 64;
    }

    @Override
    public int phpPostMaxSize() {
        return 64;
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
    public boolean supportsCdnIntegration() {
        return true;
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
    public boolean supportsObjectCache() {
        return true;
    }

    @Override
    public String preferredCacheBackend() {
        return "redis";
    }

    @Override
    public String objectCachePlugin() {
        return null; // SuiteCRM has built-in cache handlers
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
            "* * * * *", "cd /var/www/html && php -f cron.php > /dev/null 2>&1"
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
        return "crm";
    }

    @Override
    public String preferredWebServer() {
        return "apache";
    }

    @Override
    public String documentRoot() {
        return CONTAINER_DATA_PATH;
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
            "/var/log/apache2/access.log",
            "/var/log/apache2/error.log",
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

        builder.addCommands(
            "# Install Apache",
            "dnf install -y httpd mod_ssl",
            "systemctl enable httpd",
            "echo 'Apache installed' >> /var/log/userdata.log"
        );

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

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
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

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
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
