package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.JoomlaOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Joomla CMS ApplicationSpec implementation.
 *
 * <p>Joomla is a flexible, open-source CMS used for building websites
 * and web applications. It has a large extension ecosystem and is
 * popular for corporate websites, portals, and community sites.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2 with standard extensions</li>
 *   <li>MySQL/MariaDB/PostgreSQL database support</li>
 *   <li>Built-in multilingual support</li>
 *   <li>Extensive template system</li>
 *   <li>Large extension marketplace</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "joomla",
    category = "cms",
    displayName = "Joomla",
    description = "Flexible CMS for websites and web applications",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "miniOrange OIDC Plugin",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://www.joomla.org",
    defaultImage = "joomla:5-php8.2-apache"
)
public class JoomlaApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "joomla";
    protected static final String DEFAULT_IMAGE = "joomla:5-php8.2-apache";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/joomla";
    protected static final String VOLUME_NAME = "joomlaData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "pdo_pgsql", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "simplexml"
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
        return "/administrator/index.php";
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
        return "plg_filesystem_s3"; // Joomla S3 filesystem plugin
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/images";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/images/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of("/media/*", "/templates/*", "/components/*");
    }

    @Override
    public List<String> cdnAdminPaths() {
        return List.of("/administrator/*");
    }

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        env.put("JOOMLA_REDIS_HOST", host);
        env.put("JOOMLA_REDIS_PORT", String.valueOf(port));
        env.put("JOOMLA_REDIS_DATABASE", "0");
        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        env.put("JOOMLA_DB_HOST", host);
        env.put("JOOMLA_DB_PORT", String.valueOf(port));
        env.put("JOOMLA_DB_NAME", name);
        env.put("JOOMLA_DB_USER", user);
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
        return null; // Joomla has built-in cache handlers
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
            "*/15 * * * *", "php /var/www/html/cli/joomla.php scheduler:run"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return false; // Joomla doesn't have native multisite
    }

    @Override
    public String multisiteMode() {
        return "none";
    }

    @Override
    public String cmsCategory() {
        return "cms";
    }

    @Override
    public String preferredWebServer() {
        return "apache"; // Joomla traditionally uses Apache with .htaccess
    }

    @Override
    public String documentRoot() {
        return CONTAINER_DATA_PATH;
    }

    @Override
    public String cliTool() {
        return "cli/joomla.php";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of Joomla installation
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("joomla");
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
            "/var/www/html/administrator/logs/error.php",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        // Install PHP
        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults().withVersion("8.2");
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        // Install Apache instead of NGINX for Joomla
        builder.addCommands(
            "# Install Apache",
            "dnf install -y httpd mod_ssl",
            "systemctl enable httpd",
            "echo 'Apache installed' >> /var/log/userdata.log"
        );

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

        // Download Joomla
        builder.addCommands(
            "# Download Joomla",
            "cd " + ec2DataPath(),
            "if [ ! -f configuration.php ]; then",
            "    curl -L https://downloads.joomla.org/cms/joomla5/5-0-0/Joomla_5.0.0-Stable-Full_Package.tar.gz -o joomla.tar.gz",
            "    tar -xzf joomla.tar.gz",
            "    rm joomla.tar.gz",
            "    echo 'Joomla downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'Joomla installation complete' >> /var/log/userdata.log"
        );

        // Install CloudWatch agent
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        // Joomla settings
        if (fqdn != null && !fqdn.isBlank()) {
            env.put("JOOMLA_SITE_NAME", fqdn);
            env.put("JOOMLA_SITE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new JoomlaOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("application-oidc", "alb-oidc", "none");
    }
}
