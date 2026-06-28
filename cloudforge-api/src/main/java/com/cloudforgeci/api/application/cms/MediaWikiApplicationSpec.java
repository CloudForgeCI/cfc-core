package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.MediaWikiOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MediaWiki ApplicationSpec implementation.
 *
 * <p>MediaWiki is the wiki software that powers Wikipedia. It's used by
 * governments, enterprises, and organizations for documentation and
 * knowledge management.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2+ support</li>
 *   <li>MySQL/MariaDB/PostgreSQL/SQLite database</li>
 *   <li>PluggableAuth extension for OIDC</li>
 *   <li>Extensive extension ecosystem</li>
 *   <li>Semantic MediaWiki support</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "mediawiki",
    category = "wiki",
    displayName = "MediaWiki",
    description = "Wiki software powering Wikipedia",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "OpenID Connect Extension (PluggableAuth)",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql", "sqlite"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://www.mediawiki.org",
    defaultImage = "mediawiki:1.42"
)
public class MediaWikiApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "mediawiki";
    protected static final String DEFAULT_IMAGE = "mediawiki:1.42";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/mediawiki";
    protected static final String VOLUME_NAME = "mediawikiData";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "calendar", "sodium", "iconv"
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
        return "/index.php/Main_Page";
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
        return "AWS"; // MediaWiki AWS extension
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
    public List<String> cdnAssetPaths() {
        return List.of(
            "/images/*",
            "/skins/*",
            "/resources/*"
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
        return null; // MediaWiki has native cache backends
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
            "*/5 * * * *", "php /var/www/html/maintenance/runJobs.php --maxtime=60"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "domain"; // Wiki farm support
    }

    @Override
    public String cmsCategory() {
        return "wiki";
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
        return "maintenance/";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI scripts are part of MediaWiki
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "5.7")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("mediawiki");
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
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults().withVersion("8.2");
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        builder.addCommands(
            "# Install Apache",
            "dnf install -y httpd mod_ssl",
            "systemctl enable httpd",
            "echo 'Apache installed' >> /var/log/userdata.log"
        );

        // Install ImageMagick for thumbnailing
        builder.addCommands(
            "# Install ImageMagick",
            "dnf install -y ImageMagick",
            "echo 'ImageMagick installed' >> /var/log/userdata.log"
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
            "# Download MediaWiki",
            "cd " + ec2DataPath(),
            "if [ ! -f LocalSettings.php ]; then",
            "    curl -L https://releases.wikimedia.org/mediawiki/1.42/mediawiki-1.42.3.tar.gz -o mediawiki.tar.gz",
            "    tar -xzf mediawiki.tar.gz --strip-components=1",
            "    rm mediawiki.tar.gz",
            "    echo 'MediaWiki downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/images",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'MediaWiki installation complete' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("MEDIAWIKI_SERVER", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new MediaWikiOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for MediaWiki when using ALB-level OIDC.
     *
     * <p>MediaWiki administrative areas:</p>
     * <ul>
     *   <li>/mw-config - Installation wizard</li>
     * </ul>
     *
     * <p>Note: MediaWiki admin functions (Special:UserRights, Special:BlockUser, etc.)
     * are accessed via Special pages within the wiki, not separate URL paths.
     * These are protected by MediaWiki's internal permissions system.</p>
     *
     * @return list of MediaWiki administrative paths requiring authentication
     */
    @Override
    public List<String> protectedPaths() {
        return List.of(
            "/mw-config/*"    // Installation wizard
        );
    }
}
