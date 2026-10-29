package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.DolphinOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dolphin/UNA Social Network ApplicationSpec implementation.
 *
 * <p>UNA (formerly Dolphin) is an open-source social network platform
 * for building community sites, dating sites, and social networks.
 * It's similar to SocialEngine but open source.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.2 support</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>Modular app system</li>
 *   <li>Real-time messaging</li>
 *   <li>Video streaming support</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "dolphin-una",
    category = "social",
    displayName = "UNA (Dolphin)",
    description = "Open-source social network platform",
    phpVersion = "8.2",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "OAuth Connect App / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://una.io",
    defaultImage = "php:8.2-apache"
)
public class DolphinApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "php:8.2-apache";
    protected static final String EFS_DATA_PATH = "/una";
    protected static final String VOLUME_NAME = "unaData";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "exif", "bcmath"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
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
    public int defaultHealthCheckGracePeriod() {
        return 240;
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
        return 300;
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
    public String s3MediaPlugin() {
        return "bx_aws_s3"; // UNA AWS S3 app
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/storage";
    }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/storage/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of("/template/*", "/modules/*/template/*", "/plugins_public/*");
    }

    @Override
    public List<String> cdnAdminPaths() {
        // UNA uses /studio/ for the admin panel; /install/* should be blocked post-setup
        return List.of("/studio/*", "/install/*");
    }

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        env.put("BX_REDIS_HOST", host);
        env.put("BX_REDIS_PORT", String.valueOf(port));
        env.put("BX_REDIS_DATABASE", "0");
        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host);
        env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name);
        env.put("DB_USER", user);
        env.put("UNA_DB_HOST", host);
        env.put("UNA_DB_PORT", String.valueOf(port));
        env.put("UNA_DB_NAME", name);
        env.put("UNA_DB_USER", user);
        return env;
    }

    @Override
    public String objectCachePlugin() {
        return null; // UNA has built-in cache support
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "php /var/www/html/periodic/cron.php"
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
        return null; // UNA doesn't have a standard CLI
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of();
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withInstanceClass("db.t3.small")
            .withStorage(50)
            .withDatabaseName("una");
    }

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/httpd/access_log",
            "/var/log/httpd/error_log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/logs/una.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.forSocial();
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        installApache(builder);

        // Install FFmpeg for video processing
        builder.addCommands(
            "# Install FFmpeg for video processing",
            "dnf install -y ffmpeg",
            "echo 'FFmpeg installed' >> /var/log/userdata.log"
        );

        mountStorage(builder, context);

        builder.addCommands(
            "# Download UNA",
            "cd " + ec2DataPath(),
            "if [ ! -f inc/header.inc.php ]; then",
            "    curl -L https://github.com/unacms/una/releases/download/14.0.0/UNA-14.0.0.zip -o una.zip",
            "    unzip una.zip",
            "    rm una.zip",
            "    echo 'UNA downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 777 " + ec2DataPath() + "/storage",
            "chmod -R 777 " + ec2DataPath() + "/cache",
            "chmod -R 777 " + ec2DataPath() + "/cache_public",
            "chmod -R 777 " + ec2DataPath() + "/tmp",
            "chmod -R 777 " + ec2DataPath() + "/logs",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'UNA installation complete' >> /var/log/userdata.log"
        );

        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("UNA_SITE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new DolphinOidcIntegration();
    }

}
