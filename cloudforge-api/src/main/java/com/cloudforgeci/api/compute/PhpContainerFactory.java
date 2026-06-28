package com.cloudforgeci.api.compute;

import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating PHP container configurations for CMS platforms.
 *
 * <p>Generates container environment variables, PHP-FPM configuration,
 * and NGINX configuration for PHP-based CMS deployments on Fargate.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * CmsSpec wordpress = new WordPressApplicationSpec();
 * PhpRuntimeConfig config = PhpRuntimeConfig.forWordPress();
 *
 * Map<String, String> env = PhpContainerFactory.createEnvironment(wordpress, config);
 * String nginxConf = PhpContainerFactory.generateNginxConfig(wordpress);
 * }</pre>
 *
 * @since 3.1.0
 */
public final class PhpContainerFactory {

    private PhpContainerFactory() {
        // Utility class
    }

    /**
     * Generate PHP container environment variables.
     *
     * <p>Creates environment variables for:</p>
     * <ul>
     *   <li>PHP memory and execution limits</li>
     *   <li>OPcache settings</li>
     *   <li>Upload limits</li>
     *   <li>Timezone</li>
     * </ul>
     *
     * @param spec the CMS specification
     * @param config the PHP runtime configuration
     * @return map of environment variables
     */
    public static Map<String, String> createEnvironment(CmsSpec spec, PhpRuntimeConfig config) {
        Map<String, String> env = new HashMap<>();

        // PHP settings
        env.put("PHP_MEMORY_LIMIT", config.memoryLimit() + "M");
        env.put("PHP_MAX_EXECUTION_TIME", String.valueOf(config.maxExecutionTime()));
        env.put("PHP_UPLOAD_MAX_FILESIZE", config.uploadMaxFilesize() + "M");
        env.put("PHP_POST_MAX_SIZE", config.postMaxSize() + "M");
        env.put("PHP_MAX_INPUT_VARS", "10000");
        env.put("PHP_MAX_INPUT_TIME", "600");

        // OPcache settings as environment variables
        for (Map.Entry<String, String> entry : config.opcacheConfig().entrySet()) {
            String key = entry.getKey().toUpperCase().replace(".", "_");
            env.put("PHP_" + key, entry.getValue());
        }

        // PHP-FPM settings
        for (Map.Entry<String, String> entry : config.fpmConfig().entrySet()) {
            String key = entry.getKey().toUpperCase().replace(".", "_");
            env.put("PHP_FPM_" + key, entry.getValue());
        }

        // Timezone
        env.put("TZ", "UTC");

        return env;
    }

    /**
     * Generate PHP-FPM configuration file content.
     *
     * @param spec the CMS specification
     * @param config the PHP runtime configuration
     * @return PHP-FPM pool configuration content
     */
    public static String generateFpmConfig(CmsSpec spec, PhpRuntimeConfig config) {
        StringBuilder fpm = new StringBuilder();
        fpm.append("; CloudForge generated PHP-FPM configuration for ").append(spec.displayName()).append("\n\n");
        fpm.append("[www]\n");
        fpm.append("user = www-data\n");
        fpm.append("group = www-data\n");
        fpm.append("listen = 127.0.0.1:9000\n");
        fpm.append("listen.owner = www-data\n");
        fpm.append("listen.group = www-data\n\n");

        for (Map.Entry<String, String> entry : config.fpmConfig().entrySet()) {
            fpm.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
        }

        fpm.append("\n; Slow log for debugging\n");
        fpm.append("slowlog = /var/log/php-fpm/slow.log\n");
        fpm.append("request_slowlog_timeout = 10s\n");

        fpm.append("\n; Status page for monitoring\n");
        fpm.append("pm.status_path = /fpm-status\n");
        fpm.append("ping.path = /fpm-ping\n");

        return fpm.toString();
    }

    /**
     * Get the base container image for the specified PHP version.
     *
     * @param phpVersion PHP version (e.g., "8.2", "8.1")
     * @return Docker image tag
     */
    public static String getBaseImage(String phpVersion) {
        return String.format("php:%s-fpm-alpine", phpVersion);
    }

    /**
     * Get the official CMS container image if available.
     *
     * @param spec the CMS specification
     * @return container image reference
     */
    public static String getCmsImage(CmsSpec spec) {
        // Check annotation for default image
        var annotation = spec.getClass().getAnnotation(
            com.cloudforge.core.annotation.CmsPlugin.class);
        if (annotation != null && !annotation.defaultImage().isEmpty()) {
            return annotation.defaultImage();
        }

        // Fall back to default container image from spec
        return spec.defaultContainerImage();
    }

    /**
     * Generate NGINX configuration for PHP-FPM reverse proxy.
     *
     * @param spec the CMS specification
     * @return NGINX configuration content
     */
    public static String generateNginxConfig(CmsSpec spec) {
        String documentRoot = spec.documentRoot();
        int port = spec.applicationPort();

        StringBuilder nginx = new StringBuilder();
        nginx.append("# CloudForge generated NGINX configuration for ").append(spec.displayName()).append("\n\n");

        nginx.append("server {\n");
        nginx.append("    listen ").append(port).append(";\n");
        nginx.append("    listen [::]:").append(port).append(";\n");
        nginx.append("    server_name _;\n\n");

        nginx.append("    root ").append(documentRoot).append(";\n");
        nginx.append("    index index.php index.html index.htm;\n\n");

        // Client body size for uploads
        nginx.append("    client_max_body_size 128M;\n\n");

        // Logging
        nginx.append("    access_log /var/log/nginx/access.log;\n");
        nginx.append("    error_log /var/log/nginx/error.log;\n\n");

        // Security headers
        nginx.append("    # Security headers\n");
        nginx.append("    add_header X-Frame-Options \"SAMEORIGIN\" always;\n");
        nginx.append("    add_header X-Content-Type-Options \"nosniff\" always;\n");
        nginx.append("    add_header X-XSS-Protection \"1; mode=block\" always;\n\n");

        // Main location block
        nginx.append("    location / {\n");
        nginx.append("        try_files $uri $uri/ /index.php?$args;\n");
        nginx.append("    }\n\n");

        // PHP processing
        nginx.append("    location ~ \\.php$ {\n");
        nginx.append("        try_files $uri =404;\n");
        nginx.append("        fastcgi_split_path_info ^(.+\\.php)(/.+)$;\n");
        nginx.append("        fastcgi_pass 127.0.0.1:9000;\n");
        nginx.append("        fastcgi_index index.php;\n");
        nginx.append("        include fastcgi_params;\n");
        nginx.append("        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;\n");
        nginx.append("        fastcgi_param PATH_INFO $fastcgi_path_info;\n");
        nginx.append("        fastcgi_read_timeout 300;\n");
        nginx.append("        fastcgi_buffer_size 128k;\n");
        nginx.append("        fastcgi_buffers 4 256k;\n");
        nginx.append("        fastcgi_busy_buffers_size 256k;\n");
        nginx.append("    }\n\n");

        // Static file caching
        nginx.append("    # Static file caching\n");
        nginx.append("    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {\n");
        nginx.append("        expires 30d;\n");
        nginx.append("        add_header Cache-Control \"public, immutable\";\n");
        nginx.append("        access_log off;\n");
        nginx.append("    }\n\n");

        // Deny access to sensitive files
        nginx.append("    # Deny access to sensitive files\n");
        nginx.append("    location ~ /\\. {\n");
        nginx.append("        deny all;\n");
        nginx.append("    }\n\n");

        nginx.append("    location ~ \\.(sql|log|htaccess)$ {\n");
        nginx.append("        deny all;\n");
        nginx.append("    }\n\n");

        // Health check endpoint
        nginx.append("    # Health check for ALB\n");
        nginx.append("    location = /health {\n");
        nginx.append("        access_log off;\n");
        nginx.append("        return 200 'OK';\n");
        nginx.append("        add_header Content-Type text/plain;\n");
        nginx.append("    }\n\n");

        // PHP-FPM status (internal only)
        nginx.append("    # PHP-FPM status (internal)\n");
        nginx.append("    location = /fpm-status {\n");
        nginx.append("        access_log off;\n");
        nginx.append("        allow 127.0.0.1;\n");
        nginx.append("        deny all;\n");
        nginx.append("        include fastcgi_params;\n");
        nginx.append("        fastcgi_pass 127.0.0.1:9000;\n");
        nginx.append("    }\n\n");

        nginx.append("    location = /fpm-ping {\n");
        nginx.append("        access_log off;\n");
        nginx.append("        allow 127.0.0.1;\n");
        nginx.append("        deny all;\n");
        nginx.append("        include fastcgi_params;\n");
        nginx.append("        fastcgi_pass 127.0.0.1:9000;\n");
        nginx.append("    }\n");

        nginx.append("}\n");

        return nginx.toString();
    }

    /**
     * Generate WordPress-specific NGINX configuration.
     *
     * @param documentRoot document root path
     * @param port listen port
     * @param isMultisite whether multisite is enabled
     * @return WordPress NGINX configuration
     */
    public static String generateWordPressNginxConfig(String documentRoot, int port, boolean isMultisite) {
        StringBuilder nginx = new StringBuilder();
        nginx.append("# CloudForge generated NGINX configuration for WordPress\n\n");

        nginx.append("server {\n");
        nginx.append("    listen ").append(port).append(";\n");
        nginx.append("    listen [::]:").append(port).append(";\n");
        nginx.append("    server_name _;\n\n");

        nginx.append("    root ").append(documentRoot).append(";\n");
        nginx.append("    index index.php index.html;\n\n");

        nginx.append("    client_max_body_size 128M;\n\n");

        // WordPress-specific security
        nginx.append("    # WordPress security\n");
        nginx.append("    location = /xmlrpc.php {\n");
        nginx.append("        deny all;\n");
        nginx.append("    }\n\n");

        nginx.append("    location ~* /wp-includes/.*\\.php$ {\n");
        nginx.append("        deny all;\n");
        nginx.append("    }\n\n");

        nginx.append("    location ~* /wp-content/uploads/.*\\.php$ {\n");
        nginx.append("        deny all;\n");
        nginx.append("    }\n\n");

        // WordPress permalinks
        nginx.append("    location / {\n");
        nginx.append("        try_files $uri $uri/ /index.php?$args;\n");
        nginx.append("    }\n\n");

        // Multisite rules
        if (isMultisite) {
            nginx.append("    # WordPress Multisite\n");
            nginx.append("    if (!-e $request_filename) {\n");
            nginx.append("        rewrite /wp-admin$ $scheme://$host$uri/ permanent;\n");
            nginx.append("        rewrite ^(/[^/]+)?(/wp-.*) $2 last;\n");
            nginx.append("        rewrite ^(/[^/]+)?(/.*\\.php) $2 last;\n");
            nginx.append("    }\n\n");
        }

        // PHP processing
        nginx.append("    location ~ \\.php$ {\n");
        nginx.append("        try_files $uri =404;\n");
        nginx.append("        fastcgi_split_path_info ^(.+\\.php)(/.+)$;\n");
        nginx.append("        fastcgi_pass 127.0.0.1:9000;\n");
        nginx.append("        fastcgi_index index.php;\n");
        nginx.append("        include fastcgi_params;\n");
        nginx.append("        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;\n");
        nginx.append("        fastcgi_read_timeout 300;\n");
        nginx.append("    }\n\n");

        // Static file caching
        nginx.append("    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {\n");
        nginx.append("        expires 30d;\n");
        nginx.append("        add_header Cache-Control \"public, immutable\";\n");
        nginx.append("    }\n\n");

        // wp-content uploads
        nginx.append("    location /wp-content/uploads/ {\n");
        nginx.append("        location ~ \\.php$ { deny all; }\n");
        nginx.append("    }\n\n");

        // Health check
        nginx.append("    location = /health {\n");
        nginx.append("        access_log off;\n");
        nginx.append("        return 200 'OK';\n");
        nginx.append("    }\n");

        nginx.append("}\n");

        return nginx.toString();
    }

    /**
     * Generate Magento-specific NGINX configuration.
     *
     * @param documentRoot document root path (typically /var/www/html)
     * @param port listen port
     * @return Magento NGINX configuration
     */
    public static String generateMagentoNginxConfig(String documentRoot, int port) {
        StringBuilder nginx = new StringBuilder();
        nginx.append("# CloudForge generated NGINX configuration for Magento 2\n\n");

        nginx.append("upstream fastcgi_backend {\n");
        nginx.append("    server 127.0.0.1:9000;\n");
        nginx.append("}\n\n");

        nginx.append("server {\n");
        nginx.append("    listen ").append(port).append(";\n");
        nginx.append("    server_name _;\n\n");

        nginx.append("    set $MAGE_ROOT ").append(documentRoot).append(";\n");
        nginx.append("    set $MAGE_MODE production;\n\n");

        nginx.append("    root $MAGE_ROOT/pub;\n");
        nginx.append("    index index.php;\n\n");

        nginx.append("    client_max_body_size 256M;\n\n");

        // Include Magento nginx config
        nginx.append("    include ").append(documentRoot).append("/nginx.conf.sample;\n\n");

        // Health check
        nginx.append("    location = /health {\n");
        nginx.append("        access_log off;\n");
        nginx.append("        return 200 'OK';\n");
        nginx.append("    }\n");

        nginx.append("}\n");

        return nginx.toString();
    }

    /**
     * Generate environment variables for database connection.
     *
     * @param spec the CMS specification
     * @param dbHost database hostname
     * @param dbPort database port
     * @param dbName database name
     * @param dbUser database username
     * @param dbPasswordSecretArn Secrets Manager ARN for password
     * @return map of database environment variables
     */
    public static Map<String, String> createDatabaseEnvironment(
            CmsSpec spec,
            String dbHost,
            int dbPort,
            String dbName,
            String dbUser,
            String dbPasswordSecretArn) {
        // Delegate to the spec — no hardcoded CMS IDs needed here.
        // Each CmsSpec implementation declares its own DB env var names via databaseEnvVars().
        return spec.databaseEnvVars(dbHost, dbPort, dbName, dbUser);
    }

    /**
     * Generate environment variables for Redis object cache.
     *
     * @param spec the CMS specification
     * @param redisHost Redis hostname
     * @param redisPort Redis port
     * @return map of Redis environment variables
     */
    public static Map<String, String> createRedisEnvironment(
            CmsSpec spec,
            String redisHost,
            int redisPort) {
        // Delegate to the spec — no hardcoded CMS IDs needed here.
        return spec.redisEnvVars(redisHost, redisPort);
    }
}
