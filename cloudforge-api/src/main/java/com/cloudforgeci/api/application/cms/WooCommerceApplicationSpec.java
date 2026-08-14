package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WooCommerce E-commerce ApplicationSpec implementation.
 *
 * <p>WooCommerce is an e-commerce plugin for WordPress. This specification extends
 * the WordPress configuration with e-commerce-specific requirements.</p>
 *
 * <h2>Key Differences from WordPress:</h2>
 * <ul>
 *   <li>Higher CPU/Memory requirements for order processing</li>
 *   <li>Additional PHP extensions (gmp, sodium) for payments</li>
 *   <li>E-commerce specific cron jobs</li>
 *   <li>PCI-DSS compliance considerations</li>
 *   <li>Higher database requirements</li>
 * </ul>
 *
 * <h2>Recommended Plugins:</h2>
 * <ul>
 *   <li>WooCommerce Stripe Gateway</li>
 *   <li>WooCommerce PayPal Payments</li>
 *   <li>WooCommerce Subscriptions</li>
 *   <li>WP Offload Media - S3 media storage</li>
 * </ul>
 *
 * @since 3.1.0
 * @see WordPressApplicationSpec
 */
@CmsPlugin(
    value = "woocommerce",
    category = "ecommerce",
    displayName = "WooCommerce",
    description = "WordPress-based e-commerce platform for online stores",
    phpVersion = "8.2",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsOidc = true,
    oidcMethod = "OpenID Connect Generic Plugin",
    requiresDatabase = true,
    supportedDatabases = {"mysql", "mariadb"},
    supportsS3Media = true,
    supportsObjectCache = true,
    supportsMultisite = false,
    websiteUrl = "https://woocommerce.com",
    defaultImage = "wordpress:php8.2-apache"
)
public class WooCommerceApplicationSpec extends WordPressApplicationSpec {

    // ========== Constants ==========

    private static final String APPLICATION_ID = "woocommerce";

    // Additional PHP extensions for e-commerce
    private static final List<String> ECOMMERCE_PHP_EXTENSIONS = List.of(
        "mysqli", "pdo_mysql", "gd", "curl", "mbstring", "xml", "dom",
        "zip", "intl", "soap", "bcmath", "opcache", "redis", "imagick",
        "exif", "fileinfo", "gmp", "sodium"
    );

    // ========== ApplicationSpec Overrides ==========

    @Override
    public String applicationId() {
        return APPLICATION_ID;
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        return 240; // 4 minutes for WooCommerce startup (heavier than WordPress)
    }

    // ========== CmsSpec Overrides ==========

    @Override
    public List<String> requiredPhpExtensions() {
        return ECOMMERCE_PHP_EXTENSIONS;
    }

    @Override
    public int phpMemoryLimit() {
        return 512; // WooCommerce needs more memory for cart/checkout operations
    }

    @Override
    public Map<String, String> phpFpmConfig() {
        return Map.of(
            "pm", "dynamic",
            "pm.max_children", "75",
            "pm.start_servers", "10",
            "pm.min_spare_servers", "10",
            "pm.max_spare_servers", "50",
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
            "opcache.save_comments", "1"  // Required for WooCommerce REST API
        );
    }

    @Override
    public Map<String, String> cronCommands(String siteUrl) {
        Map<String, String> crons = new HashMap<>();
        // WordPress core cron
        crons.put("*/15 * * * *", String.format("curl -s '%s/wp-cron.php' > /dev/null 2>&1", siteUrl));
        // WooCommerce action scheduler (handles subscriptions, background tasks)
        crons.put("*/5 * * * *", String.format("wp --path=/var/www/html action-scheduler run --allow-root > /dev/null 2>&1"));
        return crons;
    }

    @Override
    public boolean supportsMultisite() {
        return false; // WooCommerce multisite is complex, not recommended for typical deployments
    }

    @Override
    public String cmsCategory() {
        return "ecommerce";
    }

    // ========== DatabaseSpec Overrides ==========

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.small")  // Larger than WordPress
            .withStorage(50)                    // More storage for orders/products
            .withDatabaseName("woocommerce");
    }

    @Override
    public int backupRetentionDays() {
        return 14; // Longer retention for e-commerce data
    }

    // ========== E-commerce Specific ==========

    /**
     * Returns whether this platform requires PCI-DSS compliance.
     *
     * <p>WooCommerce stores handle payment card data and should
     * follow PCI-DSS requirements when processing payments directly.</p>
     *
     * @return true if PCI-DSS compliance is required
     */
    public boolean requiresPciCompliance() {
        return true;
    }

    /**
     * Returns recommended payment gateways that support PCI compliance.
     *
     * <p>These gateways handle card data off-site, reducing PCI scope:</p>
     * <ul>
     *   <li>Stripe - PCI Level 1 Service Provider</li>
     *   <li>PayPal - PCI Level 1 Service Provider</li>
     *   <li>Square - PCI Level 1 Service Provider</li>
     * </ul>
     *
     * @return list of recommended payment gateway plugin slugs
     */
    public List<String> recommendedPaymentGateways() {
        return List.of(
            "woocommerce-gateway-stripe",
            "woocommerce-paypal-payments",
            "woocommerce-square"
        );
    }

    // ========== Container Environment Overrides ==========

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>(super.containerEnvironmentVariables(fqdn, sslEnabled, authMode));

        // WooCommerce specific configuration
        String configExtra = env.getOrDefault("WORDPRESS_CONFIG_EXTRA", "");

        // Force SSL for checkout (PCI requirement)
        if (sslEnabled) {
            configExtra += " define('FORCE_SSL_LOGIN', true);";
            configExtra += " define('FORCE_SSL_ADMIN', true);";
        }

        // WooCommerce Action Scheduler - use system cron
        configExtra += " define('DISABLE_WP_CRON', true);";

        // Increase memory limits for large catalogs
        configExtra += " define('WP_MEMORY_LIMIT', '512M');";
        configExtra += " define('WP_MAX_MEMORY_LIMIT', '512M');";

        // Enable WooCommerce logging for debugging
        configExtra += " define('WC_LOG_HANDLER', 'WC_Log_Handler_File');";

        env.put("WORDPRESS_CONFIG_EXTRA", configExtra);

        return env;
    }

    /**
     * Returns commands to install WooCommerce via WP-CLI.
     *
     * @return list of installation commands
     */
    public List<String> wooCommerceInstallCommands() {
        List<String> commands = new ArrayList<>();
        commands.add("# Install WooCommerce");
        commands.add("wp plugin install woocommerce --activate --allow-root");
        commands.add("echo 'WooCommerce installed' >> /var/log/userdata.log");

        // Install WooCommerce Action Scheduler for background processing
        commands.add("# Configure Action Scheduler");
        commands.add("wp option update action_scheduler_lock_async true --allow-root");

        return commands;
    }

    /**
     * Returns PhpRuntimeConfig optimized for WooCommerce.
     *
     * @return PHP runtime configuration for e-commerce
     */
    public static PhpRuntimeConfig getPhpConfig() {
        return PhpRuntimeConfig.forEcommerce();
    }

    // ========== Path-Based Authentication ==========

    /**
     * Returns default protected paths for WooCommerce when using ALB-level OIDC.
     *
     * <p>WooCommerce inherits WordPress admin paths plus e-commerce specific:</p>
     * <ul>
     *   <li>/wp-admin/* - WordPress admin dashboard (includes WooCommerce admin)</li>
     *   <li>/wp-login.php - Login page</li>
     * </ul>
     *
     * @return list of WooCommerce administrative paths requiring authentication
     */
    @Override
    public List<String> protectedPaths() {
        return List.of(
            "/wp-admin/*",     // Admin dashboard (includes WooCommerce)
            "/wp-login.php"    // Login page
        );
    }
}
