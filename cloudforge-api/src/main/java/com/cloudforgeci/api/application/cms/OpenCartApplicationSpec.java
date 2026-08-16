package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.OpenCartOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenCart E-commerce ApplicationSpec implementation.
 *
 * <p>OpenCart is a lightweight, open-source e-commerce platform popular
 * with small to medium businesses. It offers a simple interface and
 * extensive extension marketplace.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.1+ support (OpenCart 4.x)</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>Multi-store support</li>
 *   <li>Large extension marketplace</li>
 *   <li>Simple, user-friendly admin</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "opencart",
    category = "ecommerce",
    displayName = "OpenCart",
    description = "Lightweight e-commerce platform for SMB online stores",
    phpVersion = "8.1",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "OAuth Extension / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://www.opencart.com",
    defaultImage = "vimagick/opencart:latest"
)
public class OpenCartApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "opencart";
    protected static final String DEFAULT_IMAGE = "vimagick/opencart:latest";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/opencart";
    protected static final String VOLUME_NAME = "opencartData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.1";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "zlib"
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
        return 180;
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
        return 256;
    }

    @Override
    public int phpMaxExecutionTime() {
        return 300;
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
        return null; // Custom extension required
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/image";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/image/*",
            "/catalog/*",
            "/extension/*"
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
        return null; // Custom cache extension
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
            "0 * * * *", String.format("curl -s '%s/index.php?route=api/cron' > /dev/null 2>&1", siteUrl)
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "domain";
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
        return null; // OpenCart doesn't have a standard CLI
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of();
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("opencart");
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
            "/var/log/apache2/access.log",
            "/var/log/apache2/error.log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/system/storage/logs/error.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults().withVersion("8.1");
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        builder.addCommands(
            "# Install Apache",
            "dnf install -y httpd mod_ssl",
            "systemctl enable httpd",
            "echo 'Apache installed' >> /var/log/userdata.log"
        );

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
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'OpenCart installation complete' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("OPENCART_HOST", fqdn);
            env.put("OPENCART_USE_SSL", sslEnabled ? "true" : "false");
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new OpenCartOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for OpenCart when using ALB-level OIDC.
     *
     * <p>OpenCart administrative areas:</p>
     * <ul>
     *   <li>/admin - Store administration panel</li>
     * </ul>
     *
     * @return list of OpenCart administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        return List.of(
            "/admin/*"    // Store administration panel
        );
    }
}
