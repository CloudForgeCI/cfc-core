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
public class MediaWikiApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "mediawiki:1.42";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "calendar", "sodium", "iconv"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    /**
     * Only the uploads persist. The official image keeps MediaWiki's code in {@code /var/www/html}, and an
     * empty volume mounted there hides it, so Apache serves an empty directory and answers 403.
     */
    @Override
    public String containerDataPath() {
        return "/var/www/html/images";
    }

    @Override
    public String documentRoot() {
        return "/var/www/html";
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
    public int phpMaxExecutionTime() {
        return 300;
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
    public List<String> cdnStaticPaths() {
        return List.of(
            "/images/*",
            "/skins/*",
            "/resources/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // MediaWiki has native cache backends
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "*/5 * * * *", "php /var/www/html/maintenance/runJobs.php --maxtime=60"
        );
    }

    @Override
    public String multisiteMode() {
        return "domain"; // Wiki farm support
    }

    @Override
    public String preferredWebServer() {
        return "apache";
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
            .withDefaultVersion("8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("mediawiki");
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

        // Install ImageMagick for thumbnailing
        builder.addCommands(
            "# Install ImageMagick",
            "dnf install -y ImageMagick",
            "echo 'ImageMagick installed' >> /var/log/userdata.log"
        );

        mountStorage(builder, context);

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

        installLogAgent(builder, context);
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
    public List<String> cdnAdminPaths() {
        return List.of(
            "/mw-config/*"    // Installation wizard
        );
    }
}
