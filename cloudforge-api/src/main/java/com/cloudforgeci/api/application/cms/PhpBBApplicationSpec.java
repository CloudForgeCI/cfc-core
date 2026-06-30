package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.PhpBBOidcIntegration;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * phpBB Forum ApplicationSpec implementation.
 *
 * <p>phpBB is the world's most popular open-source forum software with
 * a massive install base. It's known for stability, security, and
 * extensive customization options.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>PHP 7.2-8.3 support</li>
 *   <li>MySQL/MariaDB/PostgreSQL/SQLite database</li>
 *   <li>Built-in OAuth support (since 3.1)</li>
 *   <li>Extensive extension/MOD system</li>
 *   <li>Multiple language support</li>
 * </ul>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
@CmsPlugin(
    value = "phpbb",
    category = "forum",
    displayName = "phpBB",
    description = "World's most popular open-source forum software",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "OAuth Extension / ALB OIDC",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb", "postgresql", "sqlite"},
    supportsS3Media = false,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://www.phpbb.com",
    defaultImage = "php:8.2-apache"
)
public class PhpBBApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    protected static final String APPLICATION_ID = "phpbb";
    protected static final String DEFAULT_IMAGE = "php:8.2-apache";
    protected static final int APPLICATION_PORT = 8080;
    protected static final String CONTAINER_DATA_PATH = "/var/www/html";
    protected static final String EFS_DATA_PATH = "/phpbb";
    protected static final String VOLUME_NAME = "phpbbData";
    // null = container runs as root; Apache manages user switching internally
    protected static final String CONTAINER_USER = null;
    protected static final String EFS_PERMISSIONS = "755";

    protected static final String PHP_VERSION = "8.2";
    protected static final List<String> PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "pdo_pgsql", "pdo_sqlite", "gd", "curl",
        "mbstring", "xml", "dom", "zip", "json", "opcache",
        "redis", "imagick", "fileinfo", "simplexml"
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
        return "/index.php";
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        // Extended grace period for first-run initialization (apt-get + phpBB download)
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
        return 128;
    }

    @Override
    public int phpMaxExecutionTime() {
        return 60;
    }

    @Override
    public int phpUploadMaxFilesize() {
        return 16;
    }

    @Override
    public int phpPostMaxSize() {
        return 16;
    }

    @Override
    public boolean supportsS3MediaStorage() {
        return false; // phpBB stores attachments locally
    }

    @Override
    public String s3MediaPlugin() {
        return null;
    }

    @Override
    public String mediaUploadPath() {
        return "/var/www/html/files";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public List<String> cdnAssetPaths() {
        return List.of(
            "/styles/*",
            "/images/*",
            "/assets/*"
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
        return null; // phpBB has built-in cache drivers
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
            "*/5 * * * *", "php /var/www/html/bin/phpbbcli.php cron:run"
        );
    }

    @Override
    public boolean supportsMultisite() {
        return false;
    }

    @Override
    public String multisiteMode() {
        return "none";
    }

    @Override
    public String cmsCategory() {
        return "forum";
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
        return "bin/phpbbcli.php";
    }

    @Override
    public List<String> cliToolInstallCommands() {
        return List.of(); // CLI is included with phpBB
    }

    // ========== DatabaseSpec Implementation ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "5.7")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("phpbb");
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
            "/var/www/html/store/log_errors.txt",
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

        String cu = containerUser();
        String[] userParts = (cu != null) ? cu.split(":") : new String[]{"0", "0"};
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
            "# Download phpBB",
            "cd " + ec2DataPath(),
            "if [ ! -f config.php ]; then",
            "    curl -L https://download.phpbb.com/pub/release/3.3/3.3.14/phpBB-3.3.14.tar.bz2 -o phpbb.tar.bz2",
            "    tar -xjf phpbb.tar.bz2 --strip-components=1",
            "    rm phpbb.tar.bz2",
            "    echo 'phpBB downloaded' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R apache:apache " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 777 " + ec2DataPath() + "/cache",
            "chmod -R 777 " + ec2DataPath() + "/files",
            "chmod -R 777 " + ec2DataPath() + "/store",
            "chmod -R 777 " + ec2DataPath() + "/images/avatars/upload",
            "",
            "# Start services",
            "systemctl restart httpd php-fpm",
            "echo 'phpBB installation complete' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            env.put("PHPBB_SERVER_NAME", fqdn);
            env.put("PHPBB_SERVER_PORT", sslEnabled ? "443" : "80");
            env.put("PHPBB_SERVER_PROTOCOL", sslEnabled ? "https://" : "http://");
        }

        return env;
    }

    /**
     * Container environment variables with database connection.
     * Called by ContainerFactory when RDS database is configured.
     */
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode,
            DatabaseSpec.DatabaseConnection dbConnection) {

        Map<String, String> env = containerEnvironmentVariables(fqdn, sslEnabled, authMode);

        // Add database connection details for auto-install
        if (dbConnection != null) {
            env.put("PHPBB_DB_HOST", dbConnection.endpoint());
            env.put("PHPBB_DB_PORT", String.valueOf(dbConnection.port()));
            env.put("PHPBB_DB_NAME", dbConnection.databaseName());
            env.put("PHPBB_DB_USER", dbConnection.username());
            // Password is injected as ECS secret (PHPBB_DB_PASSWORD) by ContainerFactory
        }

        return env;
    }

    // ========== Container Configuration ==========

    @Override
    public List<String> containerCommand() {
        // Configure Apache to listen on port 8080 (matches APPLICATION_PORT)
        // Container runs as root; Apache drops privileges to www-data after binding
        // Also downloads phpBB on first run if not already present (persisted on EFS)
        return List.of("/bin/sh", "-c",
            // Configure Apache for port 8080
            "sed -i 's/Listen 80$/Listen 8080/' /etc/apache2/ports.conf && " +
            "sed -i 's/<VirtualHost \\*:80>/<VirtualHost *:8080>/' /etc/apache2/sites-available/*.conf && " +
            // Enable Apache modules for reverse proxy header handling
            "a2enmod remoteip headers rewrite && " +
            // Configure Apache to trust ALB X-Forwarded-* headers
            "echo 'RemoteIPHeader X-Forwarded-For' >> /etc/apache2/conf-available/remoteip.conf && " +
            "echo 'RemoteIPTrustedProxy 10.0.0.0/8' >> /etc/apache2/conf-available/remoteip.conf && " +
            "echo 'RemoteIPTrustedProxy 172.16.0.0/12' >> /etc/apache2/conf-available/remoteip.conf && " +
            "a2enconf remoteip && " +
            // Set HTTPS and fix SERVER_PORT when behind ALB
            "printf '%s\\n' " +
            "'SetEnvIf X-Forwarded-Proto https HTTPS=on' " +
            "'SetEnvIf X-Forwarded-Proto https SERVER_PORT=443' " +
            "'SetEnvIf X-Forwarded-Port ^(.*)$ SERVER_PORT=$1' " +
            "> /etc/apache2/conf-available/forwarded-https.conf && " +
            "a2enconf forwarded-https && " +
            // Fix Apache redirects to use correct port (strip :8080 from Location headers)
            "printf '%s\\n' " +
            "'<IfModule mod_headers.c>' " +
            "'  Header edit Location ^(https?://[^/]*):8080(.*)$ $1$2' " +
            "'</IfModule>' " +
            "> /etc/apache2/conf-available/fix-redirects.conf && " +
            "a2enconf fix-redirects && " +
            // Download phpBB if not already installed (first run with empty EFS)
            "if [ ! -f /var/www/html/config.php ] && [ ! -f /var/www/html/install/index.php ]; then " +
            "  echo 'phpBB not found, downloading...' && " +
            "  apt-get update && apt-get install -y --no-install-recommends bzip2 && " +
            "  curl -sL https://download.phpbb.com/pub/release/3.3/3.3.14/phpBB-3.3.14.tar.bz2 -o /tmp/phpbb.tar.bz2 && " +
            "  tar -xjf /tmp/phpbb.tar.bz2 -C /tmp && " +
            "  cp -r /tmp/phpBB3/* /var/www/html/ && " +
            "  rm -rf /tmp/phpbb.tar.bz2 /tmp/phpBB3 && " +
            "  chown -R www-data:www-data /var/www/html && " +
            "  chmod -R 755 /var/www/html && " +
            "  chmod -R 777 /var/www/html/cache /var/www/html/files /var/www/html/store /var/www/html/images/avatars/upload && " +
            "  echo 'phpBB installed successfully'; " +
            "fi && " +
            // Create autofill script that stores DB config in sessionStorage
            // and landing page that redirects to installer with values ready
            "if [ -n \"$PHPBB_DB_HOST\" ] && [ -d /var/www/html/install ]; then " +
            // Create JS file that fills form fields from sessionStorage
            "  cat > /var/www/html/install/autofill.js << 'JSEOF'\n" +
            "(function() {\n" +
            "  var config = sessionStorage.getItem('phpbb_db_config');\n" +
            "  if (!config) return;\n" +
            "  config = JSON.parse(config);\n" +
            "  function fill() {\n" +
            "    var filled = false;\n" +
            "    for (var name in config) {\n" +
            "      var el = document.querySelector('input[name=\"' + name + '\"]');\n" +
            "      if (el && !el.value) { el.value = config[name]; filled = true; }\n" +
            "    }\n" +
            "    var dbms = document.querySelector('select[name=\"dbms\"]');\n" +
            "    if (dbms && dbms.value !== 'mysqli') { dbms.value = 'mysqli'; filled = true; }\n" +
            "    return filled;\n" +
            "  }\n" +
            "  if (document.readyState === 'loading') {\n" +
            "    document.addEventListener('DOMContentLoaded', fill);\n" +
            "  } else { fill(); }\n" +
            "  setTimeout(fill, 500);\n" +
            "  setTimeout(fill, 1000);\n" +
            "})();\n" +
            "JSEOF\n" +
            // Create index.html landing page that prefills non-secret fields and redirects.
            // Password is intentionally excluded — never expose RDS secrets to the browser.
            "  cat > /var/www/html/install/index.html << EOF\n" +
            "<!DOCTYPE html>\n" +
            "<html><head><title>phpBB Installation</title>\n" +
            "<script>\n" +
            "sessionStorage.setItem('phpbb_db_config', JSON.stringify({\n" +
            "  dbhost: '$PHPBB_DB_HOST',\n" +
            "  dbport: '$PHPBB_DB_PORT',\n" +
            "  dbname: '$PHPBB_DB_NAME',\n" +
            "  dbuser: '$PHPBB_DB_USER'\n" +
            "}));\n" +
            "window.location.href = '/install/app.php';\n" +
            "</script>\n" +
            "</head><body>Redirecting to installer...</body></html>\n" +
            "EOF\n" +
            // Inject script tag into installer's main template
            "  TEMPLATE=/var/www/html/install/phpbb/style/installer_main.html && " +
            "  if [ -f \"$TEMPLATE\" ]; then " +
            "    sed -i 's|</head>|<script src=\"/install/autofill.js\"></script></head>|' \"$TEMPLATE\"; " +
            "  fi && " +
            "  echo 'Autofill configured'; " +
            "fi && " +
            // Start Apache
            "apache2-foreground"
        );
    }

    // ========== OIDC Support ==========

    @Override
    public OidcIntegration getOidcIntegration() {
        return new PhpBBOidcIntegration();
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for phpBB when using ALB-level OIDC.
     *
     * <p>phpBB has several sensitive administrative areas that should be protected:</p>
     * <ul>
     *   <li>/adm/* - Administrator Control Panel (full board administration)</li>
     *   <li>/install/* - Installation directory (should be removed after install,
     *       but protect it during initial setup)</li>
     * </ul>
     *
     * <p>This allows the main forum to be publicly accessible while requiring
     * authentication for administrative functions. Users can override or extend
     * these defaults via DeploymentContext.</p>
     *
     * <p>Note: phpBB's Moderator Control Panel (MCP) is accessed via index.php?i=mcp,
     * which can't easily be matched with ALB path patterns. MCP access is controlled
     * by phpBB's internal permissions.</p>
     *
     * @return list of phpBB administrative paths requiring authentication
     */
    @Override
    public List<String> protectedPaths() {
        return List.of(
            "/adm/*",      // Administrator Control Panel
            "/install/*"   // Installation directory (protect during setup)
        );
    }
}
