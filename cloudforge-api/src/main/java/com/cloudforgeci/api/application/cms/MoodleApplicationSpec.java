package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.MoodleOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moodle LMS ApplicationSpec implementation.
 *
 * <p>Moodle is an open-source learning management system (LMS) used for
 * education and training. Compliance requirements depend on deployment and
 * operational configuration.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.0+ support (Moodle 4.x)</li>
 *   <li>MySQL/MariaDB/PostgreSQL database</li>
 *   <li>Native OIDC authentication plugin (Microsoft)</li>
 *   <li>LTI integration for external tools</li>
 *   <li>SCORM/xAPI compliance</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "moodle",
    category = "lms",
    displayName = "Moodle",
    description = "Open-source learning management system",
    phpVersion = "8.2",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "Native Microsoft OIDC Plugin",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://moodle.org",
    defaultImage = "moodlehq/moodle-php-apache:8.2"
)
public class MoodleApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "moodle";
    protected static final String DEFAULT_IMAGE = "moodlehq/moodle-php-apache:8.2";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/moodle";
    protected static final String VOLUME_NAME = "moodleData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "soap", "xmlrpc", "sodium", "exif"
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
        return "/login/index.php";
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
            "pm.max_children", "100",
            "pm.start_servers", "20",
            "pm.min_spare_servers", "10",
            "pm.max_spare_servers", "50",
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
        return 128;
    }

    @Override
    public int phpPostMaxSize() {
        return 128;
    }

    @Override
    public boolean supportsS3MediaStorage() {
        return true;
    }

    @Override
    public String s3MediaPlugin() {
        return null; // Moodle has built-in S3 filesystem support
    }

    @Override
    public String mediaUploadPath() {
        return "/var/moodledata";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/theme/*",
            "/lib/*",
            "/pluginfile.php/*"
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
        return null; // Moodle has native Redis support
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
            "* * * * *", "php /var/www/html/admin/cli/cron.php"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return false; // Moodle doesn't have native multi-tenancy
    }

    @Override
    public String multisiteMode() {
        return "none";
    }

    @Override
    public String cmsCategory() {
        return "lms";
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
        return "admin/cli/";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of Moodle
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("moodle");
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
            "/var/log/httpd/access_log",
            "/var/log/httpd/error_log",
            "/var/log/php-fpm/error.log",
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

        // Create moodledata directory
        builder.addCommands(
            "# Create Moodle data directory",
            "mkdir -p /var/moodledata",
            "chown apache:apache /var/moodledata",
            "chmod 775 /var/moodledata"
        );

        builder.addCommands(
            "# Download Moodle",
            "cd " + ec2DataPath(),
            "if [ ! -f config.php ]; then",
            "    curl -L https://download.moodle.org/download.php/direct/stable404/moodle-latest-404.tgz -o moodle.tgz",
            "    tar -xzf moodle.tgz --strip-components=1",
            "    rm moodle.tgz",
            "    echo 'Moodle downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'Moodle installation complete' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("MOODLE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        env.put("MOODLE_DATA", "/var/moodledata");

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new MoodleOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("application-oidc", "alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for Moodle when using ALB-level OIDC.
     *
     * <p>Moodle administrative areas:</p>
     * <ul>
     *   <li>/admin/* - Site administration</li>
     *   <li>/login/* - User login pages</li>
     * </ul>
     *
     * @return list of Moodle administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        // cdnMediaPaths() stays empty: mediaUploadPath() returns /var/moodledata, outside the web
        // root and not directly URL-reachable — Moodle serves files through pluginfile.php instead.
        return List.of(
            "/admin/*",    // Site administration
            "/login/*"     // Login pages
        );
    }
}
