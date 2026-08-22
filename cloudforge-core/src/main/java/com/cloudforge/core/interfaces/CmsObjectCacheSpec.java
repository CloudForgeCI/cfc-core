package com.cloudforge.core.interfaces;

import java.util.Map;

/**
 * Object cache configuration for CMS platforms.
 *
 * <p>Defines Redis/Memcached object caching capabilities for CMS deployments.
 * Object caching significantly improves performance by storing:</p>
 * <ul>
 *   <li>Database query results</li>
 *   <li>Computed page fragments</li>
 *   <li>Session data</li>
 *   <li>Transient/temporary data</li>
 * </ul>
 *
 * <h2>Supported Backends:</h2>
 * <ul>
 *   <li><strong>Redis</strong> - Recommended for most CMS platforms</li>
 *   <li><strong>Memcached</strong> - Alternative for simple key-value caching</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * if (spec instanceof CmsObjectCacheSpec cacheSpec) {
 *     if (cacheSpec.isObjectCacheEnabled()) {
 *         String backend = cacheSpec.getCacheBackend();
 *         Map<String, String> env = cacheSpec.getCachePluginEnvironment();
 *     }
 * }
 * }</pre>
 *
 * <h2>4.0 migration intent</h2>
 * <p>This is a CMS compatibility/binding contract, not a CMS-only cache provisioning model.
 * During the 4.0 migration, object, session, and page-cache needs are adapted to named typed
 * requirements such as {@code cache.object}, {@code cache.sessions}, and {@code cache.pages}.
 * The platform resolves the allowed provider/profile, endpoint binding, network placement,
 * encryption, access policy, target capability, entitlement restriction, and applicable
 * compliance controls.</p>
 *
 * <p>CMS-specific environment/configuration mapping remains application-owned. New application
 * plugins should request reusable cache capabilities directly; existing implementations remain
 * supported through the 4.0 compatibility adapter until synthesis, integration, parameterized,
 * and compliance regressions prove equivalent behavior.</p>
 *
 * @since 3.1.0
 * @see CmsSpec
 */
public interface CmsObjectCacheSpec {

    /**
     * Returns the cache backend type.
     *
     * <p>Supported backends:</p>
     * <ul>
     *   <li>"redis" - Redis 7.x (recommended)</li>
     *   <li>"memcached" - Memcached</li>
     *   <li>"none" - No object caching</li>
     * </ul>
     *
     * @return cache backend type
     */
    String getCacheBackend();

    /**
     * Returns whether object caching is enabled.
     *
     * <p>{@code null}/blank {@link #getCacheBackend()} counts as disabled, not enabled — a
     * {@code CmsSpec} implementation that hasn't configured a backend yet (including third-party
     * plugins, which won't have been through the same internal review as the built-in specs)
     * should fail safe to "no cache," not silently report caching as active with a meaningless
     * endpoint/port downstream.</p>
     *
     * @return true if object caching is enabled
     */
    default boolean isObjectCacheEnabled() {
        String backend = getCacheBackend();
        return backend != null && !backend.isBlank() && !"none".equals(backend);
    }

    /**
     * Returns the Redis/Memcached endpoint.
     *
     * <p>For ElastiCache, this is the primary endpoint or
     * configuration endpoint for cluster mode.</p>
     *
     * @return cache server hostname
     */
    String getCacheEndpoint();

    /**
     * Returns the cache port.
     *
     * <p>Default ports:</p>
     * <ul>
     *   <li>Redis: 6379</li>
     *   <li>Memcached: 11211</li>
     * </ul>
     *
     * @return cache port number
     */
    int getCachePort();

    /**
     * Returns the Redis database index.
     *
     * <p>Redis supports multiple databases (0-15). Different CMS components
     * can use different databases for isolation:</p>
     * <ul>
     *   <li>0 - Object cache</li>
     *   <li>1 - Session storage</li>
     *   <li>2 - Page cache</li>
     * </ul>
     *
     * <p>Not applicable for Memcached.</p>
     *
     * @return Redis database index (default: 0)
     */
    default int getCacheDatabase() {
        return 0;
    }

    /**
     * Returns the Redis database for session storage.
     *
     * @return Redis database index for sessions (default: 1)
     */
    default int getSessionDatabase() {
        return 1;
    }

    /**
     * Returns the Redis database for page cache.
     *
     * @return Redis database index for page cache (default: 2)
     */
    default int getPageCacheDatabase() {
        return 2;
    }

    /**
     * Returns environment variables for cache plugin configuration.
     *
     * <p>CMS-specific environment variables:</p>
     *
     * <p><b>WordPress (Redis Object Cache):</b></p>
     * <ul>
     *   <li>WP_REDIS_HOST - Redis hostname</li>
     *   <li>WP_REDIS_PORT - Redis port</li>
     *   <li>WP_REDIS_DATABASE - Database index</li>
     *   <li>WP_REDIS_PASSWORD - Auth password (if required)</li>
     * </ul>
     *
     * <p><b>Magento:</b></p>
     * <ul>
     *   <li>MAGENTO_CACHE_BACKEND_REDIS_SERVER - Redis hostname</li>
     *   <li>MAGENTO_CACHE_BACKEND_REDIS_PORT - Redis port</li>
     *   <li>MAGENTO_SESSION_BACKEND_REDIS_SERVER - Session Redis</li>
     * </ul>
     *
     * <p><b>Drupal:</b></p>
     * <ul>
     *   <li>DRUPAL_REDIS_HOST - Redis hostname</li>
     *   <li>DRUPAL_REDIS_PORT - Redis port</li>
     * </ul>
     *
     * @return map of environment variable key-value pairs
     */
    Map<String, String> getCachePluginEnvironment();

    /**
     * Returns whether AUTH is required for Redis.
     *
     * <p>Recommended for production environments. ElastiCache Redis
     * can be configured with AUTH tokens.</p>
     *
     * @return true if Redis AUTH is required (default: true in production)
     */
    default boolean requiresAuth() {
        return true;
    }

    /**
     * Returns the Secrets Manager ARN for cache password.
     *
     * <p>The secret should contain the Redis AUTH password or
     * Memcached SASL credentials.</p>
     *
     * @return Secrets Manager ARN, or null if no auth required
     */
    String getCachePasswordSecretArn();

    /**
     * Returns the cache key prefix for this CMS instance.
     *
     * <p>Useful for multi-site deployments sharing a Redis instance.
     * Each site should have a unique prefix to avoid key collisions.</p>
     *
     * @return cache key prefix (default: application ID)
     */
    default String getCacheKeyPrefix() {
        return "";
    }

    /**
     * Returns the default TTL for cached items in seconds.
     *
     * @return TTL in seconds (default: 3600 = 1 hour)
     */
    default int getDefaultTtlSeconds() {
        return 3600;
    }

    /**
     * Returns whether to enable Redis cluster mode.
     *
     * <p>Cluster mode provides automatic sharding across multiple nodes
     * for improved scalability and availability.</p>
     *
     * @return true to enable cluster mode (default: false)
     */
    default boolean enableClusterMode() {
        return false;
    }

    /**
     * Returns whether to enable TLS for cache connections.
     *
     * <p>Recommended for production environments. ElastiCache supports
     * in-transit encryption with TLS.</p>
     *
     * @return true to enable TLS (default: true in production)
     */
    default boolean enableTls() {
        return true;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return connection timeout (default: 5000ms)
     */
    default int getConnectionTimeoutMs() {
        return 5000;
    }

    /**
     * Returns the read timeout in milliseconds.
     *
     * @return read timeout (default: 1000ms)
     */
    default int getReadTimeoutMs() {
        return 1000;
    }

    /**
     * Returns whether to enable persistent connections.
     *
     * <p>Persistent connections reduce connection overhead but
     * require proper connection pooling configuration.</p>
     *
     * @return true to enable persistent connections (default: true)
     */
    default boolean enablePersistentConnections() {
        return true;
    }
}
