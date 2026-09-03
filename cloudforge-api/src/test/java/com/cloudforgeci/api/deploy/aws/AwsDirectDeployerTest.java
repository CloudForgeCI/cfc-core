package com.cloudforgeci.api.deploy.aws;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.local.DeploymentTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what can be verified without live AWS credentials: pure request-building logic (tag
 * conventions, bucket/region naming) and error paths against an intentionally-unreachable
 * endpoint (same pattern as {@code LocalStackDeployerLifecycleTest} — exercises exception
 * handling in {@code stackExists}/{@code verifyDeployment} without needing a real backend).
 *
 * <p>Does NOT exercise a real {@code deploy()} success path — see {@link AwsDirectDeployer}'s
 * class javadoc for why that's out of reach in this environment.</p>
 */
class AwsDirectDeployerTest {

    private static AwsDirectDeployer unreachableDeployer(String applicationId, RuntimeType runtime) {
        URI unreachable = URI.create("http://127.0.0.1:1");
        StaticCredentialsProvider creds =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        CloudFormationClient cloudFormation = CloudFormationClient.builder()
            .endpointOverride(unreachable)
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .build();
        S3Client s3 = S3Client.builder()
            .endpointOverride(unreachable)
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(creds)
            .build();
        return new AwsDirectDeployer(
            cloudFormation, s3, applicationId, AwsDirectDeployer.runtimeTag(runtime),
            AwsDirectDeployer.templateBucketName("000000000000", "us-east-1"));
    }

    private static AwsDirectDeployer unreachableDeployer(boolean localEmulatorTarget) {
        URI unreachable = URI.create("http://127.0.0.1:1");
        StaticCredentialsProvider creds =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        CloudFormationClient cloudFormation = CloudFormationClient.builder()
            .endpointOverride(unreachable)
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .build();
        S3Client s3 = S3Client.builder()
            .endpointOverride(unreachable)
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(creds)
            .build();
        return new AwsDirectDeployer(
            cloudFormation, s3, "jenkins", AwsDirectDeployer.runtimeTag(RuntimeType.FARGATE),
            AwsDirectDeployer.templateBucketName("000000000000", "us-east-1"), localEmulatorTarget, Region.US_EAST_1);
    }

    @Test
    void physicalStackNameAddsLocalstackSuffixOnlyForLocalEmulatorTargets() {
        // StackListingPolicy.acceptsName requires a "-localstack" suffix for a stack to appear
        // under Manager's LocalStack target view -- without it, a deploy:create stack redirected
        // to a local emulator (see class javadoc) deploys successfully on CloudFormation's side
        // yet never shows up anywhere in Manager's own UI.
        try (AwsDirectDeployer local = unreachableDeployer(true)) {
            assertEquals("cf-d-localstack", local.physicalStackName("cf-d"));
        }
        try (AwsDirectDeployer real = unreachableDeployer(false)) {
            assertEquals("cf-d", real.physicalStackName("cf-d"));
        }
    }

    @Test
    void managedTagsCarryTheCloudForgeConvention() {
        try (AwsDirectDeployer deployer = unreachableDeployer("jenkins", RuntimeType.FARGATE)) {
            List<Tag> tags = deployer.managedTags();
            assertEquals(3, tags.size());
            assertTrue(tags.stream().anyMatch(t ->
                AwsDirectDeployer.TAG_MANAGED.equals(t.key()) && "true".equals(t.value())));
            assertTrue(tags.stream().anyMatch(t ->
                AwsDirectDeployer.TAG_APPLICATION.equals(t.key()) && "jenkins".equals(t.value())));
            assertTrue(tags.stream().anyMatch(t ->
                AwsDirectDeployer.TAG_RUNTIME.equals(t.key()) && "fargate".equals(t.value())));
        }
    }

    @Test
    void managedTagsFallBackToUnknownForMissingApplicationId() {
        try (AwsDirectDeployer deployer = unreachableDeployer(null, null)) {
            List<Tag> tags = deployer.managedTags();
            assertTrue(tags.stream().anyMatch(t ->
                AwsDirectDeployer.TAG_APPLICATION.equals(t.key()) && "unknown".equals(t.value())));
            assertTrue(tags.stream().anyMatch(t ->
                AwsDirectDeployer.TAG_RUNTIME.equals(t.key()) && "unknown".equals(t.value())));
        }
    }

    @Test
    void runtimeTagIsLowercasedEnumName() {
        assertEquals("fargate", AwsDirectDeployer.runtimeTag(RuntimeType.FARGATE));
        assertEquals("ec2", AwsDirectDeployer.runtimeTag(RuntimeType.EC2));
        assertEquals("unknown", AwsDirectDeployer.runtimeTag(null));
    }

    @Test
    void templateBucketNameIncludesAccountAndRegionAndDefaultsRegionWhenBlank() {
        // A bare "cfc-cfn-templates-<region>" name with no account ID would collide with
        // whatever AWS account anywhere had already claimed it -- every headBucket/createBucket/
        // putObject call against a bucket this account doesn't own comes back 403 Access Denied,
        // not a friendlier "already exists".
        assertEquals("cfc-cfn-templates-111111111111-us-west-2",
            AwsDirectDeployer.templateBucketName("111111111111", "us-west-2"));
        assertEquals("cfc-cfn-templates-111111111111-us-east-1",
            AwsDirectDeployer.templateBucketName("111111111111", null));
        assertEquals("cfc-cfn-templates-111111111111-us-east-1",
            AwsDirectDeployer.templateBucketName("111111111111", ""));
    }

    @Test
    void resolveCdkBootstrapParametersRewritesSsmDynamicReferenceToPlainStringDefault()
            throws Exception {
        String template = """
            {
              "Parameters": {
                "BootstrapVersion": {
                  "Type": "AWS::SSM::Parameter::Value<String>",
                  "Default": "/cdk-bootstrap/hnb659fds/version",
                  "Description": "Version of the CDK Bootstrap resources"
                },
                "UnrelatedParam": {
                  "Type": "String",
                  "Default": "keep-me"
                }
              }
            }
            """;

        String rewritten = AwsDirectDeployer.resolveCdkBootstrapParameters(template);
        JsonNode parsed = new ObjectMapper().readTree(rewritten);
        JsonNode bootstrapVersion = parsed.path("Parameters").path("BootstrapVersion");
        JsonNode unrelated = parsed.path("Parameters").path("UnrelatedParam");

        assertEquals("String", bootstrapVersion.path("Type").asText(),
            "BootstrapVersion's Type should be rewritten to a plain String: " + rewritten);
        assertEquals("21", bootstrapVersion.path("Default").asText(),
            "An SSM-path Default should resolve to a plain numeric default: " + rewritten);
        assertEquals("String", unrelated.path("Type").asText());
        assertEquals("keep-me", unrelated.path("Default").asText(),
            "Unrelated parameters must be left untouched: " + rewritten);
        assertTrue(!rewritten.contains("AWS::SSM::Parameter::Value"),
            "The dynamic-reference type must not survive the rewrite: " + rewritten);
    }

    @Test
    void resolveCdkBootstrapParametersLeavesTemplatesWithoutTheSsmTypeUnchanged() throws Exception {
        String template = """
            {"Parameters": {"Plain": {"Type": "String", "Default": "value"}}}
            """;

        String rewritten = AwsDirectDeployer.resolveCdkBootstrapParameters(template);

        assertEquals(template.trim(), rewritten.trim());
    }

    @Test
    void realAwsConstructorDefaultsRegionWhenConfigOmitsIt() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.EC2;
        config.region = null;
        // Building the client is not a network call (AWS SDK v2 clients are lazy) — this only
        // proves construction doesn't NPE when region/credentials aren't resolvable yet.
        try (AwsDirectDeployer deployer = new AwsDirectDeployer(config, DeploymentTarget.AWS)) {
            assertTrue(deployer.managedTags().stream().anyMatch(t ->
                AwsDirectDeployer.TAG_RUNTIME.equals(t.key()) && "ec2".equals(t.value())));
        }
    }

    /** The literal CLI code path (see {@code DeployOptionsTest}'s javadoc): {@code
     *  InteractiveDeployer.deployLocalTarget} never supplies a credentials override, always
     *  reaching this 1-arg constructor. Reflection, not a public accessor — {@code
     *  credentialsOverride} is a genuinely private implementation detail everywhere else. */
    @Test
    void oneArgConstructorLeavesCredentialsOverrideNull() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.region = "us-east-1";

        try (AwsDirectDeployer deployer = new AwsDirectDeployer(config, DeploymentTarget.AWS)) {
            var field = AwsDirectDeployer.class.getDeclaredField("credentialsOverride");
            field.setAccessible(true);
            assertEquals(null, field.get(deployer));
        }
    }

    @Test
    void stackExistsPropagatesConnectionFailuresRatherThanReportingAbsent() {
        try (AwsDirectDeployer deployer = unreachableDeployer("jenkins", RuntimeType.FARGATE)) {
            // An unreachable host raises an SdkClientException (not a 400/404 CloudFormationException),
            // so this proves stackExists doesn't swallow non-CFN failures as "doesn't exist" —
            // it's expected to propagate.
            assertThrows(RuntimeException.class, () -> deployer.stackExists("some-stack"));
        }
    }

    @Test
    void verifyDeploymentFailsWhenStackMissingOrUnreachable() {
        try (AwsDirectDeployer deployer = unreachableDeployer("jenkins", RuntimeType.FARGATE)) {
            assertThrows(Exception.class, () -> deployer.verifyDeployment("missing-stack"));
        }
    }

    @Test
    void deleteIsANoOpWhenStackDoesNotExistEvenAgainstUnreachableEndpoint() {
        // delete() calls stackExists() first; when that throws (unreachable host), delete()
        // should propagate rather than silently doing nothing, since we can't actually confirm
        // absence.
        try (AwsDirectDeployer deployer = unreachableDeployer("jenkins", RuntimeType.FARGATE)) {
            assertThrows(RuntimeException.class, () -> deployer.delete("some-stack"));
        }
    }
}
