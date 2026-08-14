package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudForgeSynthesizerTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeploymentConfig jenkinsFargateConfig(String stackName) {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = stackName;
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.securityProfile = SecurityProfile.DEV;
        config.authMode = com.cloudforge.core.enums.AuthMode.NONE;
        return config;
    }

    @Test
    void synthesizesARealTemplateFileForJenkinsFargate() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestJenkins");

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        assertEquals("SynthTestJenkins", result.stackName());
        assertTrue(Files.exists(result.templateFile()), "template file should exist: " + result.templateFile());
        assertTrue(result.templateFile().getFileName().toString().endsWith(".template.json"));

        JsonNode template = MAPPER.readTree(result.templateFile().toFile());
        assertTrue(template.has("Resources"));
        assertTrue(template.get("Resources").size() > 0);
    }

    @Test
    void synthesizedTemplateCarriesTheCloudForgeManagedTagsOnTaggableResources() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestTags");

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        String templateJson = Files.readString(result.templateFile());
        assertTrue(templateJson.contains("cloudforge:managed"));
        assertTrue(templateJson.contains("cloudforge:application"));
        assertTrue(templateJson.contains("\"jenkins\""));
    }

    @Test
    void synthesizeResolvesApplicationSpecFromApplicationIdWhenNotAlreadySet() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestResolve");
        assertTrue(config.applicationSpec == null, "test setup should not have set applicationSpec");

        CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        assertEquals("jenkins", config.applicationSpec.applicationId());
    }

    @Test
    void ec2RuntimeSynthesizesTooViaTheEc2LaunchStack() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestEc2");
        config.runtime = RuntimeType.EC2;

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        JsonNode template = MAPPER.readTree(result.templateFile().toFile());
        assertTrue(template.has("Resources"));
    }

    @Test
    void throwsForUnknownApplicationId() {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestUnknown");
        config.applicationId = "definitely-not-a-registered-app";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out")));
        assertTrue(ex.getMessage().contains("definitely-not-a-registered-app"));
    }

    @Test
    void throwsForMissingStackName() {
        DeploymentConfig config = jenkinsFargateConfig(null);
        config.stackName = null;

        assertThrows(IllegalArgumentException.class,
            () -> CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out")));
    }

    @Test
    void throwsForMissingRuntime() {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestNoRuntime");
        config.runtime = null;

        assertThrows(IllegalArgumentException.class,
            () -> CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out")));
    }

    /**
     * The landmine this closes: {@code VpcFactory.maxAzs(2)} resolves AZs via {@code Fn::GetAZs}
     * (a CloudFormation-time token) only while account+region are both unresolved. Pinning {@code
     * config.account} routes CDK's AZ resolution through its synth-time {@code availability-zones}
     * context provider instead — and since this deploy path never shells out to the {@code cdk}
     * CLI to satisfy that lookup, CDK would silently fall back to its built-in dummy values
     * ({@code dummy1a}/{@code dummy1b}/{@code dummy1c}) baked straight into subnet definitions,
     * which then fails at real CloudFormation. Must never regress.
     */
    @Test
    void synthesizingWithAnExplicitAccountNeverBakesInDummyAvailabilityZones() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestNoDummyAz");
        config.account = "111122223333";

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        String templateJson = Files.readString(result.templateFile());
        assertTrue(templateJson.contains("us-east-1a") || templateJson.contains("us-east-1b"),
            "expected real seeded AZ names in the template, got: " + templateJson);
        assertTrue(!templateJson.contains("dummy1a") && !templateJson.contains("dummy1b")
                && !templateJson.contains("dummy1c"),
            "template must never contain CDK's dummy AZ fallback values: " + templateJson);
    }

    /**
     * Companion to the dummy-AZ regression test above — proves the fix is additive, not a
     * behavior change for every existing caller that never sets {@code config.account} (the
     * default). Same account-agnostic {@code Fn::GetAZs} resolution as before this field existed.
     */
    @Test
    void synthesizingWithoutAnAccountStillUsesDeferredCloudFormationTimeAzResolution() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestNoAccountUnchanged");
        assertTrue(config.account == null, "test setup should not have set an account");

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        String templateJson = Files.readString(result.templateFile());
        assertTrue(templateJson.contains("Fn::GetAZs"),
            "account-agnostic template should still defer AZ resolution to CloudFormation: " + templateJson);
    }

    @Test
    void resultCanFeedDirectlyIntoADeploymentRequest() throws IOException {
        DeploymentConfig config = jenkinsFargateConfig("SynthTestFeedsRequest");

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        // Proves the synthesizer's output shape is exactly what CloudForgeDeployment's façade
        // consumes — the actual point of building this.
        DeploymentRequest request = DeploymentRequest.dryRun(
            config,
            com.cloudforge.core.local.DeploymentTarget.AWS,
            result.templateFile(),
            result.assemblyDirectory());
        assertEquals(result.templateFile(), request.canonicalTemplate());
    }
}
