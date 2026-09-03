package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentContextCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void persistWritesPrimaryCatalogFile() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Dev";
        config.applicationId = "jenkins";
        config.applicationName = "Jenkins";
        config.environment = "dev";
        config.runtime = RuntimeType.FARGATE;
        config.authMode = AuthMode.APPLICATION_OIDC;

        Path catalogDir = tempDir.resolve("deployment-contexts");
        List<Path> written = DeploymentContextCatalog.persist(config, catalogDir, List.of());

        assertEquals(1, written.size());
        assertTrue(Files.isRegularFile(catalogDir.resolve("Jenkins-Dev.json")));
        String json = Files.readString(catalogDir.resolve("Jenkins-Dev.json"));
        assertTrue(json.contains("jenkins"));
    }

    @Test
    void catalogBodyCarriesComplianceAndSecurityPostureNotJustIdentity() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Prod";
        config.applicationId = "jenkins";
        config.topology = TopologyType.JENKINS_SERVICE;
        config.securityProfile = SecurityProfile.PRODUCTION;
        config.complianceMode = ComplianceMode.ENFORCE;
        config.complianceFrameworks = List.of(ComplianceFrameworkType.SOC2, ComplianceFrameworkType.PCI_DSS);
        config.region = "us-west-2";
        config.domain = "example.com";
        config.fqdn = "jenkins.example.com";

        Map<String, Object> body = DeploymentContextCatalog.catalogBody(config);

        assertEquals("JENKINS_SERVICE", body.get("topology"));
        assertEquals("PRODUCTION", body.get("securityProfile"));
        assertEquals("ENFORCE", body.get("complianceMode"));
        assertEquals(List.of("SOC2", "PCI_DSS"), body.get("complianceFrameworks"));
        assertEquals("us-west-2", body.get("region"));
        assertEquals("example.com", body.get("domain"));
        assertEquals("jenkins.example.com", body.get("fqdn"));
    }

    @Test
    void catalogBodyHandlesUnsetComplianceFieldsWithoutThrowing() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Dev";
        config.applicationId = "jenkins";

        Map<String, Object> body = DeploymentContextCatalog.catalogBody(config);

        assertEquals(List.of(), body.get("complianceFrameworks"));
    }

    @Test
    void persistRegistersTheStackInPanelStacksJsonAsSiblingOfCatalogDir() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Dev";
        config.applicationId = "jenkins";

        Path catalogDir = tempDir.resolve("deployment-contexts");
        DeploymentContextCatalog.persist(config, catalogDir, List.of());

        Path panelStacks = tempDir.resolve("panel-stacks.json");
        assertTrue(Files.isRegularFile(panelStacks), "panel-stacks.json should be created as a sibling of deployment-contexts/");
        JsonNode stacks = new ObjectMapper().readTree(panelStacks.toFile()).path("stacks");
        assertTrue(stacks.isArray());
        assertEquals(1, stacks.size());
        assertEquals("Jenkins-Dev", stacks.get(0).asText());
    }

    /**
     * Regression test for a bare, single-segment relative path — not caught by any other test
     * here, which all use {@code tempDir.resolve(...)}, an absolute, multi-segment path that
     * always has a parent. {@code InteractiveDeployer}'s default {@code
     * DeployOptions} passes {@code catalogDirectory = Path.of("deployment-contexts")} — a bare,
     * single-segment relative path. {@code Path.of("deployment-contexts").resolveSibling
     * ("panel-stacks.json")} has no parent ({@code Path.getParent()} is {@code null} for a
     * single-segment path), which used to NPE inside {@code registerKnownStack}'s {@code
     * Files.createDirectories(null)} call — an exception type its own {@code IOException}-only
     * catch didn't swallow, so it escaped {@code persist()} entirely and, in production, aborted
     * {@code CloudForgeDeployment.finalizeResult} before it reached emulator-edge reconciliation —
     * silently leaving the freshly-deployed stack's hostname unrouted in the local nginx edge
     * (surfaced as "CloudForge application route not found" in a browser).
     */
    @Test
    void persistDoesNotThrowWhenCatalogDirectoryIsABareSingleSegmentRelativePath() throws Exception {
        Path relativeCatalogDir = Path.of("deployment-contexts-regression-test-" + System.nanoTime());
        Path relativePanelStacks = Path.of("panel-stacks.json");
        boolean panelStacksPreexisted = Files.exists(relativePanelStacks);
        try {
            DeploymentConfig config = new DeploymentConfig();
            config.stackName = "RegressionStack";
            config.applicationId = "jenkins";

            List<Path> written = assertDoesNotThrow(() ->
                DeploymentContextCatalog.persist(config, relativeCatalogDir, List.of()));

            assertEquals(1, written.size());
            assertTrue(Files.isRegularFile(relativePanelStacks),
                "panel-stacks.json should still be written directly into the working directory "
                    + "when catalogDirectory has no parent to make it a sibling of");
        } finally {
            Files.deleteIfExists(relativeCatalogDir.resolve("RegressionStack.json"));
            Files.deleteIfExists(relativeCatalogDir);
            if (!panelStacksPreexisted) {
                Files.deleteIfExists(relativePanelStacks);
            }
        }
    }

    @Test
    void persistUpsertsWithoutDuplicatingOrDroppingExistingEntries() throws Exception {
        Path catalogDir = tempDir.resolve("deployment-contexts");
        Path panelStacks = tempDir.resolve("panel-stacks.json");
        Files.createDirectories(tempDir);
        Files.writeString(panelStacks, "{\"stacks\":[\"SomeOtherStack\"]}");

        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Dev";
        config.applicationId = "jenkins";
        DeploymentContextCatalog.persist(config, catalogDir, List.of());
        // Deploying the same stack again must not add a duplicate entry.
        DeploymentContextCatalog.persist(config, catalogDir, List.of());

        JsonNode stacks = new ObjectMapper().readTree(panelStacks.toFile()).path("stacks");
        List<String> names = new ArrayList<>();
        stacks.forEach(n -> names.add(n.asText()));
        assertEquals(List.of("SomeOtherStack", "Jenkins-Dev"), names);
    }
}
