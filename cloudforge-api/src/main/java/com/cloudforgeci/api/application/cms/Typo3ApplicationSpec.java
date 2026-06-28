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
 * <p>TYPO3 is an enterprise-grade CMS very popular in Germany/Europe.
 * It's known for its robust architecture, security, and enterprise features.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 8.1-8.4 support (TYPO3 v12)</li>
 *   <li>MySQL/MariaDB/PostgreSQL/SQLite database</li>
 *   <li>Enterprise multi-site support</li>
 *   <li>Robust security and compliance</li>
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
    description = "Enterprise CMS popular in Germany and Europe",
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
public class Typo3ApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "typo3";
    protected static final String DEFAULT_IMAGE = "martinhelmich/typo3:12";
    protected static final int APPLICATION_PORT = 80;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/typo3";
    protected static final String VOLUME_NAME = "typo3Data";
    protected static final String CONTAINER_USER = "33:33";
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "simplexml", "soap", "sodium"
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
        return "/typo3/";
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
        return "t3g/aws-sdk"; // TYPO3 AWS SDK extension
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/fileadmin";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnAssetPaths() {
        return List.of(
            "/fileadmin/*",
            "/typo3temp/*",
            "/typo3conf/ext/*/Resources/Public/*"
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
        return null; // TYPO3 has native Redis support
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
            "* * * * *", "php /var/www/html/vendor/bin/typo3 scheduler:run"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return true;
    }

    @Override
    public String multisiteMode() {
        return "domain"; // TYPO3 supports multi-domain setups
    }

    @Override
    public String cmsCategory() {
        return "cms";
    }

    @Override
    public String preferredWebServer() {
        return "nginx";
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

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
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

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
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
    public List<String> protectedPaths() {
        return List.of(
            "/typo3/*"    // Backend administration
        );
    }
}
