package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalDeploymentPipelineTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private static final TemplateAdapter IDENTITY = new TemplateAdapter() {
        @Override
        public TemplateAdaptationResult adapt(ObjectNode canonicalTemplate, String stackName) {
            return new TemplateAdaptationResult(canonicalTemplate, List.of());
        }

        @Override
        public boolean requiresLocalAuthRuntime(TemplateAdaptationResult result) {
            return false;
        }

        @Override
        public String applicationUrl(TemplateAdaptationResult result) {
            return null;
        }
    };

    private static LocalDeployer deployerFor(String region, List<String> regionsUsed) {
        regionsUsed.add(String.valueOf(region));
        return new LocalDeployer() {
            @Override
            public LocalDeployResult deploy(String stackName, Path template) {
                return new LocalDeployResult(stackName, true, false, List.of(), Map.of());
            }

            @Override
            public void delete(String stackName) {
            }

            @Override
            public boolean stackExists(String stackName) {
                return true;
            }

            @Override
            public Map<String, String> outputs(String stackName) {
                return Map.of();
            }

            @Override
            public void close() {
            }
        };
    }

    private LocalDeploymentRequest request() throws IOException {
        Path canonical = tempDir.resolve("App.template.json");
        Files.writeString(canonical, MAPPER.writeValueAsString(MAPPER.createObjectNode().putObject("Resources")));
        return LocalDeploymentRequest.forTarget(DeploymentTarget.MINISTACK, "App", canonical, tempDir.resolve("out"));
    }

    @Test
    void aRegionalPipelineBuildsItsDeployerForTheRegionGiven() throws IOException {
        List<String> regionsUsed = new ArrayList<>();
        LocalDeploymentPipeline pipeline = LocalDeploymentPipeline.forRegion(
            IDENTITY, region -> deployerFor(region, regionsUsed), null);

        pipeline.deploy(request(), "eu-west-1");
        pipeline.deploy(request());

        assertEquals(List.of("eu-west-1", "null"), regionsUsed, "no region means the deployer's default");
    }

    @Test
    void aSupplierPipelineStillBuildsOneDeployerRegardlessOfRegion() throws IOException {
        List<String> regionsUsed = new ArrayList<>();
        LocalDeploymentPipeline pipeline = new LocalDeploymentPipeline(
            IDENTITY, () -> deployerFor(null, regionsUsed), null);

        pipeline.deploy(request(), "eu-west-1");

        assertEquals(List.of("null"), regionsUsed);
    }
}
