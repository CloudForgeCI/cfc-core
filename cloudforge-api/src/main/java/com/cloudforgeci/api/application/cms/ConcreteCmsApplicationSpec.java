package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.ConcreteCmsOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete CMS ApplicationSpec implementation.
 *
 * <p>Concrete CMS (formerly concrete5) provides in-context editing,
 * accessibility features, and add-on support.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 7.3-8.x support (v9)</li>
 *   <li>MySQL/MariaDB database</li>
 *   <li>In-context editing</li>
 *   <li>Accessibility features</li>
 *   <li>Marketplace for add-ons</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "concrete-cms",
    category = "cms",
    displayName = "Concrete CMS",
    description = "CMS with in-context editing and accessibility features",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "OAuth Add-on / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = true,
    websiteUrl = "https://www.concretecms.org",
    defaultImage = "php:8.2-apache"
)
public class ConcreteCmsApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "php:8.2-apache";
    protected static final String EFS_DATA_PATH = "/concrete";
    protected static final String VOLUME_NAME = "concreteData";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml",
        "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "simplexml"
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
        return null; // Custom S3 filesystem driver
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/application/files";
    }

    @Override
    public List<String> cdnStaticPaths() {
        return List.of(
            "/application/files/*",
            "/concrete/css/*",
            "/concrete/js/*",
            "/packages/*/css/*",
            "/packages/*/js/*"
        );
    }

    @Override
    public String objectCachePlugin() {
        return null; // Concrete has built-in cache drivers
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "* * * * *", "php /var/www/html/concrete/bin/concrete jobs:run"
        );
    }

    @Override
    public String multisiteMode() {
        return "subdirectory";
    }

    @Override
    public String preferredWebServer() {
        return "apache";
    }

    @Override
    public String cliTool() {
        return "concrete/bin/concrete";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is part of Concrete CMS
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("concrete");
    }

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/httpd/access_log",
            "/var/log/httpd/error_log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/application/files/log.txt",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults().withVersion("8.2");
        PhpUserDataBuilder.installPhp(builder, phpConfig);

        installApache(builder);

        PhpUserDataBuilder.installComposer(builder);

        mountStorage(builder, context);

        builder.addCommands(
            "# Download Concrete CMS",
            "cd " + ec2DataPath(),
            "if [ ! -f composer.json ]; then",
            "    composer create-project concrete5/composer .",
            "    echo 'Concrete CMS downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/application/config",
            "chmod -R 775 " + ec2DataPath() + "/application/files",
            "chmod -R 775 " + ec2DataPath() + "/packages",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'Concrete CMS installation complete' >> /var/log/userdata.log"
        );

        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("CONCRETE_BASE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        return env;
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new ConcreteCmsOidcIntegration();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for Concrete CMS when using ALB-level OIDC.
     *
     * <p>Concrete CMS administrative areas:</p>
     * <ul>
     *   <li>/dashboard - Site dashboard and administration</li>
     *   <li>/ccm - Core system routes</li>
     * </ul>
     *
     * @return list of Concrete CMS administrative paths requiring authentication
     */
    @Override
    public List<String> cdnAdminPaths() {
        return List.of(
            "/dashboard/*",    // Site dashboard
            "/ccm/*"           // Core system routes
        );
    }
}
