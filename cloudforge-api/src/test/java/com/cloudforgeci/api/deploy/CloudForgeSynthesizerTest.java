package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.ComplianceMode;
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

    private DeploymentConfig wordpressFargateConfig(String stackName) {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = stackName;
        config.applicationId = "wordpress";
        config.runtime = RuntimeType.FARGATE;
        config.securityProfile = SecurityProfile.PRODUCTION;
        config.authMode = com.cloudforge.core.enums.AuthMode.NONE;
        // An explicit single-AZ database is a genuine AwsSolutions-RDS3 finding; PRODUCTION alone is Multi-AZ.
        config.databaseMultiAz = false;
        return config;
    }

    /**
     * WordPress on Fargate with a single-AZ database produces an {@code AwsSolutions-RDS3} finding, so this
     * exercises {@code ComplianceMode.ENFORCE} against a violation cdk-nag reports on its own, not a synthetic one.
     */
    @Test
    void enforceModeBlocksSynthesisWhenComplianceFrameworkFindsARealViolation() {
        DeploymentConfig config = wordpressFargateConfig("SynthTestEnforce");
        config.complianceFrameworks = java.util.List.of(ComplianceFrameworkType.SOC2);
        config.complianceMode = ComplianceMode.ENFORCE;

        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class,
            () -> CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out")));

        assertTrue(!ex.findings().isEmpty(), "expected at least one real cdk-nag finding");
        assertTrue(ex.getMessage().contains("SynthTestEnforce"), ex.getMessage());
        assertTrue(ex.getMessage().contains(ex.findings().get(0).ruleId()), ex.getMessage());
    }

    /** Same setup, ADVISORY instead of ENFORCE -- the same real violation exists (cdk-nag itself
     *  doesn't behave differently), but synthesis must still succeed; nothing should block a
     *  deploy just because a caller chose not to enforce. */
    @Test
    void advisoryModeNeverBlocksSynthesisEvenWithRealFindings() throws IOException {
        DeploymentConfig config = wordpressFargateConfig("SynthTestAdvisory");
        config.complianceFrameworks = java.util.List.of(ComplianceFrameworkType.SOC2);
        config.complianceMode = ComplianceMode.ADVISORY;

        CloudForgeSynthesizer.Result result =
            CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));

        assertEquals("SynthTestAdvisory", result.stackName());
    }

    /** {@link CloudForgeSynthesizer#synthesizeAdvisoryDryRun} exists specifically so an on-demand
     *  compliance check (CloudForge Manager's Compliance tab) can see EVERY finding regardless of
     *  {@code complianceMode} -- proves it never throws even when the caller's own
     *  {@code complianceMode}/{@code securityProfile}/{@code auditManagerEnabled} would otherwise
     *  have produced nothing (a DEV-profile stack blocked by a real violation), restores all three
     *  afterward, and surfaces both cdk-nag AND framework-rule findings (the latter otherwise only
     *  ever reaching a {@code Logger.warning} call -- see {@link
     *  com.cloudforgeci.api.core.rules.ComplianceFindingsCollector}). */
    @Test
    void advisoryDryRunNeverThrowsAndReturnsBothCdkNagAndFrameworkFindings() {
        DeploymentConfig config = wordpressFargateConfig("SynthTestDryRun");
        config.securityProfile = SecurityProfile.DEV;
        config.complianceFrameworks = java.util.List.of(ComplianceFrameworkType.SOC2, ComplianceFrameworkType.HIPAA);
        config.complianceMode = ComplianceMode.ENFORCE;
        config.auditManagerEnabled = false;
        // AdvancedMonitoringRules ("always load", doesn't itself honor ComplianceMode) hard-blocks
        // synthesis for PRODUCTION + GDPR/HIPAA without this -- a real, pre-existing gap unrelated
        // to this test's own purpose, worked around the same way its own error message says to.
        config.macieEnabled = true;
        config.macieAutomatedDiscovery = true;
        // DatabaseSecurityRules is the same kind of "always load" validator that doesn't honor
        // ComplianceMode -- it hard-blocks PRODUCTION on any failed database check, including this
        // one, regardless of ADVISORY mode. WordPress provisions a real database, so this fires.
        config.rdsEnhancedMonitoringEnabled = true;

        CloudForgeSynthesizer.ComplianceCheckResult result =
            CloudForgeSynthesizer.synthesizeAdvisoryDryRun(config, tempDir.resolve("cdk.out"));

        assertEquals(null, result.error(), "synthesis should have completed cleanly: " + result.error());
        assertEquals("SynthTestDryRun", result.stackName());
        assertTrue(!result.nagFindings().isEmpty(), "expected a real cdk-nag finding");
        assertTrue(!result.frameworkFindings().isEmpty(), "expected a real HIPAA framework-rule finding");
        assertEquals(ComplianceMode.ENFORCE, config.complianceMode,
            "caller's original complianceMode must be restored after the call");
        assertEquals(SecurityProfile.DEV, config.securityProfile,
            "caller's original securityProfile must be restored after the call");
        assertEquals(Boolean.FALSE, config.auditManagerEnabled,
            "caller's original auditManagerEnabled must be restored after the call");
    }

    /** A real, pre-existing gap this method has to defend against: not every validator in this
     *  codebase honors {@code ComplianceMode} -- {@code AdvancedMonitoringRules} (an "always load"
     *  cross-framework validator) hard-blocks {@code app.synth()} for a PRODUCTION profile
     *  requesting GDPR/HIPAA without {@code macieEnabled}, regardless of {@code complianceMode}.
     *  Proves the dry run surfaces that as {@code error} instead of throwing. */
    @Test
    void advisoryDryRunReportsAnErrorRatherThanThrowingWhenAnAlwaysLoadValidatorHardBlocks() {
        DeploymentConfig config = wordpressFargateConfig("SynthTestDryRunHardBlock");
        config.complianceFrameworks = java.util.List.of(ComplianceFrameworkType.HIPAA);
        config.auditManagerEnabled = true;
        // macieEnabled left false -- deliberately triggers AdvancedMonitoringRules' hard block.

        CloudForgeSynthesizer.ComplianceCheckResult result =
            CloudForgeSynthesizer.synthesizeAdvisoryDryRun(config, tempDir.resolve("cdk.out"));

        assertTrue(result.error() != null && result.error().contains("Macie"), "expected a Macie error: " + result.error());
        assertTrue(result.nagFindings().isEmpty(), "cdk-nag never ran since synthesis never completed");
    }

    /** {@code complianceFrameworksRawOverride} is the escape hatch for tokens with no {@link
     *  ComplianceFrameworkType} entry -- ISO-27001 (a real {@code FrameworkRules} implementation)
     *  and AWS-BEST-PRACTICES (cdk-nag's own generic {@code AwsSolutionsChecks} fallback pack,
     *  independent of any specific compliance framework). Neither could be requested through
     *  {@code config.complianceFrameworks} at all -- that field's enum doesn't have them. */
    @Test
    void advisoryDryRunSupportsRawOverrideTokensWithNoComplianceFrameworkTypeEntry() {
        DeploymentConfig config = wordpressFargateConfig("SynthTestDryRunRawOverride");
        config.complianceFrameworksRawOverride = "ISO-27001,AWS-BEST-PRACTICES";
        config.auditManagerEnabled = true;
        config.macieEnabled = true;
        config.macieAutomatedDiscovery = true;
        // Same DatabaseSecurityRules ADVISORY gap as advisoryDryRunNeverThrowsAndReturnsBoth... above.
        config.rdsEnhancedMonitoringEnabled = true;

        CloudForgeSynthesizer.ComplianceCheckResult result =
            CloudForgeSynthesizer.synthesizeAdvisoryDryRun(config, tempDir.resolve("cdk.out"));

        assertEquals(null, result.error(), "synthesis should have completed cleanly: " + result.error());
        assertTrue(!result.nagFindings().isEmpty(),
            "AWS-BEST-PRACTICES should apply cdk-nag's AwsSolutionsChecks fallback pack");
        assertTrue(!result.frameworkFindings().isEmpty(), "expected a real ISO-27001 framework-rule finding");
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
     * {@code VpcFactory.maxAzs(2)} resolves AZs via {@code Fn::GetAZs} (a CloudFormation-time
     * token) only while account and region are both unresolved. Pinning {@code config.account}
     * routes AZ resolution through CDK's synth-time {@code availability-zones} context provider,
     * and because this path does not invoke the {@code cdk} CLI, CDK would fall back to dummy
     * values ({@code dummy1a}/{@code dummy1b}/{@code dummy1c}) that fail at CloudFormation.
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
     * Companion to the dummy-AZ test above: callers that do not set {@code config.account} (the
     * default) keep account-agnostic {@code Fn::GetAZs} resolution.
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
