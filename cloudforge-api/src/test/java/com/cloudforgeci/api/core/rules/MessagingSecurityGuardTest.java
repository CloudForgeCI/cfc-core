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
 * Test suite for messaging-security.guard CloudFormation Guard rules.
 *
 * Validates SQS, SNS, Secrets Manager, EventBridge, and Kinesis security rules.
 * CloudForge Core - Multi-Layer Compliance Validation
 * Layer 3: Template-Level Policy Enforcement (cfn-guard)
 */
class MessagingSecurityGuardTest {

    private static final String GUARD_FILE_PATH = "/cfn-guard/frameworks/messaging-security.guard";

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
        assertTrue(content.contains("Messaging Security"), "Should have Messaging Security header");
        assertTrue(content.contains("CloudForge Core"), "Should reference CloudForge Core");
        assertTrue(content.contains("Layer 3"), "Should reference Layer 3");
    }

    // ========== SQS Queue Security Rules ==========

    @Test
    void testSqsEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_sqs_encryption"),
            "Should have SQS encryption rule");
        assertTrue(content.contains("KmsMasterKeyId"),
            "Should check for KmsMasterKeyId property");
    }

    @Test
    void testSqsSseRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_sqs_sse"),
            "Should have SQS SSE rule");
        assertTrue(content.contains("SqsManagedSseEnabled"),
            "Should check for SqsManagedSseEnabled property");
    }

    @Test
    void testSqsDlqRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_sqs_dlq"),
            "Should have SQS DLQ rule");
        assertTrue(content.contains("RedrivePolicy"),
            "Should check for RedrivePolicy property");
        assertTrue(content.contains("deadLetterTargetArn"),
            "Should check for deadLetterTargetArn property");
    }

    @Test
    void testSqsVisibilityTimeoutRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_sqs_visibility_timeout"),
            "Should have SQS visibility timeout rule");
        assertTrue(content.contains("VisibilityTimeout"),
            "Should check for VisibilityTimeout property");
    }

    // ========== SNS Topic Security Rules ==========

    @Test
    void testSnsEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_sns_encryption"),
            "Should have SNS encryption rule");
        assertTrue(content.contains("AWS::SNS::Topic"),
            "Should target SNS Topic resource type");
    }

    @Test
    void testSnsPolicyRestrictionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_sns_policy_restriction"),
            "Should have SNS policy restriction rule");
        assertTrue(content.contains("Principal != '*'"),
            "Should disallow public access");
    }

    // ========== Secrets Manager Security Rules ==========

    @Test
    void testSecretsManagerReplicationRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_secretsmanager_replication"),
            "Should have Secrets Manager replication rule");
        assertTrue(content.contains("ReplicaRegions"),
            "Should check for ReplicaRegions property");
    }

    @Test
    void testSecretsManagerRotationRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_secretsmanager_rotation"),
            "Should have Secrets Manager rotation rule");
        assertTrue(content.contains("RotationRules"),
            "Should check for RotationRules property");
        assertTrue(content.contains("AutomaticallyAfterDays"),
            "Should check for AutomaticallyAfterDays property");
    }

    @Test
    void testSecretsManagerKmsRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_secretsmanager_kms"),
            "Should have Secrets Manager KMS rule");
        assertTrue(content.contains("KmsKeyId"),
            "Should check for KmsKeyId property");
    }

    // ========== EventBridge Security Rules ==========

    @Test
    void testEventBridgeTargetRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_eventbridge_target"),
            "Should have EventBridge target rule");
        assertTrue(content.contains("Targets"),
            "Should check for Targets property");
    }

    @Test
    void testEventBridgeDlqRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_eventbridge_dlq"),
            "Should have EventBridge DLQ rule");
        assertTrue(content.contains("DeadLetterConfig"),
            "Should check for DeadLetterConfig property");
    }

    // ========== Kinesis Stream Security Rules ==========

    @Test
    void testKinesisEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_kinesis_encryption"),
            "Should have Kinesis encryption rule");
        assertTrue(content.contains("StreamEncryption"),
            "Should check for StreamEncryption property");
        assertTrue(content.contains("EncryptionType"),
            "Should check for EncryptionType property");
    }

    @Test
    void testKinesisMonitoringRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_kinesis_monitoring"),
            "Should have Kinesis monitoring rule");
        assertTrue(content.contains("StreamModeDetails"),
            "Should check for StreamModeDetails property");
    }

    // ========== Kinesis Firehose Security Rules ==========

    @Test
    void testFirehoseEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_firehose_encryption"),
            "Should have Firehose encryption rule");
        assertTrue(content.contains("DeliveryStreamEncryptionConfigurationInput"),
            "Should check for encryption configuration");
    }

    @Test
    void testFirehoseS3EncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule messaging_security_firehose_s3_encryption"),
            "Should have Firehose S3 encryption rule");
        assertTrue(content.contains("S3DestinationConfiguration"),
            "Should check for S3DestinationConfiguration property");
    }

    // ========== CloudForge Mapping Validation ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "ENCRYPTION_AT_REST",
        "ACCESS_CONTROL",
        "HIGH_AVAILABILITY",
        "KEY_MANAGEMENT",
        "ERROR_HANDLING",
        "CONFIGURATION_MANAGEMENT"
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
            .filter(line -> line.trim().startsWith("rule messaging_security"))
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
            .filter(line -> line.trim().startsWith("rule messaging_security"))
            .count();

        assertTrue(ruleCount >= 14, "Should have at least 14 messaging security rules");
    }

    @Test
    void testSecretsRotationWithin90Days() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("AutomaticallyAfterDays <= 90"),
            "Should require secrets rotation within 90 days");
    }

    @Test
    void testSqsVisibilityTimeoutMinimum() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("VisibilityTimeout >= 30"),
            "Should require minimum 30 second visibility timeout");
    }
}
