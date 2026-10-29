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
public class JoomlaApplicationSpec extends AbstractCmsApplicationSpec implements DatabaseSpec {

    // ========== Constants ==========

    protected static final String DEFAULT_IMAGE = "joomla:5-php8.2-apache";

    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "pdo_pgsql", "gd", "curl", "mbstring",
        "xml", "dom", "zip", "intl", "json", "opcache", "redis",
        "imagick", "fileinfo", "simplexml"
    );

    // ========== ApplicationSpec Implementation ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
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
        return "plg_filesystem_s3"; // Joomla S3 filesystem plugin
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/images";
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
    public String objectCachePlugin() {
        return null; // Joomla has built-in cache handlers
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        return Map.of(
            "*/15 * * * *", "php /var/www/html/cli/joomla.php scheduler:run"
        );
    }

    @Override
    public String multisiteMode() {
        return "none";
    }

    @Override
    public String preferredWebServer() {
        return "apache"; // Joomla traditionally uses Apache with .htaccess
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

    // ========== EC2 Configuration ==========

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/httpd/access_log",
            "/var/log/httpd/error_log",
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
        installApache(builder);

        // Mount storage
        mountStorage(builder, context);

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
        installLogAgent(builder, context);
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        // Site name is always set, fqdn or not -- it (along with the four JOOMLA_ADMIN_* vars
        // below) must be present and non-empty for the official image's entrypoint to run a
        // fully non-interactive install instead of leaving its web installer wizard, including
        // the database-connection step, for a human to complete by hand.
        env.put("JOOMLA_SITE_NAME", fqdn != null && !fqdn.isBlank() ? fqdn : displayName());
        if (fqdn != null && !fqdn.isBlank()) {
            env.put("JOOMLA_SITE_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        // Initial admin account. JOOMLA_ADMIN_PASSWORD is deliberately absent here -- see
        // autoAdminPasswordEnvVar() below; it's delivered as an ECS Secret, never a plaintext
        // task-definition environment variable. Retrieve it the same way as the database
        // password: `aws secretsmanager get-secret-value` against this stack's secret.
        env.put("JOOMLA_ADMIN_USER", "Administrator");
        env.put("JOOMLA_ADMIN_USERNAME", "admin");
        env.put("JOOMLA_ADMIN_EMAIL", "admin@" + (fqdn != null && !fqdn.isBlank() ? fqdn : "example.com"));

        return env;
    }

    @Override
    public String autoAdminPasswordEnvVar() {
        return "JOOMLA_ADMIN_PASSWORD";
    }

    @Override
    public String autoAdminEmailEnvVar() {
        return "JOOMLA_ADMIN_EMAIL";
    }

    /** The default {@code CMD} of {@code joomla:5-php8.2-apache}; see {@link
     *  com.cloudforge.core.interfaces.ApplicationSpec#defaultContainerCommand} for why specs only
     *  override this for images whose default command is known. */
    @Override
    public List<String> defaultContainerCommand() {
        return List.of("apache2-foreground");
    }

    /** The entrypoint of {@code joomla:5-php8.2-apache}, which runs its first-run setup (copying
     *  Joomla's source, waiting on the database) only when {@code $1} matches {@code apache2*};
     *  see {@link com.cloudforge.core.interfaces.ApplicationSpec#defaultContainerEntrypoint()}. */
    @Override
    public String defaultContainerEntrypoint() {
        return "/entrypoint.sh";
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
