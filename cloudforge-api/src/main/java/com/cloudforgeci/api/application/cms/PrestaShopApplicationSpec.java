package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.PrestaShopOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PrestaShop E-commerce ApplicationSpec implementation.
 *
 * <p>PrestaShop is an open-source e-commerce platform used by
 * over 300,000 shops worldwide. It's particularly popular in
 * Europe and provides a complete online store solution.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.1+ with e-commerce extensions</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>Multi-store support</li>
 *   <li>Large module marketplace</li>
 *   <li>Built-in SEO features</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "prestashop",
    category = "ecommerce",
    displayName = "PrestaShop",
    description = "Open-source e-commerce platform for online stores",
    phpVersion = "8.1",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "OAuth Admin API Module",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://www.prestashop.com",
    defaultImage = "prestashop/prestashop:8-8.1-apache"
)
public class PrestaShopApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "prestashop";
    protected static final String DEFAULT_IMAGE = "prestashop/prestashop:8-8.1-apache";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/prestashop";
    protected static final String VOLUME_NAME = "prestashopData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.1";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "simplexml", "bcmath", "soap"
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
        return 240;
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
        return "amazons3"; // PrestaShop Amazon S3 module
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/img";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/img/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of("/themes/*", "/modules/*", "/js/*", "/css/*");
    }

    @Override
    public List<String> cdnAdminPaths() {
        // "/admin*" (no trailing slash) catches PrestaShop's randomized admin folder name
        // (e.g. admin123abc), a standard PrestaShop security convention.
        return List.of("/admin*", "/admin-dev/*", "/install/*");
    }

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        env.put("PS_REDIS_HOST", host);
        env.put("PS_REDIS_PORT", String.valueOf(port));
        env.put("PS_REDIS_DATABASE", "0");
        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        env.put("PS_DB_SERVER", host);
        env.put("PS_DB_PORT", String.valueOf(port));
        env.put("PS_DB_NAME", name);
        env.put("PS_DB_USER", user);
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
        return null; // PrestaShop has built-in Redis support
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
            "*/5 * * * *", "php /var/www/html/bin/console prestashop:cron:run"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "domain"; // PrestaShop multi-shop uses separate domains
    }

    @Override
    public String cmsCategory() {
        return "ecommerce";
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
        return "bin/console";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of PrestaShop
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("prestashop");
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
            "/var/www/html/var/logs/dev.log",
            "/var/www/html/var/logs/prod.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        // Install PHP
        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.forEcommerce().withVersion("8.1");
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        // Install Apache
        builder.addCommands(
            "# Install Apache",
            "dnf install -y httpd mod_ssl",
            "systemctl enable httpd",
            "echo 'Apache installed' >> /var/log/userdata.log"
        );

        PhpUserDataBuilder.installComposer(builder);

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

        // Set permissions
        builder.addCommands(
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/var",
            "chmod -R 775 " + ec2DataPath() + "/img",
            "chmod -R 775 " + ec2DataPath() + "/cache",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'PrestaShop installation complete' >> /var/log/userdata.log"
        );

        // Install CloudWatch agent
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        // PrestaShop settings
        if (fqdn != null && !fqdn.isBlank()) {
            env.put("PS_DOMAIN", fqdn);
            env.put("PS_ENABLE_SSL", sslEnabled ? "1" : "0");
        }

        env.put("PS_DEV_MODE", "0");
        env.put("PS_INSTALL_AUTO", "0");

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new PrestaShopOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("application-oidc", "alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

}
