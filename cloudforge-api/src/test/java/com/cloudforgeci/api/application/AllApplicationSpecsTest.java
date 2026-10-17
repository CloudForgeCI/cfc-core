package com.cloudforgeci.api.application;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.InstanceCatalog;
import com.cloudforgeci.api.application.analytics.MetabaseApplicationSpec;
import com.cloudforgeci.api.application.analytics.SupersetApplicationSpec;
import com.cloudforgeci.api.application.artifactregistry.HarborApplicationSpec;
import com.cloudforgeci.api.application.artifactregistry.NexusApplicationSpec;
import com.cloudforgeci.api.application.cicd.DroneApplicationSpec;
import com.cloudforgeci.api.application.cicd.GitLabApplicationSpec;
import com.cloudforgeci.api.application.collaboration.MattermostApplicationSpec;
import com.cloudforgeci.api.application.collaboration.MattermostTeamApplicationSpec;
import com.cloudforgeci.api.application.database.PostgreSQLApplicationSpec;
import com.cloudforgeci.api.application.database.RedisApplicationSpec;
import com.cloudforgeci.api.application.monitoring.GrafanaApplicationSpec;
import com.cloudforgeci.api.application.monitoring.PrometheusApplicationSpec;
import com.cloudforgeci.api.application.secrets.VaultApplicationSpec;
import com.cloudforgeci.api.application.vcs.GiteaApplicationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all ApplicationSpec implementations.
 */
class AllApplicationSpecsTest {

    static Stream<ApplicationSpec> allApplicationSpecs() {
        return Stream.of(
            new JenkinsApplicationSpec(),
            new MetabaseApplicationSpec(),
            new SupersetApplicationSpec(),
            new HarborApplicationSpec(),
            new NexusApplicationSpec(),
            new DroneApplicationSpec(),
            new GitLabApplicationSpec(),
            new MattermostApplicationSpec(),
            new MattermostTeamApplicationSpec(),
            new PostgreSQLApplicationSpec(),
            new RedisApplicationSpec(),
            new GrafanaApplicationSpec(),
            new PrometheusApplicationSpec(),
            new VaultApplicationSpec(),
            new GiteaApplicationSpec()
        );
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testApplicationIdIsNotNull(ApplicationSpec spec) {
        assertNotNull(spec.applicationId());
        assertFalse(spec.applicationId().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testDefaultContainerImageIsValid(ApplicationSpec spec) {
        String image = spec.defaultContainerImage();
        assertNotNull(image);
        assertFalse(image.isEmpty());
        assertTrue(image.contains("/") || image.contains(":"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testApplicationPortIsValid(ApplicationSpec spec) {
        int port = spec.applicationPort();
        assertTrue(port > 0);
        assertTrue(port < 65536);
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testContainerDataPathIsAbsolute(ApplicationSpec spec) {
        String path = spec.containerDataPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testEfsDataPathIsAbsolute(ApplicationSpec spec) {
        String path = spec.efsDataPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testVolumeNameIsValid(ApplicationSpec spec) {
        String volumeName = spec.volumeName();
        assertNotNull(volumeName);
        assertFalse(volumeName.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testContainerUserIsValid(ApplicationSpec spec) {
        String user = spec.containerUser();
        // Some applications may not specify a container user
        if (user != null) {
            assertTrue(user.matches("\\d+:\\d+"));
        }
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testEfsPermissionsAreValid(ApplicationSpec spec) {
        String perms = spec.efsPermissions();
        assertNotNull(perms);
        assertTrue(perms.matches("[0-7]{3}"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testEbsDeviceNameIsValid(ApplicationSpec spec) {
        String device = spec.ebsDeviceName();
        assertNotNull(device);
        assertTrue(device.startsWith("/dev/"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testEc2DataPathIsAbsolute(ApplicationSpec spec) {
        String path = spec.ec2DataPath();
        assertNotNull(path);
        assertTrue(path.startsWith("/"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testEc2LogPathsAreValid(ApplicationSpec spec) {
        var logPaths = spec.ec2LogPaths();
        assertNotNull(logPaths);
        assertFalse(logPaths.isEmpty());
        for (String path : logPaths) {
            assertTrue(path.startsWith("/"));
        }
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testHealthCheckPathIsValid(ApplicationSpec spec) {
        String healthPath = spec.healthCheckPath();
        assertNotNull(healthPath);
        assertTrue(healthPath.startsWith("/"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testContainerEnvironmentVariables(ApplicationSpec spec) {
        var envVars = spec.containerEnvironmentVariables("example.com", true, "none");
        assertNotNull(envVars);
    }

    // Individual application tests
    @Test
    void testJenkinsApplicationId() {
        JenkinsApplicationSpec jenkins = new JenkinsApplicationSpec();
        assertEquals("jenkins", jenkins.applicationId());
        assertEquals(8080, jenkins.applicationPort());
    }

    @Test
    void testMetabaseApplicationId() {
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();
        assertEquals("metabase", metabase.applicationId());
        assertEquals(3000, metabase.applicationPort());
    }

    @Test
    void testSupersetApplicationId() {
        SupersetApplicationSpec superset = new SupersetApplicationSpec();
        assertEquals("superset", superset.applicationId());
        assertEquals(8088, superset.applicationPort());
    }

    @Test
    void testHarborApplicationId() {
        HarborApplicationSpec harbor = new HarborApplicationSpec();
        assertEquals("harbor", harbor.applicationId());
        assertEquals(80, harbor.applicationPort());
    }

    @Test
    void testNexusApplicationId() {
        NexusApplicationSpec nexus = new NexusApplicationSpec();
        assertEquals("nexus", nexus.applicationId());
        assertEquals(8081, nexus.applicationPort());
    }

    @Test
    void testDroneApplicationId() {
        DroneApplicationSpec drone = new DroneApplicationSpec();
        assertEquals("drone", drone.applicationId());
        assertEquals(80, drone.applicationPort());
    }

    @Test
    void testGitLabApplicationId() {
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();
        assertEquals("gitlab", gitlab.applicationId());
        assertEquals(80, gitlab.applicationPort());
    }

    @Test
    void testMattermostEnterpriseApplicationId() {
        MattermostApplicationSpec mattermost = new MattermostApplicationSpec();
        assertEquals("mattermost-enterprise", mattermost.applicationId());
        assertEquals(8065, mattermost.applicationPort());
    }

    @Test
    void testMattermostTeamApplicationId() {
        MattermostTeamApplicationSpec mattermostTeam = new MattermostTeamApplicationSpec();
        assertEquals("mattermost-team", mattermostTeam.applicationId());
        assertEquals(8065, mattermostTeam.applicationPort());
    }

    @Test
    void testPostgreSQLApplicationId() {
        PostgreSQLApplicationSpec postgresql = new PostgreSQLApplicationSpec();
        assertEquals("postgresql", postgresql.applicationId());
        assertEquals(5432, postgresql.applicationPort());
    }

    @Test
    void testRedisApplicationId() {
        RedisApplicationSpec redis = new RedisApplicationSpec();
        assertEquals("redis", redis.applicationId());
        assertEquals(6379, redis.applicationPort());
    }

    @Test
    void testGrafanaApplicationId() {
        GrafanaApplicationSpec grafana = new GrafanaApplicationSpec();
        assertEquals("grafana", grafana.applicationId());
        assertEquals(3000, grafana.applicationPort());
    }

    @Test
    void testPrometheusApplicationId() {
        PrometheusApplicationSpec prometheus = new PrometheusApplicationSpec();
        assertEquals("prometheus", prometheus.applicationId());
        assertEquals(9090, prometheus.applicationPort());
    }

    @Test
    void testVaultApplicationId() {
        VaultApplicationSpec vault = new VaultApplicationSpec();
        assertEquals("vault", vault.applicationId());
        assertEquals(8200, vault.applicationPort());
    }

    @Test
    void testGiteaApplicationId() {
        GiteaApplicationSpec gitea = new GiteaApplicationSpec();
        assertEquals("gitea", gitea.applicationId());
        assertEquals(3000, gitea.applicationPort());
    }

    // ========== Cross-app structural parity checks ==========

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testDefaultCpuIsPositive(ApplicationSpec spec) {
        assertTrue(spec.defaultCpu() > 0, spec.applicationId() + " defaultCpu must be positive");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testDefaultMemoryIsPositive(ApplicationSpec spec) {
        assertTrue(spec.defaultMemory() > 0, spec.applicationId() + " defaultMemory must be positive");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testDisplayNameIsNonEmpty(ApplicationSpec spec) {
        assertNotNull(spec.displayName(), spec.applicationId() + " displayName must not be null");
        assertFalse(spec.displayName().isEmpty(), spec.applicationId() + " displayName must not be empty");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testCategoryIsNonEmpty(ApplicationSpec spec) {
        assertNotNull(spec.category(), spec.applicationId() + " category must not be null");
        assertFalse(spec.category().isEmpty(), spec.applicationId() + " category must not be empty");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testPublicPathsReturnsNonNullList(ApplicationSpec spec) {
        assertNotNull(spec.publicPaths(), spec.applicationId() + " publicPaths() must not return null");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testProtectedPathsReturnsNonNullList(ApplicationSpec spec) {
        assertNotNull(spec.protectedPaths(), spec.applicationId() + " protectedPaths() must not return null");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testSupportsAtLeastOneRuntime(ApplicationSpec spec) {
        assertTrue(spec.supportsFargate() || spec.supportsEc2(),
            spec.applicationId() + " must support at least one runtime (Fargate or EC2)");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testSupportedAuthModesIsNonEmpty(ApplicationSpec spec) {
        var modes = spec.getSupportedAuthModes();
        assertNotNull(modes, spec.applicationId() + " getSupportedAuthModes() must not return null");
        assertFalse(modes.isEmpty(), spec.applicationId() + " must support at least one auth mode");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void testRecommendedAuthModeIsSupported(ApplicationSpec spec) {
        String recommended = spec.getRecommendedAuthMode();
        var supported = spec.getSupportedAuthModes();
        assertNotNull(recommended, spec.applicationId() + " getRecommendedAuthMode() must not return null");
        assertTrue(supported.contains(recommended),
            spec.applicationId() + " recommended auth mode must be one of its own supported modes");
    }

    // OIDC support tests
    @Test
    void testJenkinsSupportsOidc() {
        JenkinsApplicationSpec jenkins = new JenkinsApplicationSpec();
        assertTrue(jenkins.supportsOidcIntegration());
        assertNotNull(jenkins.getOidcIntegration());
    }

    @Test
    void testGitLabSupportsOidc() {
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();
        assertTrue(gitlab.supportsOidcIntegration());
        assertNotNull(gitlab.getOidcIntegration());
    }

    @Test
    void testGrafanaSupportsOidc() {
        GrafanaApplicationSpec grafana = new GrafanaApplicationSpec();
        assertTrue(grafana.supportsOidcIntegration());
        assertNotNull(grafana.getOidcIntegration());
    }

    // ========== Default image tags ==========

    /**
     * Specs whose default image still uses a floating tag. A floating tag changes what a new
     * deployment runs without any change here, which is how GitLab's image began requiring a newer
     * database than the one the spec provisioned. Pin each of these to a version and delete its
     * entry; this test fails when an entry is no longer floating, so the list only shrinks.
     */
    private static final Set<String> FLOATING_TAG_EXCEPTIONS = Set.of(
        "MattermostApplicationSpec", "MattermostTeamApplicationSpec", "NexusApplicationSpec",
        "VaultApplicationSpec", "OpenCartApplicationSpec",
        "GiteaApplicationSpec", "GrafanaApplicationSpec", "PrometheusApplicationSpec",
        "MetabaseApplicationSpec", "SupersetApplicationSpec");

    private static boolean isFloatingTag(String image) {
        // The tag is whatever follows the last ':' that comes after the last '/', so a registry
        // port ("host:5000/app") is not mistaken for one.
        int slash = image.lastIndexOf('/');
        int colon = image.lastIndexOf(':');
        if (image.contains("@sha256:")) {
            return false;
        }
        if (colon <= slash) {
            return true; // no tag at all means latest
        }
        String tag = image.substring(colon + 1);
        // Only "latest" (and no tag at all). Upstream-managed release channels such as Jenkins "lts"
        // are a deliberate choice and are not treated as floating here.
        return tag.equals("latest");
    }

    @Test
    void newApplicationsMustPinTheirDefaultImageVersion() {
        List<ApplicationSpec> registered = ServiceLoader.load(ApplicationSpec.class).stream()
            .map(ServiceLoader.Provider::get)
            .toList();
        assertTrue(registered.size() >= 25, "expected the registered applications, found " + registered.size());

        for (ApplicationSpec spec : registered) {
            String name = spec.getClass().getSimpleName();
            boolean floating = isFloatingTag(spec.defaultContainerImage());
            if (FLOATING_TAG_EXCEPTIONS.contains(name)) {
                assertTrue(floating, name + " now pins " + spec.defaultContainerImage()
                    + "; remove it from FLOATING_TAG_EXCEPTIONS");
            } else {
                assertFalse(floating, name + " uses a floating default image tag: " + spec.defaultContainerImage()
                    + ". Pin a version.");
            }
        }
    }

    @Test
    void floatingTagDetection() {
        assertTrue(isFloatingTag("gitlab/gitlab-ce:latest"));
        assertTrue(isFloatingTag("redis"));
        assertFalse(isFloatingTag("jenkins/jenkins:lts"));
        assertFalse(isFloatingTag("gitlab/gitlab-ce:19.4.2-ce.0"));
        assertFalse(isFloatingTag("registry.example.com:5000/app:1.2"));
        assertTrue(isFloatingTag("registry.example.com:5000/app"));
        assertFalse(isFloatingTag("app@sha256:abc"));
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void defaultInstanceTypeIsOneTheCatalogOffers(ApplicationSpec spec) {
        assertTrue(InstanceCatalog.ec2InstanceTypes().contains(spec.defaultInstanceType()),
            spec.applicationId() + " defaults to EC2 type " + spec.defaultInstanceType()
                + ", which InstanceCatalog does not offer");
    }

    @ParameterizedTest
    @MethodSource("allApplicationSpecs")
    void declaredDatabaseInstanceClassIsOneTheCatalogOffers(ApplicationSpec spec) {
        if (!(spec instanceof DatabaseSpec databaseSpec) || databaseSpec.databaseRequirement() == null
                || databaseSpec.databaseRequirement().type() == DatabaseSpec.DatabaseRequirement.RequirementType.NONE) {
            return;
        }
        assertTrue(InstanceCatalog.databaseInstanceClasses().contains(databaseSpec.databaseRequirement().instanceClass()),
            spec.applicationId() + " declares RDS class " + databaseSpec.databaseRequirement().instanceClass()
                + ", which InstanceCatalog does not offer");
    }
}
