package com.cloudforgeci.api.core;

import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserData builder extension for PHP applications on EC2.
 *
 * <p>Provides helper methods for installing and configuring PHP,
 * NGINX, and CMS platforms on Amazon Linux 2023 EC2 instances.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * public void configureUserData(UserDataBuilder builder, Ec2Context context) {
 *     builder.addSystemUpdate();
 *
 *     PhpRuntimeConfig config = PhpRuntimeConfig.forWordPress();
 *     PhpUserDataBuilder.installPhp(builder, config);
 *     PhpUserDataBuilder.installNginx(builder);
 *     PhpUserDataBuilder.installWordPress(builder, "/var/www/html");
 *
 *     // Mount storage
 *     if (context.hasEfs()) {
 *         builder.mountEfs(...);
 *     }
 * }
 * }</pre>
 *
 * @since 3.1.0
 */
public final class PhpUserDataBuilder {

    private PhpUserDataBuilder() {
        // Utility class
    }

    /**
     * Install PHP and required extensions on Amazon Linux 2023.
     *
     * @param builder the UserDataBuilder
     * @param config the PHP runtime configuration
     */
    public static void installPhp(UserDataBuilder builder, PhpRuntimeConfig config) {
        String phpPkg = "php" + config.version().replace(".", "");

        // Build extension package list
        String extensions = config.extensions().stream()
            .map(ext -> phpPkg + "-" + ext)
            .collect(Collectors.joining(" \\\n    "));

        builder.addCommands(
            "# Install PHP " + config.version(),
            "echo 'Installing PHP " + config.version() + "...' >> /var/log/userdata.log",
            "",
            "# Install PHP packages",
            "dnf install -y " + phpPkg + " \\",
            "    " + phpPkg + "-fpm \\",
            "    " + phpPkg + "-cli \\",
            "    " + extensions,
            "",
            "echo 'PHP installed successfully' >> /var/log/userdata.log"
        );

        // Configure PHP
        configurePhpIni(builder, config);
        configurePhpFpm(builder, config);
    }

    /**
     * Configure PHP memory limits, execution time, and other settings.
     *
     * @param builder the UserDataBuilder
     * @param config the PHP runtime configuration
     */
    public static void configurePhpIni(UserDataBuilder builder, PhpRuntimeConfig config) {
        builder.addCommands(
            "# Configure PHP settings",
            String.format("sed -i 's/memory_limit = .*/memory_limit = %dM/' /etc/php.ini",
                config.memoryLimit()),
            String.format("sed -i 's/max_execution_time = .*/max_execution_time = %d/' /etc/php.ini",
                config.maxExecutionTime()),
            String.format("sed -i 's/upload_max_filesize = .*/upload_max_filesize = %dM/' /etc/php.ini",
                config.uploadMaxFilesize()),
            String.format("sed -i 's/post_max_size = .*/post_max_size = %dM/' /etc/php.ini",
                config.postMaxSize()),
            "sed -i 's/max_input_vars = .*/max_input_vars = 10000/' /etc/php.ini",
            "sed -i 's/max_input_time = .*/max_input_time = 600/' /etc/php.ini",
            ""
        );

        // OPcache configuration
        if (!config.opcacheConfig().isEmpty()) {
            builder.addCommands("# Configure OPcache");
            for (Map.Entry<String, String> entry : config.opcacheConfig().entrySet()) {
                builder.addCommand(String.format(
                    "echo '%s = %s' >> /etc/php.d/10-opcache.ini",
                    entry.getKey(), entry.getValue()
                ));
            }
            builder.addCommand("");
        }

        // Additional php.ini settings
        if (!config.phpIni().isEmpty()) {
            builder.addCommands("# Additional PHP settings");
            for (Map.Entry<String, String> entry : config.phpIni().entrySet()) {
                builder.addCommand(String.format(
                    "echo '%s = %s' >> /etc/php.ini",
                    entry.getKey(), entry.getValue()
                ));
            }
            builder.addCommand("");
        }
    }

    /**
     * Configure PHP-FPM pool settings.
     *
     * @param builder the UserDataBuilder
     * @param config the PHP runtime configuration
     */
    public static void configurePhpFpm(UserDataBuilder builder, PhpRuntimeConfig config) {
        builder.addCommands(
            "# Configure PHP-FPM",
            "mkdir -p /var/log/php-fpm"
        );

        // Apply FPM configuration
        for (Map.Entry<String, String> entry : config.fpmConfig().entrySet()) {
            builder.addCommand(String.format(
                "sed -i 's/%s = .*/%s = %s/' /etc/php-fpm.d/www.conf",
                entry.getKey(), entry.getKey(), entry.getValue()
            ));
        }

        builder.addCommands(
            "",
            "# Enable and start PHP-FPM",
            "systemctl enable php-fpm",
            "systemctl start php-fpm",
            "echo 'PHP-FPM configured and started' >> /var/log/userdata.log"
        );
    }

    /**
     * Install NGINX web server.
     *
     * @param builder the UserDataBuilder
     */
    public static void installNginx(UserDataBuilder builder) {
        builder.addCommands(
            "# Install NGINX",
            "echo 'Installing NGINX...' >> /var/log/userdata.log",
            "dnf install -y nginx",
            "",
            "# Create log directories",
            "mkdir -p /var/log/nginx",
            "",
            "# Enable NGINX",
            "systemctl enable nginx",
            "echo 'NGINX installed' >> /var/log/userdata.log"
        );
    }

    /**
     * Configure NGINX with a provided configuration.
     *
     * @param builder the UserDataBuilder
     * @param nginxConfig the NGINX configuration content
     */
    public static void configureNginx(UserDataBuilder builder, String nginxConfig) {
        builder.addCommands(
            "# Configure NGINX",
            "cat > /etc/nginx/conf.d/cms.conf << 'NGINX_EOF'",
            nginxConfig,
            "NGINX_EOF",
            "",
            "# Test and start NGINX",
            "nginx -t && systemctl restart nginx",
            "echo 'NGINX configured and started' >> /var/log/userdata.log"
        );
    }

    /**
     * Install WP-CLI for WordPress management.
     *
     * @param builder the UserDataBuilder
     */
    public static void installWpCli(UserDataBuilder builder) {
        builder.addCommands(
            "# Install WP-CLI",
            "curl -O https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar",
            "chmod +x wp-cli.phar",
            "mv wp-cli.phar /usr/local/bin/wp",
            "echo 'WP-CLI installed' >> /var/log/userdata.log"
        );
    }

    /**
     * Download and install WordPress.
     *
     * @param builder the UserDataBuilder
     * @param installPath the installation directory
     */
    public static void installWordPress(UserDataBuilder builder, String installPath) {
        builder.addCommands(
            "# Download WordPress",
            "cd " + installPath,
            "wp core download --allow-root",
            "chown -R nginx:nginx " + installPath,
            "chmod -R 755 " + installPath,
            "echo 'WordPress downloaded' >> /var/log/userdata.log"
        );
    }

    /**
     * Install Composer for PHP dependency management.
     *
     * @param builder the UserDataBuilder
     */
    public static void installComposer(UserDataBuilder builder) {
        builder.addCommands(
            "# Install Composer",
            "php -r \"copy('https://getcomposer.org/installer', 'composer-setup.php');\"",
            "php composer-setup.php --install-dir=/usr/local/bin --filename=composer",
            "rm composer-setup.php",
            "echo 'Composer installed' >> /var/log/userdata.log"
        );
    }

    /**
     * Install Drush for Drupal management.
     *
     * @param builder the UserDataBuilder
     */
    public static void installDrush(UserDataBuilder builder) {
        builder.addCommands(
            "# Install Drush",
            "composer global require drush/drush",
            "ln -s ~/.composer/vendor/bin/drush /usr/local/bin/drush",
            "echo 'Drush installed' >> /var/log/userdata.log"
        );
    }

    /**
     * Install Magento CLI dependencies.
     *
     * @param builder the UserDataBuilder
     */
    public static void installMagentoDependencies(UserDataBuilder builder) {
        builder.addCommands(
            "# Install Magento dependencies",
            "dnf install -y elasticsearch opensearch",
            "systemctl enable elasticsearch",
            "systemctl start elasticsearch",
            "",
            "# Install Varnish for full-page caching",
            "dnf install -y varnish",
            "systemctl enable varnish",
            "echo 'Magento dependencies installed' >> /var/log/userdata.log"
        );
    }

    /**
     * Configure system cron for CMS scheduled tasks.
     *
     * @param builder the UserDataBuilder
     * @param spec the CMS specification
     * @param siteUrl the site URL
     */
    public static void configureCron(UserDataBuilder builder, CmsSpec spec, String siteUrl) {
        if (!spec.hasScheduledTasks()) {
            return;
        }

        Map<String, String> cronJobs = spec.cronCommands(siteUrl);
        if (cronJobs.isEmpty()) {
            return;
        }

        builder.addCommands(
            "# Configure CMS cron jobs",
            "crontab -l > /tmp/crontab.txt 2>/dev/null || true"
        );

        for (Map.Entry<String, String> cron : cronJobs.entrySet()) {
            builder.addCommand(String.format(
                "echo '%s %s' >> /tmp/crontab.txt",
                cron.getKey(), cron.getValue()
            ));
        }

        builder.addCommands(
            "crontab /tmp/crontab.txt",
            "rm /tmp/crontab.txt",
            "echo 'Cron jobs configured' >> /var/log/userdata.log"
        );
    }

    /**
     * Install Redis PHP extension and configure for object caching.
     *
     * @param builder the UserDataBuilder
     * @param redisHost Redis server hostname
     * @param redisPort Redis server port
     */
    public static void configureRedisCache(UserDataBuilder builder, String redisHost, int redisPort) {
        builder.addCommands(
            "# Configure Redis object cache",
            "echo 'Configuring Redis cache connection...' >> /var/log/userdata.log",
            "",
            "# Verify Redis connectivity",
            String.format("redis-cli -h %s -p %d ping", redisHost, redisPort),
            "echo 'Redis connection verified' >> /var/log/userdata.log"
        );
    }

    /**
     * Install and configure CloudWatch Agent for PHP logs.
     *
     * @param builder the UserDataBuilder
     * @param logGroupName CloudWatch log group name
     * @param spec the CMS specification
     */
    public static void installCloudWatchAgent(UserDataBuilder builder, String logGroupName, CmsSpec spec) {
        List<String> logPaths = new java.util.ArrayList<>(spec.ec2LogPaths());
        if (!logPaths.contains("/var/log/php-fpm/error.log"))  logPaths.add("/var/log/php-fpm/error.log");
        if (!logPaths.contains("/var/log/nginx/error.log"))    logPaths.add("/var/log/nginx/error.log");
        if (!logPaths.contains("/var/log/nginx/access.log"))   logPaths.add("/var/log/nginx/access.log");

        builder.installCloudWatchAgent(logGroupName, logPaths);
    }

    /**
     * Set up file permissions for CMS directory.
     *
     * @param builder the UserDataBuilder
     * @param documentRoot document root path
     * @param webUser web server user (e.g., "nginx", "www-data")
     * @param webGroup web server group
     */
    public static void setFilePermissions(UserDataBuilder builder, String documentRoot, String webUser, String webGroup) {
        builder.addCommands(
            "# Set file permissions",
            String.format("chown -R %s:%s %s", webUser, webGroup, documentRoot),
            String.format("find %s -type d -exec chmod 755 {} \\;", documentRoot),
            String.format("find %s -type f -exec chmod 644 {} \\;", documentRoot),
            "echo 'File permissions set' >> /var/log/userdata.log"
        );
    }

    /**
     * Configure SELinux for PHP/NGINX (if enabled).
     *
     * @param builder the UserDataBuilder
     * @param documentRoot document root path
     */
    public static void configureSELinux(UserDataBuilder builder, String documentRoot) {
        builder.addCommands(
            "# Configure SELinux (if enabled)",
            "if [ $(getenforce 2>/dev/null) == 'Enforcing' ]; then",
            "    setsebool -P httpd_can_network_connect 1",
            "    setsebool -P httpd_can_network_connect_db 1",
            "    setsebool -P httpd_unified 1",
            String.format("    chcon -R -t httpd_sys_rw_content_t %s", documentRoot),
            "    echo 'SELinux configured' >> /var/log/userdata.log",
            "fi"
        );
    }

    /**
     * Complete CMS installation with all standard components.
     *
     * @param builder the UserDataBuilder
     * @param spec the CMS specification
     * @param config the PHP runtime configuration
     * @param documentRoot document root path
     */
    public static void completeInstallation(
            UserDataBuilder builder,
            CmsSpec spec,
            PhpRuntimeConfig config,
            String documentRoot) {

        // System update
        builder.addSystemUpdate();

        // Install PHP
        installPhp(builder, config);

        // Install NGINX
        installNginx(builder);

        // Install Composer (needed for most modern CMS)
        installComposer(builder);

        // CMS-specific installations
        String cmsId = spec.applicationId();
        switch (cmsId) {
            case "wordpress", "woocommerce" -> installWpCli(builder);
            case "drupal" -> installDrush(builder);
            case "magento" -> installMagentoDependencies(builder);
        }

        // Set permissions
        setFilePermissions(builder, documentRoot, "nginx", "nginx");

        // Configure SELinux
        configureSELinux(builder, documentRoot);

        builder.addCommands(
            "",
            "echo 'CMS installation complete' >> /var/log/userdata.log",
            "echo 'Ready for configuration at: " + documentRoot + "' >> /var/log/userdata.log"
        );
    }
}
