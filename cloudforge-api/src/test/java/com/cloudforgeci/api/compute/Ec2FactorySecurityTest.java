package com.cloudforgeci.api.compute;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.deploy.CloudForgeSynthesizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthesizes the EC2 runtime end to end and checks what it creates for each security profile. */
class Ec2FactorySecurityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    /** Synthesizes an EC2-runtime stack and returns its template's {@code Resources} node. */
    private JsonNode synthesize(String stackName, SecurityProfile profile, String frameworks) throws IOException {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = stackName;
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.EC2;
        config.securityProfile = profile;
        config.authMode = AuthMode.NONE;
        if (frameworks != null) {
            config.complianceFrameworks = new ArrayList<>(ComplianceFrameworkType.parseCommaSeparated(frameworks));
            config.complianceMode = ComplianceMode.ADVISORY;
        }
        CloudForgeSynthesizer.Result result = CloudForgeSynthesizer.synthesize(config, tempDir.resolve("cdk.out"));
        return MAPPER.readTree(result.templateFile().toFile()).path("Resources");
    }

    /** Returns every resource in {@code resources} whose CloudFormation {@code Type} matches. */
    private static List<JsonNode> ofType(JsonNode resources, String type) {
        List<JsonNode> matches = new ArrayList<>();
        resources.properties().forEach(e -> {
            if (type.equals(e.getValue().path("Type").asText())) {
                matches.add(e.getValue());
            }
        });
        return matches;
    }

    /** Finds the EC2 instance's own log group (as opposed to any other stack log group). */
    private static JsonNode instanceLogGroup(JsonNode resources) {
        List<JsonNode> groups = new ArrayList<>();
        resources.properties().forEach(e -> {
            if ("AWS::Logs::LogGroup".equals(e.getValue().path("Type").asText()) && e.getKey().contains("Ec2Logs")) {
                groups.add(e.getValue());
            }
        });
        assertEquals(1, groups.size(), "expected exactly one EC2 instance log group");
        return groups.get(0);
    }

    @Test
    void createsAnAutoScalingGroupBackedByALaunchTemplate() throws IOException {
        JsonNode resources = synthesize("Ec2DevTest", SecurityProfile.DEV, null);

        assertEquals(1, ofType(resources, "AWS::AutoScaling::AutoScalingGroup").size());
        assertEquals(1, ofType(resources, "AWS::EC2::LaunchTemplate").size());
    }

    @Test
    void productionRequiresImdsv2OnTheLaunchTemplate() throws IOException {
        JsonNode resources = synthesize("Ec2ProdImds", SecurityProfile.PRODUCTION, "SOC2");

        JsonNode launchTemplate = ofType(resources, "AWS::EC2::LaunchTemplate").get(0);
        assertEquals("required", launchTemplate.path("Properties").path("LaunchTemplateData")
            .path("MetadataOptions").path("HttpTokens").asText());
    }

    @Test
    void productionWithSoc2EncryptsTheInstanceLogGroupWithARotatingKey() throws IOException {
        JsonNode resources = synthesize("Ec2ProdLogs", SecurityProfile.PRODUCTION, "SOC2");

        assertTrue(instanceLogGroup(resources).path("Properties").has("KmsKeyId"),
            "the instance log group should use the KMS key");
        boolean rotatedKeyAllowingLogs = ofType(resources, "AWS::KMS::Key").stream().anyMatch(key ->
            key.path("Properties").path("EnableKeyRotation").asBoolean()
                && key.path("Properties").path("KeyPolicy").toString().contains("Allow CloudWatch Logs"));
        assertTrue(rotatedKeyAllowingLogs, "a rotating key that the CloudWatch Logs service may use should exist");
    }

    @Test
    void devLeavesTheInstanceLogGroupUnencrypted() throws IOException {
        JsonNode resources = synthesize("Ec2DevLogs", SecurityProfile.DEV, null);

        assertFalse(instanceLogGroup(resources).path("Properties").has("KmsKeyId"));
    }

    @Test
    void runsASingleInstanceByDefault() throws IOException {
        JsonNode resources = synthesize("Ec2Capacity", SecurityProfile.DEV, null);

        JsonNode properties = ofType(resources, "AWS::AutoScaling::AutoScalingGroup").get(0).path("Properties");
        assertEquals("1", properties.path("MinSize").asText());
        assertEquals("1", properties.path("MaxSize").asText());
        assertTrue(properties.path("VPCZoneIdentifier").size() >= 1);
    }
}
