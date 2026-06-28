package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.oidc.CognitoOidcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AllCmsSpecsTest {

    static Stream<CmsSpec> allCmsSpecs() {
        return Stream.of(
            new WordPressApplicationSpec(),
            new WooCommerceApplicationSpec(),
            new DrupalApplicationSpec(),
            new JoomlaApplicationSpec(),
            new Typo3ApplicationSpec(),
            new ConcreteCmsApplicationSpec(),
            new OctoberCmsApplicationSpec(),
            new MagentoApplicationSpec(),
            new PrestaShopApplicationSpec(),
            new OpenCartApplicationSpec(),
            new SyliusApplicationSpec(),
            new BagistoApplicationSpec(),
            new PhpBBApplicationSpec(),
            new FlarumApplicationSpec(),
            new MyBBApplicationSpec(),
            new SuiteCrmApplicationSpec(),
            new MediaWikiApplicationSpec(),
            new MoodleApplicationSpec(),
            new DolphinApplicationSpec()
        );
    }

    // ========== ApplicationSpec contract ==========

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void applicationIdIsNonEmpty(CmsSpec spec) {
        assertNotNull(spec.applicationId());
        assertFalse(spec.applicationId().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void applicationPortIsValid(CmsSpec spec) {
        int port = spec.applicationPort();
        assertTrue(port > 0 && port < 65536);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void containerDataPathIsAbsolute(CmsSpec spec) {
        String path = spec.containerDataPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"), "containerDataPath must be absolute: " + path);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void efsDataPathIsAbsolute(CmsSpec spec) {
        String path = spec.efsDataPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"), "efsDataPath must be absolute: " + path);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void volumeNameIsNonEmpty(CmsSpec spec) {
        assertNotNull(spec.volumeName());
        assertFalse(spec.volumeName().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void healthCheckPathIsAbsolute(CmsSpec spec) {
        String path = spec.healthCheckPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"), "healthCheckPath must start with /: " + path);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void defaultContainerImageIsNonEmpty(CmsSpec spec) {
        String image = spec.defaultContainerImage();
        assertNotNull(image);
        assertFalse(image.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void efsPermissionsAreOctal(CmsSpec spec) {
        String perms = spec.efsPermissions();
        assertNotNull(perms);
        assertTrue(perms.matches("[0-7]{3}"), "efsPermissions must be 3-digit octal: " + perms);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void ec2LogPathsAreAbsolute(CmsSpec spec) {
        var logPaths = spec.ec2LogPaths();
        assertNotNull(logPaths);
        assertFalse(logPaths.isEmpty());
        for (String path : logPaths) {
            assertTrue(path.startsWith("/"), "log path must be absolute: " + path);
        }
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void containerEnvironmentVariablesReturnsMap(CmsSpec spec) {
        Map<String, String> env = spec.containerEnvironmentVariables("example.com", true, "none");
        assertNotNull(env);
    }

    // ========== CmsSpec contract ==========

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void phpVersionIsNonEmpty(CmsSpec spec) {
        String version = spec.phpVersion();
        assertNotNull(version);
        assertFalse(version.isEmpty());
        assertTrue(version.matches("\\d+\\.\\d+.*"), "phpVersion should be dotted: " + version);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void cmsCategoryIsNonEmpty(CmsSpec spec) {
        String cat = spec.cmsCategory();
        assertNotNull(cat);
        assertFalse(cat.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void requiredPhpExtensionsIsNonEmpty(CmsSpec spec) {
        var exts = spec.requiredPhpExtensions();
        assertNotNull(exts);
        assertFalse(exts.isEmpty(), spec.applicationId() + " must declare at least one PHP extension");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void mediaUploadPathIsAbsolute(CmsSpec spec) {
        String path = spec.mediaUploadPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"), "mediaUploadPath must be absolute: " + path);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void redisEnvVarsContainHostAndPort(CmsSpec spec) {
        Map<String, String> env = spec.redisEnvVars("redis.example.com", 6379);
        assertNotNull(env);
        assertFalse(env.isEmpty(), spec.applicationId() + " must return Redis env vars");
        assertTrue(env.values().stream().anyMatch(v -> v.contains("redis.example.com")),
            spec.applicationId() + " Redis env vars must contain the host");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void databaseEnvVarsContainHostAndName(CmsSpec spec) {
        Map<String, String> env = spec.databaseEnvVars("db.example.com", 3306, "mydb", "dbuser");
        assertNotNull(env);
        assertFalse(env.isEmpty(), spec.applicationId() + " must return DB env vars");
        assertTrue(env.values().stream().anyMatch(v -> v.contains("db.example.com")),
            spec.applicationId() + " DB env vars must contain the host");
        assertTrue(env.values().stream().anyMatch(v -> v.contains("mydb")),
            spec.applicationId() + " DB env vars must contain the database name");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void defaultCpuIsPositive(CmsSpec spec) {
        assertTrue(spec.defaultCpu() > 0);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void defaultMemoryIsPositive(CmsSpec spec) {
        assertTrue(spec.defaultMemory() > 0);
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void supportsFargate(CmsSpec spec) {
        assertTrue(spec.supportsFargate(), spec.applicationId() + " must support Fargate");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void supportsEc2(CmsSpec spec) {
        assertTrue(spec.supportsEc2(), spec.applicationId() + " must support EC2");
    }

    // ========== Individual ID / port spot-checks ==========

    @Test
    void wordPressId() {
        assertEquals("wordpress", new WordPressApplicationSpec().applicationId());
        assertEquals(80, new WordPressApplicationSpec().applicationPort());
        assertEquals("cms", new WordPressApplicationSpec().cmsCategory());
    }

    @Test
    void wooCommerceId() {
        WooCommerceApplicationSpec spec = new WooCommerceApplicationSpec();
        assertEquals("woocommerce", spec.applicationId());
        assertEquals("ecommerce", spec.cmsCategory());
    }

    @Test
    void drupalId() {
        DrupalApplicationSpec spec = new DrupalApplicationSpec();
        assertEquals("drupal", spec.applicationId());
        assertEquals(80, spec.applicationPort());
        assertEquals("cms", spec.cmsCategory());
    }

    @Test
    void magentoId() {
        MagentoApplicationSpec spec = new MagentoApplicationSpec();
        assertEquals("magento", spec.applicationId());
        assertEquals("ecommerce", spec.cmsCategory());
    }

    @Test
    void phpBBPort() {
        assertEquals(8080, new PhpBBApplicationSpec().applicationPort());
    }

    @Test
    void dolphinId() {
        DolphinApplicationSpec spec = new DolphinApplicationSpec();
        assertEquals("dolphin-una", spec.applicationId());
        assertEquals("social", spec.cmsCategory());
    }

    @Test
    void moodleCategory() {
        assertEquals("lms", new MoodleApplicationSpec().cmsCategory());
    }

    @Test
    void mediaWikiCategory() {
        assertEquals("wiki", new MediaWikiApplicationSpec().cmsCategory());
    }

    @Test
    void suiteCrmCategory() {
        assertEquals("crm", new SuiteCrmApplicationSpec().cmsCategory());
    }

    // ========== OIDC ==========

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void supportsOidcIntegration(CmsSpec spec) {
        assertTrue(spec.supportsOidcIntegration(), spec.applicationId() + " must support OIDC");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void oidcIntegrationIsNonNull(CmsSpec spec) {
        assertNotNull(spec.getOidcIntegration(),
            spec.applicationId() + " must return a non-null OidcIntegration");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void publicPathsReturnsNonNullList(CmsSpec spec) {
        assertNotNull(spec.publicPaths(), spec.applicationId() + " publicPaths() must not return null");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void protectedPathsReturnsNonNullList(CmsSpec spec) {
        assertNotNull(spec.protectedPaths(), spec.applicationId() + " protectedPaths() must not return null");
    }

    @ParameterizedTest
    @MethodSource("allCmsSpecs")
    void oidcIntegrationEnvVarsReturnsMap(CmsSpec spec) {
        var config = new CognitoOidcConfiguration(
            "us-east-1", "us-east-1_abc123", "myapp",
            "client-id", "arn:aws:secretsmanager:us-east-1:123456789012:secret:s",
            "https://app.example.com/callback", "Admins");
        var env = spec.getOidcIntegration().getEnvironmentVariables(config);
        assertNotNull(env);
    }
}
