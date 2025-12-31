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
 * Test suite for iam-security.guard CloudFormation Guard rules.
 *
 * Validates IAM policy least privilege and trust policy rules.
 * CloudForge Core - Multi-Layer Compliance Validation
 * Layer 3: Template-Level Policy Enforcement (cfn-guard)
 */
class IamSecurityGuardTest {

    private static final String GUARD_FILE_PATH = "/cfn-guard/frameworks/iam-security.guard";

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
        assertTrue(content.contains("IAM Security"), "Should have IAM Security header");
        assertTrue(content.contains("CloudForge Core"), "Should reference CloudForge Core");
        assertTrue(content.contains("Layer 3"), "Should reference Layer 3");
    }

    // ========== IAM Policy Least Privilege Rules ==========

    @Test
    void testIamPolicyFullAdminRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_policy_full_admin"),
            "Should have full admin detection rule");
        assertTrue(content.contains("Action != '*'"),
            "Should check for wildcard Action");
    }

    @Test
    void testIamPolicyUnrestrictedResourceRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_policy_unrestricted_resource"),
            "Should have unrestricted resource rule");
        assertTrue(content.contains("Resource != '*'"),
            "Should check for wildcard Resource");
    }

    @Test
    void testIamPolicyStsAssumeRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_policy_sts_assume"),
            "Should have STS assume role rule");
        assertTrue(content.contains("sts:AssumeRole"),
            "Should check for sts:AssumeRole action");
    }

    @Test
    void testIamPolicyPassRoleRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_policy_iam_passrole"),
            "Should have IAM PassRole rule");
        assertTrue(content.contains("iam:PassRole"),
            "Should check for iam:PassRole action");
    }

    @Test
    void testIamPolicyS3AllAccessRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_policy_s3_allaccess"),
            "Should have S3 all access rule");
        assertTrue(content.contains("s3:*"),
            "Should check for s3:* action");
    }

    // ========== IAM Role Trust Policy Rules ==========

    @Test
    void testIamRolePublicTrustRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_role_assume_public"),
            "Should have public trust policy rule");
        assertTrue(content.contains("Principal != '*'"),
            "Should check for wildcard Principal");
    }

    // ========== IAM User and Group Best Practices ==========

    @Test
    void testIamUserAttachedPoliciesRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_user_attached_policies"),
            "Should have user attached policies rule");
        assertTrue(content.contains("AWS::IAM::User"),
            "Should target IAM User resource type");
    }

    @Test
    void testIamNoInlinePoliciesRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_no_inline_policies"),
            "Should have no inline policies rule");
    }

    // ========== IAM Sensitive Actions Rules ==========

    @Test
    void testIamSensitiveActionsRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_sensitive_actions"),
            "Should have sensitive actions rule");
        assertTrue(content.contains("iam:CreateAccessKey"),
            "Should check for CreateAccessKey action");
        assertTrue(content.contains("iam:AttachUserPolicy"),
            "Should check for AttachUserPolicy action");
    }

    @Test
    void testIamLambdaInvokeAllRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_lambda_invoke_all"),
            "Should have Lambda invoke all rule");
        assertTrue(content.contains("lambda:InvokeFunction"),
            "Should check for Lambda invoke action");
    }

    @Test
    void testIamSecretsManagerAllAccessRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_secretsmanager_allaccess"),
            "Should have Secrets Manager all access rule");
        assertTrue(content.contains("secretsmanager:GetSecretValue"),
            "Should check for GetSecretValue action");
    }

    // ========== KMS Key Policy Rules ==========

    @Test
    void testIamKmsPublicRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule iam_security_kms_public"),
            "Should have KMS public access rule");
        assertTrue(content.contains("AWS::KMS::Key"),
            "Should target KMS Key resource type");
    }

    // ========== CloudForge Mapping Validation ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "ACCESS_CONTROL",
        "KEY_MANAGEMENT"
    })
    void testCloudForgeMappingsExist(String control) throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains(control),
            "Should map to CloudForge control: " + control);
    }

    @Test
    void testAllRulesHaveCloudForgeMapping() throws IOException {
        String content = loadGuardFile();
        // Count rules and mappings
        long ruleCount = content.lines()
            .filter(line -> line.trim().startsWith("rule iam_security"))
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
            .filter(line -> line.trim().startsWith("rule iam_security") || line.trim().startsWith("rule iam_security"))
            .count();

        assertTrue(ruleCount >= 10, "Should have at least 10 IAM security rules");
    }
}
