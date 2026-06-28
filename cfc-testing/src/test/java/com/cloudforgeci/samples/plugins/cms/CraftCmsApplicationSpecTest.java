package com.cloudforgeci.samples.plugins.cms;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CraftCmsApplicationSpec plugin.
 *
 * <p>Validates that the Craft CMS plugin is correctly configured,
 * discoverable via ServiceLoader, and exposes accurate spec values.</p>
 */
class CraftCmsApplicationSpecTest {

    private CraftCmsApplicationSpec spec;

    @BeforeEach
    void setUp() {
        spec = new CraftCmsApplicationSpec();
    }

    // ========== Identity ==========

    @Test
    void testApplicationId() {
        assertEquals("craft-cms", spec.applicationId());
    }

    @Test
    void testDefaultContainerImage() {
        assertEquals("craftcms/nginx:8.2", spec.defaultContainerImage());
    }

    @Test
    void testApplicationPort() {
        assertEquals(8080, spec.applicationPort());
    }

    // ========== Craft-specific document root ==========

    @Test
    void testDocumentRootIsWebSubdirectory() {
        // Critical: Craft serves from web/ subdirectory, not the app root.
        // nginx must point at /var/www/html/web, not /var/www/html.
        assertEquals("/var/www/html/web", spec.documentRoot());
        assertNotEquals(spec.containerDataPath(), spec.documentRoot(),
            "Document root must differ from app root for Craft CMS");
    }

    @Test
    void testContainerDataPath() {
        assertEquals("/var/www/html", spec.containerDataPath());
    }

    @Test
    void testMediaUploadPath() {
        assertTrue(spec.mediaUploadPath().startsWith(spec.documentRoot()),
            "Media upload path should be inside the document root (web/)");
    }

    // ========== PHP Runtime ==========

    @Test
    void testPhpVersion() {
        assertEquals("8.2", spec.phpVersion());
    }

    @Test
    void testRequiredPhpExtensions() {
        List<String> extensions = spec.requiredPhpExtensions();
        assertFalse(extensions.isEmpty());
        assertTrue(extensions.contains("pdo_mysql"), "Must include pdo_mysql for database");
        assertTrue(extensions.contains("mbstring"), "Must include mbstring");
        assertTrue(extensions.contains("gd"), "Must include gd for image handling");
    }

    @Test
    void testPhpMemoryLimit() {
        assertTrue(spec.phpMemoryLimit() >= 256,
            "Craft CMS requires at least 256MB memory");
    }

    // ========== Database ==========

    @Test
    void testImplementsDatabaseSpec() {
        assertTrue(spec instanceof DatabaseSpec,
            "CraftCmsApplicationSpec must implement DatabaseSpec — database is required");
    }

    @Test
    void testDatabaseRequirement() {
        DatabaseSpec.DatabaseRequirement req = spec.databaseRequirement();
        assertNotNull(req);
        assertEquals(DatabaseSpec.DatabaseRequirement.RequirementType.REQUIRED, req.type(),
            "Craft CMS always requires a database");
    }

    @Test
    void testDatabaseEnvVars() {
        Map<String, String> env = spec.databaseEnvVars("rds.example.com", 3306, "craft", "craft_user");
        // Craft-native variable names
        assertEquals("mysql", env.get("CRAFT_DB_DRIVER"));
        assertEquals("rds.example.com", env.get("CRAFT_DB_SERVER"));
        assertEquals("3306", env.get("CRAFT_DB_PORT"));
        assertEquals("craft", env.get("CRAFT_DB_DATABASE"));
        assertEquals("craft_user", env.get("CRAFT_DB_USER"));
        // Generic fallbacks also present
        assertEquals("rds.example.com", env.get("DB_HOST"));
    }

    // ========== Redis / Cache ==========

    @Test
    void testSupportsObjectCache() {
        assertTrue(spec.supportsObjectCache());
        assertEquals("redis", spec.preferredCacheBackend());
    }

    @Test
    void testRedisEnvVars() {
        Map<String, String> env = spec.redisEnvVars("redis.example.com", 6379);
        assertEquals("redis.example.com", env.get("CRAFT_REDIS_HOSTNAME"));
        assertEquals("6379", env.get("CRAFT_REDIS_PORT"));
        // Generic fallbacks also present
        assertEquals("redis.example.com", env.get("REDIS_HOST"));
    }

    // ========== S3 / CDN ==========

    @Test
    void testSupportsS3MediaStorage() {
        assertTrue(spec.supportsS3MediaStorage());
        assertEquals("craftcms/aws-s3", spec.s3MediaPlugin());
    }

    @Test
    void testCdnStaticPathsIncludeCpresources() {
        List<String> staticPaths = spec.cdnStaticPaths();
        assertTrue(staticPaths.stream().anyMatch(p -> p.contains("cpresources")),
            "cpresources must be in CDN static paths — it holds Craft's compiled CSS/JS");
    }

    @Test
    void testCdnAdminPathsBypassCdn() {
        List<String> adminPaths = spec.cdnAdminPaths();
        assertTrue(adminPaths.stream().anyMatch(p -> p.contains("admin")),
            "/admin/* must bypass CDN so session cookies are forwarded");
    }

    // ========== Scheduled Tasks ==========

    @Test
    void testHasScheduledTasks() {
        assertTrue(spec.hasScheduledTasks());
        assertTrue(spec.useSystemCron(), "System cron is preferred over Craft's internal runner");
    }

    @Test
    void testCronCommandsRunQueue() {
        Map<String, String> cron = spec.cronCommands("https://example.com");
        assertFalse(cron.isEmpty());
        assertTrue(cron.values().stream().anyMatch(cmd -> cmd.contains("queue/run")),
            "Cron must run Craft's queue worker");
    }

    // ========== Auth / OIDC ==========

    @Test
    void testHealthCheckPathDoesNotRequireAuth() {
        String healthPath = spec.healthCheckPath();
        List<String> publicPaths = spec.publicPaths();
        assertTrue(publicPaths.stream().anyMatch(p ->
                p.equals(healthPath) || healthPath.matches(p.replace("*", ".*"))),
            "Health check path must be in publicPaths so ALB does not gate it with OIDC");
    }

    @Test
    void testProtectedPathsCoversAdminOnly() {
        List<String> protected_ = spec.protectedPaths();
        assertTrue(protected_.stream().anyMatch(p -> p.contains("admin")),
            "/admin/* must be a protected path");
        // Front-end should NOT be in protected paths
        assertFalse(protected_.stream().anyMatch(p -> p.equals("/*")),
            "Craft's public front-end should not require ALB-level auth");
    }

    @Test
    void testSupportedAuthModes() {
        List<String> modes = spec.getSupportedAuthModes();
        assertTrue(modes.contains("alb-oidc"), "alb-oidc should be supported");
        assertTrue(modes.contains("none"), "none should be supported (public sites)");
    }

    // ========== Container Env Vars ==========

    @Test
    void testContainerEnvVarsSetsEnvironment() {
        Map<String, String> env = spec.containerEnvironmentVariables("craft.example.com", true, "alb-oidc");
        assertEquals("production", env.get("CRAFT_ENVIRONMENT"));
        assertTrue(env.get("PRIMARY_SITE_URL").startsWith("https://"),
            "SSL-enabled deployments must use https in PRIMARY_SITE_URL");
    }

    @Test
    void testContainerEnvVarsStagingWithHttp() {
        Map<String, String> env = spec.containerEnvironmentVariables("craft.example.com", false, "none");
        assertEquals("staging", env.get("CRAFT_ENVIRONMENT"));
        assertTrue(env.get("PRIMARY_SITE_URL").startsWith("http://"));
    }

    // ========== Plugin Discovery ==========

    @Test
    void testImplementsCmsSpec() {
        assertTrue(spec instanceof CmsSpec);
    }

    @Test
    void testImplementsApplicationSpec() {
        assertTrue(spec instanceof ApplicationSpec);
    }

    @Test
    void testAnnotationPresent() {
        assertTrue(CraftCmsApplicationSpec.class
            .isAnnotationPresent(com.cloudforge.core.annotation.CmsPlugin.class),
            "@CmsPlugin annotation required for ServiceLoader discovery");
    }

    @Test
    void testAnnotationValues() {
        var annotation = CraftCmsApplicationSpec.class
            .getAnnotation(com.cloudforge.core.annotation.CmsPlugin.class);
        assertNotNull(annotation);
        assertEquals("craft-cms", annotation.value());
        assertEquals("cms", annotation.category());
        assertTrue(annotation.supportsOidc());
        assertTrue(annotation.requiresDatabase());
        assertTrue(annotation.supportsS3Media());
    }

    @Test
    void testServiceLoaderDiscovery() {
        var loader = java.util.ServiceLoader.load(ApplicationSpec.class);
        boolean found = loader.stream()
            .map(java.util.ServiceLoader.Provider::type)
            .anyMatch(clazz -> clazz.equals(CraftCmsApplicationSpec.class));
        assertTrue(found, "CraftCmsApplicationSpec must be discoverable via ServiceLoader");
    }

    @Test
    void testToString() {
        String result = spec.toString();
        assertNotNull(result);
        assertTrue(result.contains("craft-cms"));
    }
}
