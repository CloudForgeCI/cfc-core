package com.cloudforgeci.samples.plugins.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforgeci.api.core.PhpUserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Craft CMS ApplicationSpec — example CmsSpec plugin for cloudforge-sample.
 *
 * <p>Craft CMS is a flexible, user-friendly content management system built on
 * the Yii2 framework. Used by agencies, media companies, and enterprises that
 * need fine-grained content modelling without the constraints of a traditional CMS.</p>
 *
 * <h2>Key characteristics:</h2>
 * <ul>
 *   <li>Document root is <code>/var/www/html/web</code> — the <code>web/</code>
 *       subdirectory, not the application root. Getting this wrong breaks the install.</li>
 *   <li>MySQL 8.0+ or PostgreSQL 14+ required (no optional database)</li>
 *   <li>Redis caching supported natively since Craft 4</li>
 *   <li>Official AWS S3 plugin (<code>craftcms/aws-s3</code>) for media offloading</li>
 *   <li>Queue runner (<code>php craft queue/run</code>) handles async jobs — system cron
 *       is preferred over Craft's internal runner for production</li>
 *   <li>OIDC via <code>verbb/auth</code> plugin</li>
 *   <li>Admin panel path configurable via <code>CRAFT_CP_PATH</code> env var (default: <code>admin</code>)</li>
 * </ul>
 *
 * <h2>This plugin demonstrates:</h2>
 * <ul>
 *   <li>Implementing CmsSpec + DatabaseSpec outside cloudforge-api</li>
 *   <li>CMS with a non-root document root (web/ subdirectory)</li>
 *   <li>Queue-based async processing wired to system cron</li>
 *   <li>Craft-native environment variable naming conventions</li>
 * </ul>
 *
 * <h2>Deployment:</h2>
 * <ul>
 *   <li><b>Fargate:</b> {@code craftcms/nginx:8.2} (PHP-FPM + nginx bundled)</li>
 *   <li><b>EC2:</b> php8.2-fpm + nginx installed via user data</li>
 * </ul>
 *
 * @since 3.1.0
 * @see com.cloudforgeci.api.application.cms.WordPressApplicationSpec
 */
@CmsPlugin(
    value = "craft-cms",
    category = "cms",
    displayName = "Craft CMS",
    description = "Flexible, user-friendly CMS for content-driven websites and custom publishing",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsOidc = true,
    oidcMethod = "verbb/auth plugin (OAuth2/OIDC)",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "pgsql"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://craftcms.com",
    defaultImage = "craftcms/nginx:8.2"
)
public class CraftCmsApplicationSpec implements CmsSpec, DatabaseSpec {

    // ========== Constants ==========

    private static final String APPLICATION_ID = "craft-cms";
    private static final String DEFAULT_IMAGE = "craftcms/nginx:8.2";
    private static final int APPLICATION_PORT = 8080;

    // Application root — Craft files live here (composer, templates, config, storage)
    private static final String CONTAINER_DATA_PATH = "/var/www/html";

    // Public web root — Craft's index.php and uploaded assets live here
    // This is what nginx's document root points at, NOT the app root.
    private static final String DOCUMENT_ROOT = "/var/www/html/web";

    private static final String EFS_DATA_PATH = "/craft";
    private static final String VOLUME_NAME = "craftData";
    private static final String CONTAINER_USER = "33:33"; // www-data UID:GID
    private static final String EFS_PERMISSIONS = "755";

    private static final String PHP_VERSION = "8.2";
    private static final List<String> PHP_EXTENSIONS = List.of(
        "bcmath", "curl", "dom", "gd", "iconv", "imagick", "intl",
        "json", "mbstring", "openssl", "pcre", "pdo", "pdo_mysql",
        "pdo_pgsql", "reflection", "spl", "zip", "redis"
    );

    // ========== ApplicationSpec ==========

    @Override
    public String applicationId() { return APPLICATION_ID; }

    @Override
    public String defaultContainerImage() { return DEFAULT_IMAGE; }

    @Override
    public int applicationPort() { return APPLICATION_PORT; }

    @Override
    public String containerDataPath() { return CONTAINER_DATA_PATH; }

    @Override
    public String documentRoot() { return DOCUMENT_ROOT; }

    @Override
    public String efsDataPath() { return EFS_DATA_PATH; }

    @Override
    public String volumeName() { return VOLUME_NAME; }

    @Override
    public String containerUser() { return CONTAINER_USER; }

    @Override
    public String efsPermissions() { return EFS_PERMISSIONS; }

    @Override
    public String healthCheckPath() {
        // Craft 4+ provides a built-in health check endpoint
        return "/actions/app/health-check";
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 120;
    }

    // ========== CmsSpec — PHP Runtime ==========

    @Override
    public String phpVersion() { return PHP_VERSION; }

    @Override
    public List<String> requiredPhpExtensions() { return PHP_EXTENSIONS; }

    @Override
    public int phpMemoryLimit() { return 256; }

    @Override
    public int phpMaxExecutionTime() { return 120; }

    @Override
    public int phpUploadMaxFilesize() { return 100; }

    @Override
    public int phpPostMaxSize() { return 100; }

    @Override
    public Map<String, String> phpFpmConfig() {
        return Map.of(
            "pm", "dynamic",
            "pm.max_children", "20",
            "pm.start_servers", "4",
            "pm.min_spare_servers", "2",
            "pm.max_spare_servers", "6",
            "pm.max_requests", "500"
        );
    }

    // ========== CmsSpec — Media Storage ==========

    @Override
    public boolean supportsS3MediaStorage() { return true; }

    @Override
    public String s3MediaPlugin() { return "craftcms/aws-s3"; }

    @Override
    public String mediaUploadPath() { return "/var/www/html/web/uploads"; }

    // ========== CmsSpec — CDN ==========

    @Override
    public boolean supportsCdnIntegration() { return true; }

    @Override
    public List<String> cdnMediaPaths() {
        return List.of("/uploads/*");
    }

    @Override
    public List<String> cdnStaticPaths() {
        // cpresources is Craft's compiled asset pipeline output (CSS, JS, icons)
        return List.of("/cpresources/*", "/assets/*", "/dist/*");
    }

    @Override
    public List<String> cdnAdminPaths() {
        // Admin path is configurable but defaults to /admin.
        // Bypass CDN entirely so session cookies are forwarded.
        return List.of("/admin/*", "/index.php/*");
    }

    // ========== CmsSpec — Object Caching ==========

    @Override
    public boolean supportsObjectCache() { return true; }

    @Override
    public String preferredCacheBackend() { return "redis"; }

    @Override
    public String objectCachePlugin() {
        // Craft 4+ has native Redis support via yii2-redis — no separate plugin needed
        return "yii2-redis (native)";
    }

    // ========== CmsSpec — Scheduled Tasks ==========

    @Override
    public boolean hasScheduledTasks() { return true; }

    @Override
    public boolean useSystemCron() { return true; }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        // Craft's queue runner processes async jobs (image transforms, email sends, etc.)
        // Run every minute so jobs are processed promptly without hammering the server.
        return Map.of(
            "* * * * *", "php /var/www/html/craft queue/run --verbose=0 2>&1"
        );
    }

    // ========== CmsSpec — Misc ==========

    @Override
    public boolean supportsMultisite() { return false; }

    @Override
    public String cmsCategory() { return "cms"; }

    @Override
    public String preferredWebServer() { return "nginx"; }

    @Override
    public String cliTool() { return "php craft"; }

    @Override
    public List<String> cliToolInstallCommands() { return List.of(); } // bundled in craftcms/nginx image

    // ========== DatabaseSpec ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("craft");
    }

    @Override
    public int backupRetentionDays() { return 7; }

    // ========== Environment Variables ==========

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();

        env.put("CRAFT_ENVIRONMENT", sslEnabled ? "production" : "staging");
        env.put("CRAFT_WEB_ROOT", DOCUMENT_ROOT);

        if (fqdn != null && !fqdn.isBlank()) {
            String scheme = sslEnabled ? "https" : "http";
            env.put("PRIMARY_SITE_URL", scheme + "://" + fqdn);
            env.put("CRAFT_DEV_MODE", "false");
        }

        // Security key must be set at deploy time via Secrets Manager — placeholder here
        // so Craft can start without crashing; real value injected by the secrets factory.
        env.put("CRAFT_SECURITY_KEY", "set-via-secrets-manager");

        return env;
    }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>(CmsSpec.super.databaseEnvVars(host, port, name, user));
        // Craft-native variable names (read directly from .env / environment)
        env.put("CRAFT_DB_DRIVER", "mysql");
        env.put("CRAFT_DB_SERVER", host);
        env.put("CRAFT_DB_PORT", String.valueOf(port));
        env.put("CRAFT_DB_DATABASE", name);
        env.put("CRAFT_DB_USER", user);
        env.put("CRAFT_DB_TABLE_PREFIX", "");
        return env;
    }

    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>(CmsSpec.super.redisEnvVars(host, port));
        // Craft reads these via yii2-redis configuration in config/app.php
        env.put("CRAFT_REDIS_HOSTNAME", host);
        env.put("CRAFT_REDIS_PORT", String.valueOf(port));
        env.put("CRAFT_REDIS_DATABASE", "0");
        return env;
    }

    // ========== EC2 Configuration ==========

    @Override
    public String ebsDeviceName() { return "/dev/xvdh"; }

    @Override
    public String ec2DataPath() { return "/var/www/html"; }

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/nginx/access.log",
            "/var/log/nginx/error.log",
            "/var/log/php-fpm/error.log",
            "/var/www/html/storage/logs/web.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        PhpRuntimeConfig phpConfig = PhpRuntimeConfig.defaults()
                .withVersion(PHP_VERSION)
                .withExtensions(PHP_EXTENSIONS);
        PhpUserDataBuilder.installPhp(builder, phpConfig);
        PhpUserDataBuilder.installNginx(builder);

        // Install Composer (required for Craft)
        builder.addCommands(
            "# Install Composer",
            "curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer",
            "echo 'Composer installed' >> /var/log/userdata.log"
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

        // Create Craft project via Composer if not already present
        builder.addCommands(
            "# Create Craft project if not present",
            "if [ ! -f " + ec2DataPath() + "/craft ]; then",
            "    cd /var/www",
            "    composer create-project craftcms/craft html --no-interaction",
            "    echo 'Craft CMS project created' >> /var/log/userdata.log",
            "fi",
            "",
            "# Set permissions",
            "chown -R nginx:nginx " + ec2DataPath(),
            "chmod -R 755 " + ec2DataPath(),
            "chmod -R 775 " + ec2DataPath() + "/storage",
            "chmod -R 775 " + ec2DataPath() + "/web/cpresources",
            "",
            "# Configure nginx document root to web/ subdirectory",
            "sed -i 's|root /var/www/html;|root /var/www/html/web;|g' /etc/nginx/conf.d/default.conf",
            "",
            "# Start services",
            "systemctl restart nginx php-fpm",
            "echo 'Craft CMS installation complete' >> /var/log/userdata.log"
        );

        // Install CloudWatch agent
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }

    // ========== OIDC ==========

    @Override
    public boolean supportsOidcIntegration() { return true; }

    @Override
    public OidcIntegration getOidcIntegration() {
        // OIDC via verbb/auth plugin — no built-in OidcIntegration class yet.
        // Returning null here means the framework falls back to ALB-level OIDC (Cognito),
        // which works for Craft because the admin panel is behind /admin/*.
        return null;
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    // ========== Path-Based Authentication ==========

    @Override
    public List<String> protectedPaths() {
        // Only the admin panel needs protection at the ALB level.
        // Craft's public front-end is unauthenticated by design.
        return List.of("/admin/*");
    }

    @Override
    public List<String> publicPaths() {
        return List.of(
            "/actions/app/health-check",  // health check must not require auth
            "/index.php/*"                // Craft's front controller for public pages
        );
    }

    @Override
    public String toString() {
        return "CraftCmsApplicationSpec{id='craft-cms', image='" + DEFAULT_IMAGE + "', port=" + APPLICATION_PORT + "}";
    }
}
