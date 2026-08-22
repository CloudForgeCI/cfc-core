package com.cloudforge.core.interfaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PHP runtime configuration for CMS deployments.
 *
 * <p>This record encapsulates all PHP-related configuration including:</p>
 * <ul>
 *   <li>PHP version and extensions</li>
 *   <li>PHP-FPM pool settings</li>
 *   <li>OPcache configuration</li>
 *   <li>Memory and execution limits</li>
 *   <li>Upload size limits</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * PhpRuntimeConfig config = PhpRuntimeConfig.defaults()
 *     .withVersion("8.2")
 *     .withMemoryLimit(512)
 *     .withExtensions(List.of("mysqli", "gd", "redis"));
 * }</pre>
 *
 * @param version PHP version (e.g., "8.2", "8.1")
 * @param extensions List of required PHP extensions
 * @param phpIni Custom php.ini settings
 * @param fpmConfig PHP-FPM pool configuration
 * @param opcacheConfig OPcache settings
 * @param memoryLimit PHP memory_limit in MB
 * @param maxExecutionTime max_execution_time in seconds
 * @param uploadMaxFilesize upload_max_filesize in MB
 * @param postMaxSize post_max_size in MB
 *
 * @since 3.1.0
 */
public record PhpRuntimeConfig(
    String version,
    List<String> extensions,
    Map<String, String> phpIni,
    Map<String, String> fpmConfig,
    Map<String, String> opcacheConfig,
    int memoryLimit,
    int maxExecutionTime,
    int uploadMaxFilesize,
    int postMaxSize
) {

    /**
     * Common PHP extensions required by most CMS platforms.
     */
    public static final List<String> COMMON_EXTENSIONS = List.of(
        "mysqli",
        "pdo_mysql",
        "gd",
        "curl",
        "mbstring",
        "xml",
        "dom",
        "zip",
        "intl",
        "soap",
        "bcmath",
        "opcache",
        "json",
        "fileinfo",
        "exif"
    );

    /**
     * Extensions for image processing (beyond basic GD).
     */
    public static final List<String> IMAGE_EXTENSIONS = List.of(
        "gd",
        "imagick"
    );

    /**
     * Extensions for caching backends.
     */
    public static final List<String> CACHE_EXTENSIONS = List.of(
        "redis",
        "memcached",
        "apcu"
    );

    /**
     * Extensions for e-commerce platforms (encryption, math).
     */
    public static final List<String> ECOMMERCE_EXTENSIONS = List.of(
        "gmp",
        "sodium",
        "openssl"
    );

    /**
     * Creates a default PHP runtime configuration suitable for most CMS platforms.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>PHP 8.2</li>
     *   <li>Common extensions + redis</li>
     *   <li>256MB memory limit</li>
     *   <li>300 second max execution time</li>
     *   <li>64MB upload limit</li>
     * </ul>
     *
     * @return default PhpRuntimeConfig
     */
    public static PhpRuntimeConfig defaults() {
        List<String> extensions = new ArrayList<>(COMMON_EXTENSIONS);
        extensions.add("redis");

        return new PhpRuntimeConfig(
            "8.2",
            extensions,
            Map.of(),
            Map.of(
                "pm", "dynamic",
                "pm.max_children", "50",
                "pm.start_servers", "5",
                "pm.min_spare_servers", "5",
                "pm.max_spare_servers", "35",
                "pm.max_requests", "500"
            ),
            Map.of(
                "opcache.enable", "1",
                "opcache.memory_consumption", "128",
                "opcache.interned_strings_buffer", "16",
                "opcache.max_accelerated_files", "10000",
                "opcache.revalidate_freq", "60",
                "opcache.fast_shutdown", "1",
                "opcache.enable_cli", "0"
            ),
            256,  // memory_limit MB
            300,  // max_execution_time seconds
            64,   // upload_max_filesize MB
            64    // post_max_size MB
        );
    }

    /**
     * Creates a configuration optimized for WordPress.
     *
     * @return WordPress-optimized PhpRuntimeConfig
     */
    public static PhpRuntimeConfig forWordPress() {
        List<String> extensions = new ArrayList<>(COMMON_EXTENSIONS);
        extensions.addAll(List.of("redis", "imagick"));

        return defaults()
            .withExtensions(extensions)
            .withMemoryLimit(256)
            .withUploadMaxFilesize(64)
            .withPostMaxSize(64);
    }

    /**
     * Creates a configuration optimized for WooCommerce/e-commerce.
     *
     * @return E-commerce optimized PhpRuntimeConfig
     */
    public static PhpRuntimeConfig forEcommerce() {
        List<String> extensions = new ArrayList<>(COMMON_EXTENSIONS);
        extensions.addAll(CACHE_EXTENSIONS);
        extensions.addAll(ECOMMERCE_EXTENSIONS);
        extensions.addAll(IMAGE_EXTENSIONS);

        Map<String, String> fpm = new HashMap<>();
        fpm.put("pm", "dynamic");
        fpm.put("pm.max_children", "100");
        fpm.put("pm.start_servers", "10");
        fpm.put("pm.min_spare_servers", "10");
        fpm.put("pm.max_spare_servers", "50");
        fpm.put("pm.max_requests", "500");

        return new PhpRuntimeConfig(
            "8.2",
            extensions,
            Map.of(),
            fpm,
            defaults().opcacheConfig(),
            512,  // memory_limit MB (higher for e-commerce)
            600,  // max_execution_time seconds (longer for checkouts)
            128,  // upload_max_filesize MB (product images)
            128   // post_max_size MB
        );
    }

    /**
     * Creates a configuration optimized for Magento 2.
     *
     * @return Magento-optimized PhpRuntimeConfig
     */
    public static PhpRuntimeConfig forMagento() {
        List<String> extensions = new ArrayList<>(COMMON_EXTENSIONS);
        extensions.addAll(List.of(
            "redis", "imagick", "gmp", "sodium", "sockets",
            "xsl", "pdo_mysql", "openswoole"
        ));

        Map<String, String> fpm = new HashMap<>();
        fpm.put("pm", "dynamic");
        fpm.put("pm.max_children", "150");
        fpm.put("pm.start_servers", "20");
        fpm.put("pm.min_spare_servers", "10");
        fpm.put("pm.max_spare_servers", "75");
        fpm.put("pm.max_requests", "1000");

        Map<String, String> opcache = new HashMap<>();
        opcache.put("opcache.enable", "1");
        opcache.put("opcache.memory_consumption", "512");
        opcache.put("opcache.interned_strings_buffer", "32");
        opcache.put("opcache.max_accelerated_files", "60000");
        opcache.put("opcache.revalidate_freq", "0");  // Don't check for file changes
        opcache.put("opcache.validate_timestamps", "0");
        opcache.put("opcache.save_comments", "1");
        opcache.put("opcache.fast_shutdown", "1");

        return new PhpRuntimeConfig(
            "8.2",
            extensions,
            Map.of(
                "realpath_cache_size", "10M",
                "realpath_cache_ttl", "7200"
            ),
            fpm,
            opcache,
            2048,  // memory_limit MB (Magento is memory-hungry)
            900,   // max_execution_time seconds
            256,   // upload_max_filesize MB
            256    // post_max_size MB
        );
    }

    /**
     * Creates a configuration for Drupal.
     *
     * @return Drupal-optimized PhpRuntimeConfig
     */
    public static PhpRuntimeConfig forDrupal() {
        List<String> extensions = new ArrayList<>(COMMON_EXTENSIONS);
        extensions.addAll(List.of("redis", "imagick", "apcu", "pdo_pgsql"));

        return defaults()
            .withExtensions(extensions)
            .withMemoryLimit(256)
            .withMaxExecutionTime(300);
    }

    /**
     * Creates a configuration for social networking platforms (Dolphin/UNA, HumHub).
     *
     * @return Social platform optimized PhpRuntimeConfig
     */
    public static PhpRuntimeConfig forSocial() {
        List<String> extensions = new ArrayList<>(COMMON_EXTENSIONS);
        extensions.addAll(List.of(
            "redis", "imagick", "gd", "ffmpeg", "apcu"
        ));

        return defaults()
            .withExtensions(extensions)
            .withMemoryLimit(384)
            .withMaxExecutionTime(600)  // Video processing
            .withUploadMaxFilesize(256)  // Media uploads
            .withPostMaxSize(256);
    }

    // ========== Builder Methods ==========

    /**
     * Returns a new config with the specified PHP version.
     *
     * @param version PHP version (e.g., "8.2", "8.1")
     * @return new PhpRuntimeConfig with updated version
     */
    public PhpRuntimeConfig withVersion(String version) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified extensions.
     *
     * @param extensions list of PHP extension names
     * @return new PhpRuntimeConfig with updated extensions
     */
    public PhpRuntimeConfig withExtensions(List<String> extensions) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with additional extensions added.
     *
     * @param additionalExtensions extensions to add
     * @return new PhpRuntimeConfig with extensions added
     */
    public PhpRuntimeConfig withAdditionalExtensions(List<String> additionalExtensions) {
        List<String> combined = new ArrayList<>(extensions);
        for (String ext : additionalExtensions) {
            if (!combined.contains(ext)) {
                combined.add(ext);
            }
        }
        return withExtensions(combined);
    }

    /**
     * Returns a new config with the specified php.ini settings.
     *
     * @param phpIni map of php.ini key-value pairs
     * @return new PhpRuntimeConfig with updated php.ini
     */
    public PhpRuntimeConfig withPhpIni(Map<String, String> phpIni) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified PHP-FPM settings.
     *
     * @param fpmConfig map of PHP-FPM configuration
     * @return new PhpRuntimeConfig with updated FPM config
     */
    public PhpRuntimeConfig withFpmConfig(Map<String, String> fpmConfig) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified OPcache settings.
     *
     * @param opcacheConfig map of OPcache configuration
     * @return new PhpRuntimeConfig with updated OPcache config
     */
    public PhpRuntimeConfig withOpcacheConfig(Map<String, String> opcacheConfig) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified memory limit.
     *
     * @param memoryLimit memory_limit in MB
     * @return new PhpRuntimeConfig with updated memory limit
     */
    public PhpRuntimeConfig withMemoryLimit(int memoryLimit) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified max execution time.
     *
     * @param maxExecutionTime max_execution_time in seconds
     * @return new PhpRuntimeConfig with updated execution time
     */
    public PhpRuntimeConfig withMaxExecutionTime(int maxExecutionTime) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified upload max filesize.
     *
     * @param uploadMaxFilesize upload_max_filesize in MB
     * @return new PhpRuntimeConfig with updated upload size
     */
    public PhpRuntimeConfig withUploadMaxFilesize(int uploadMaxFilesize) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    /**
     * Returns a new config with the specified post max size.
     *
     * @param postMaxSize post_max_size in MB
     * @return new PhpRuntimeConfig with updated post size
     */
    public PhpRuntimeConfig withPostMaxSize(int postMaxSize) {
        return new PhpRuntimeConfig(
            version, extensions, phpIni, fpmConfig, opcacheConfig,
            memoryLimit, maxExecutionTime, uploadMaxFilesize, postMaxSize
        );
    }

    // ========== Utility Methods ==========

    /**
     * Returns the base Docker image for this PHP version.
     *
     * @return PHP-FPM Alpine image tag
     */
    public String getBaseImage() {
        return String.format("php:%s-fpm-alpine", version);
    }

    /**
     * Returns the PHP-FPM pool name.
     *
     * @return pool name (default: "www")
     */
    public String getPoolName() {
        return "www";
    }

    /**
     * Generates php.ini content from configuration.
     *
     * @return php.ini content as string
     */
    public String generatePhpIni() {
        StringBuilder ini = new StringBuilder();
        ini.append("; CloudForge generated php.ini\n\n");

        // Memory and execution
        ini.append(String.format("memory_limit = %dM\n", memoryLimit));
        ini.append(String.format("max_execution_time = %d\n", maxExecutionTime));
        ini.append(String.format("upload_max_filesize = %dM\n", uploadMaxFilesize));
        ini.append(String.format("post_max_size = %dM\n", postMaxSize));
        ini.append("max_input_vars = 10000\n");
        ini.append("max_input_time = 600\n\n");

        // OPcache
        ini.append("; OPcache configuration\n");
        for (Map.Entry<String, String> entry : opcacheConfig.entrySet()) {
            ini.append(String.format("%s = %s\n", entry.getKey(), entry.getValue()));
        }
        ini.append("\n");

        // Custom php.ini settings
        if (!phpIni.isEmpty()) {
            ini.append("; Custom settings\n");
            for (Map.Entry<String, String> entry : phpIni.entrySet()) {
                ini.append(String.format("%s = %s\n", entry.getKey(), entry.getValue()));
            }
        }

        return ini.toString();
    }

    /**
     * Generates PHP-FPM pool configuration content.
     *
     * @return PHP-FPM pool configuration as string
     */
    public String generateFpmPoolConfig() {
        StringBuilder fpm = new StringBuilder();
        fpm.append("; CloudForge generated PHP-FPM pool configuration\n\n");
        fpm.append(String.format("[%s]\n", getPoolName()));
        fpm.append("user = www-data\n");
        fpm.append("group = www-data\n");
        fpm.append("listen = 127.0.0.1:9000\n\n");

        for (Map.Entry<String, String> entry : fpmConfig.entrySet()) {
            fpm.append(String.format("%s = %s\n", entry.getKey(), entry.getValue()));
        }

        return fpm.toString();
    }
}
