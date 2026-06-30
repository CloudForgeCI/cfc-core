package com.cloudforgeci.api.compute;

import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforgeci.api.application.cms.WordPressApplicationSpec;
import com.cloudforgeci.api.application.cms.MagentoApplicationSpec;
import com.cloudforgeci.api.application.cms.DrupalApplicationSpec;
import com.cloudforgeci.api.application.cms.PhpBBApplicationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PhpContainerFactoryTest {

    static Stream<CmsSpec> allSpecs() {
        return Stream.of(
            new WordPressApplicationSpec(),
            new MagentoApplicationSpec(),
            new DrupalApplicationSpec(),
            new PhpBBApplicationSpec()
        );
    }

    // ===== createEnvironment =====

    @Test
    void createEnvironmentContainsMemoryLimit() {
        PhpRuntimeConfig config = PhpRuntimeConfig.defaults();
        Map<String, String> env = PhpContainerFactory.createEnvironment(new WordPressApplicationSpec(), config);
        assertNotNull(env);
        assertTrue(env.containsKey("PHP_MEMORY_LIMIT"), "Must contain PHP_MEMORY_LIMIT");
        assertTrue(env.get("PHP_MEMORY_LIMIT").endsWith("M"));
    }

    @Test
    void createEnvironmentContainsUploadLimit() {
        PhpRuntimeConfig config = PhpRuntimeConfig.defaults();
        Map<String, String> env = PhpContainerFactory.createEnvironment(new WordPressApplicationSpec(), config);
        assertTrue(env.containsKey("PHP_UPLOAD_MAX_FILESIZE"));
        assertTrue(env.containsKey("PHP_POST_MAX_SIZE"));
    }

    @Test
    void createEnvironmentContainsMaxExecutionTime() {
        PhpRuntimeConfig config = PhpRuntimeConfig.defaults();
        Map<String, String> env = PhpContainerFactory.createEnvironment(new WordPressApplicationSpec(), config);
        assertTrue(env.containsKey("PHP_MAX_EXECUTION_TIME"));
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void createEnvironmentIsNonEmptyForAllSpecs(CmsSpec spec) {
        PhpRuntimeConfig config = PhpRuntimeConfig.defaults();
        Map<String, String> env = PhpContainerFactory.createEnvironment(spec, config);
        assertNotNull(env);
        assertFalse(env.isEmpty());
    }

    // ===== generateFpmConfig =====

    @Test
    void generateFpmConfigIsNonEmpty() {
        PhpRuntimeConfig config = PhpRuntimeConfig.defaults();
        String fpm = PhpContainerFactory.generateFpmConfig(new WordPressApplicationSpec(), config);
        assertNotNull(fpm);
        assertFalse(fpm.isBlank());
    }

    @Test
    void generateFpmConfigContainsPoolSettings() {
        PhpRuntimeConfig config = PhpRuntimeConfig.defaults();
        String fpm = PhpContainerFactory.generateFpmConfig(new WordPressApplicationSpec(), config);
        assertTrue(fpm.contains("pm") || fpm.contains("pool") || fpm.contains("www"),
            "FPM config must contain pool/pm settings");
    }

    // ===== getBaseImage =====

    @Test
    void getBaseImageForPhp82() {
        String image = PhpContainerFactory.getBaseImage("8.2");
        assertEquals("php:8.2-fpm-alpine", image);
    }

    @Test
    void getBaseImageForPhp81() {
        String image = PhpContainerFactory.getBaseImage("8.1");
        assertEquals("php:8.1-fpm-alpine", image);
    }

    // ===== getCmsImage =====

    @Test
    void getCmsImageForWordPress() {
        String image = PhpContainerFactory.getCmsImage(new WordPressApplicationSpec());
        assertNotNull(image);
        assertFalse(image.isBlank());
        assertTrue(image.toLowerCase().contains("wordpress") || image.contains("php"),
            "WordPress image should contain 'wordpress' or 'php'");
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void getCmsImageIsNonEmptyForAllSpecs(CmsSpec spec) {
        String image = PhpContainerFactory.getCmsImage(spec);
        assertNotNull(image);
        assertFalse(image.isBlank());
    }

    // ===== generateNginxConfig =====

    @Test
    void generateNginxConfigIsNonEmpty() {
        String nginx = PhpContainerFactory.generateNginxConfig(new WordPressApplicationSpec());
        assertNotNull(nginx);
        assertFalse(nginx.isBlank());
    }

    @Test
    void generateNginxConfigContainsServerBlock() {
        String nginx = PhpContainerFactory.generateNginxConfig(new WordPressApplicationSpec());
        assertTrue(nginx.contains("server") || nginx.contains("listen"),
            "NGINX config must contain server/listen directives");
    }

    @Test
    void generateNginxConfigContainsDocumentRoot() {
        String nginx = PhpContainerFactory.generateNginxConfig(new WordPressApplicationSpec());
        assertTrue(nginx.contains("/var/www/html"), "NGINX config must reference document root");
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void generateNginxConfigIsNonEmptyForAllSpecs(CmsSpec spec) {
        String nginx = PhpContainerFactory.generateNginxConfig(spec);
        assertNotNull(nginx);
        assertFalse(nginx.isBlank());
    }

    // ===== generateWordPressNginxConfig =====

    @Test
    void generateWordPressNginxConfigBasic() {
        String nginx = PhpContainerFactory.generateWordPressNginxConfig("/var/www/html", 80, false);
        assertNotNull(nginx);
        assertTrue(nginx.contains("listen") || nginx.contains("server"));
        assertTrue(nginx.contains("80"));
    }

    @Test
    void generateWordPressNginxConfigMultisite() {
        String nginx = PhpContainerFactory.generateWordPressNginxConfig("/var/www/html", 80, true);
        assertNotNull(nginx);
        assertFalse(nginx.isBlank());
    }

    // ===== generateMagentoNginxConfig =====

    @Test
    void generateMagentoNginxConfigIsNonEmpty() {
        String nginx = PhpContainerFactory.generateMagentoNginxConfig("/var/www/html", 80);
        assertNotNull(nginx);
        assertFalse(nginx.isBlank());
        assertTrue(nginx.contains("listen") || nginx.contains("server"));
    }

    // ===== createDatabaseEnvironment =====

    @Test
    void createDatabaseEnvironmentContainsHost() {
        Map<String, String> env = PhpContainerFactory.createDatabaseEnvironment(
            new WordPressApplicationSpec(), "db.example.com", 3306, "wpdb", "wpuser");
        assertNotNull(env);
        assertTrue(env.values().stream().anyMatch(v -> v.contains("db.example.com")));
    }

    @Test
    void createDatabaseEnvironmentContainsDbName() {
        Map<String, String> env = PhpContainerFactory.createDatabaseEnvironment(
            new WordPressApplicationSpec(), "db.example.com", 3306, "wpdb", "wpuser");
        assertTrue(env.values().stream().anyMatch(v -> v.contains("wpdb")));
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void createDatabaseEnvironmentIsNonEmptyForAllSpecs(CmsSpec spec) {
        Map<String, String> env = PhpContainerFactory.createDatabaseEnvironment(
            spec, "db.example.com", 3306, "testdb", "testuser");
        assertNotNull(env);
        assertFalse(env.isEmpty());
    }

    // ===== createRedisEnvironment =====

    @Test
    void createRedisEnvironmentContainsHost() {
        Map<String, String> env = PhpContainerFactory.createRedisEnvironment(
            new WordPressApplicationSpec(), "redis.example.com", 6379);
        assertNotNull(env);
        assertTrue(env.values().stream().anyMatch(v -> v.contains("redis.example.com")));
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void createRedisEnvironmentIsNonEmptyForAllSpecs(CmsSpec spec) {
        Map<String, String> env = PhpContainerFactory.createRedisEnvironment(
            spec, "redis.example.com", 6379);
        assertNotNull(env);
        assertFalse(env.isEmpty());
    }
}
