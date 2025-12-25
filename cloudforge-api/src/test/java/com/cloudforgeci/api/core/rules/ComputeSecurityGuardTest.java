package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for compute-security.guard CloudFormation Guard rules.
 *
 * Validates EC2, EKS, and compute resource security rules.
 * CloudForge Core - Multi-Layer Compliance Validation
 * Layer 3: Template-Level Policy Enforcement (cfn-guard)
 */
class ComputeSecurityGuardTest {

    private static final String GUARD_FILE_PATH = "/cfn-guard/frameworks/compute-security.guard";

    private String loadGuardFile() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(GUARD_FILE_PATH)) {
            assertNotNull(is, "Guard file should exist: " + GUARD_FILE_PATH);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    @Test
    void testGuardFileExists() throws IOException {
        String content = loadGuardFile();
        assertNotNull(content);
        assertFalse(content.isEmpty(), "Guard file should not be empty");
    }

    @Test
    void testGuardFileHasHeader() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("Compute Security"), "Should have Compute Security header");
        assertTrue(content.contains("CloudForge Core"), "Should reference CloudForge Core");
        assertTrue(content.contains("Layer 3"), "Should reference Layer 3");
    }

    // ========== EC2 Instance Security Rules ==========

    @Test
    void testEc2TerminationProtectionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ec2_termination_protection"),
            "Should have EC2 termination protection rule");
        assertTrue(content.contains("DisableApiTermination"),
            "Should check for DisableApiTermination property");
    }

    @Test
    void testEc2EbsOptimizedRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ec2_ebs_optimized"),
            "Should have EC2 EBS optimized rule");
        assertTrue(content.contains("EbsOptimized"),
            "Should check for EbsOptimized property");
    }

    @Test
    void testEc2DetailedMonitoringRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ec2_detailed_monitoring"),
            "Should have EC2 detailed monitoring rule");
        assertTrue(content.contains("Monitoring"),
            "Should check for Monitoring property");
    }

    @Test
    void testEc2IamProfileRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ec2_iam_profile"),
            "Should have EC2 IAM profile rule");
        assertTrue(content.contains("IamInstanceProfile"),
            "Should check for IamInstanceProfile property");
    }

    @Test
    void testEc2BlockDeviceEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ec2_block_device_encryption"),
            "Should have EC2 block device encryption rule");
        assertTrue(content.contains("Ebs.Encrypted"),
            "Should check for EBS encryption");
    }

    @Test
    void testEc2Imdsv2Rule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ec2_imdsv2"),
            "Should have EC2 IMDSv2 rule");
        assertTrue(content.contains("HttpTokens"),
            "Should check for HttpTokens property");
        assertTrue(content.contains("required"),
            "Should require HttpTokens = required");
    }

    // ========== Launch Template Security Rules ==========

    @Test
    void testLaunchTemplateImdsv2Rule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_launch_template_imdsv2"),
            "Should have launch template IMDSv2 rule");
        assertTrue(content.contains("AWS::EC2::LaunchTemplate"),
            "Should target LaunchTemplate resource type");
    }

    @Test
    void testLaunchTemplateEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_launch_template_encryption"),
            "Should have launch template encryption rule");
    }

    // ========== VPC Subnet Security Rules ==========

    @Test
    void testSubnetNoAutoPublicIpRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_subnet_no_auto_public_ip"),
            "Should have subnet auto-assign public IP rule");
        assertTrue(content.contains("MapPublicIpOnLaunch"),
            "Should check for MapPublicIpOnLaunch property");
    }

    // ========== EKS Cluster Security Rules ==========

    @Test
    void testEksPrivateOnlyRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_eks_private_only"),
            "Should have EKS private endpoint rule");
        assertTrue(content.contains("EndpointPublicAccess"),
            "Should check for EndpointPublicAccess property");
        assertTrue(content.contains("EndpointPrivateAccess"),
            "Should check for EndpointPrivateAccess property");
    }

    @Test
    void testEksPublicAccessRestrictedRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_eks_public_access_restricted"),
            "Should have EKS public access restricted rule");
        assertTrue(content.contains("PublicAccessCidrs"),
            "Should check for PublicAccessCidrs property");
        assertTrue(content.contains("0.0.0.0/0"),
            "Should disallow unrestricted CIDR");
    }

    @Test
    void testEksSecretsEncryptedRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_eks_secrets_encrypted"),
            "Should have EKS secrets encryption rule");
        assertTrue(content.contains("EncryptionConfig"),
            "Should check for EncryptionConfig property");
    }

    @Test
    void testEksControlPlaneLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_eks_control_plane_logging"),
            "Should have EKS control plane logging rule");
        assertTrue(content.contains("ClusterLogging"),
            "Should check for ClusterLogging property");
    }

    // ========== EKS Node Group Security Rules ==========

    @Test
    void testEksNodeGroupLaunchTemplateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_eks_nodegroup_launch_template"),
            "Should have EKS node group launch template rule");
        assertTrue(content.contains("AWS::EKS::Nodegroup"),
            "Should target EKS Nodegroup resource type");
    }

    @Test
    void testEksNodeGroupRemoteAccessRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_eks_nodegroup_remote_access"),
            "Should have EKS node group remote access rule");
        assertTrue(content.contains("SourceSecurityGroups"),
            "Should check for SourceSecurityGroups property");
    }

    // ========== Auto Scaling Group Security Rules ==========

    @Test
    void testAsgLaunchTemplateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_asg_launch_template"),
            "Should have ASG launch template rule");
        assertTrue(content.contains("AWS::AutoScaling::AutoScalingGroup"),
            "Should target AutoScalingGroup resource type");
    }

    @Test
    void testAsgHealthCheckRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_asg_health_check"),
            "Should have ASG health check rule");
        assertTrue(content.contains("HealthCheckType"),
            "Should check for HealthCheckType property");
    }

    // ========== EBS Volume Security Rules ==========

    @Test
    void testEbsDeleteOnTerminationRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule compute_security_ebs_delete_on_termination"),
            "Should have EBS delete on termination rule");
        assertTrue(content.contains("DeleteOnTermination"),
            "Should check for DeleteOnTermination property");
    }

    // ========== CloudForge Mapping Validation ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "DELETION_PROTECTION",
        "ACCESS_CONTROL",
        "ENCRYPTION_AT_REST",
        "INSTANCE_METADATA_SECURITY",
        "CONTAINER_SECURITY",
        "HIGH_AVAILABILITY"
    })
    void testCloudForgeMappingsExist(String control) throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains(control),
            "Should map to CloudForge control: " + control);
    }

    @Test
    void testAllRulesHaveCloudForgeMapping() throws IOException {
        String content = loadGuardFile();
        long ruleCount = content.lines()
            .filter(line -> line.trim().startsWith("rule compute_security"))
            .count();
        long mappingCount = content.lines()
            .filter(line -> line.contains("CloudForge Mapping:"))
            .count();

        assertTrue(ruleCount > 0, "Should have at least one rule");
        assertEquals(ruleCount, mappingCount,
            "Each rule should have a CloudForge Mapping");
    }

    @Test
    void testRuleCountIsExpected() throws IOException {
        String content = loadGuardFile();
        long ruleCount = content.lines()
            .filter(line -> line.trim().startsWith("rule compute_security"))
            .count();

        assertTrue(ruleCount >= 15, "Should have at least 15 compute security rules");
    }
}
