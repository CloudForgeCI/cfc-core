package com.cloudforgeci.api.application;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforgeci.api.application.analytics.MetabaseApplicationSpec;
import com.cloudforgeci.api.application.analytics.SupersetApplicationSpec;
import com.cloudforgeci.api.application.artifactregistry.HarborApplicationSpec;
import com.cloudforgeci.api.application.artifactregistry.NexusApplicationSpec;
import com.cloudforgeci.api.application.cicd.DroneApplicationSpec;
import com.cloudforgeci.api.application.cicd.GitLabApplicationSpec;
import com.cloudforgeci.api.application.collaboration.MattermostApplicationSpec;
import com.cloudforgeci.api.application.database.PostgreSQLApplicationSpec;
import com.cloudforgeci.api.application.database.RedisApplicationSpec;
import com.cloudforgeci.api.application.monitoring.GrafanaApplicationSpec;
import com.cloudforgeci.api.application.monitoring.PrometheusApplicationSpec;
import com.cloudforgeci.api.application.secrets.VaultApplicationSpec;
import com.cloudforgeci.api.application.vcs.GiteaApplicationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    void testMattermostApplicationId() {
        MattermostApplicationSpec mattermost = new MattermostApplicationSpec();
        assertEquals("mattermost", mattermost.applicationId());
        assertEquals(8065, mattermost.applicationPort());
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
}
