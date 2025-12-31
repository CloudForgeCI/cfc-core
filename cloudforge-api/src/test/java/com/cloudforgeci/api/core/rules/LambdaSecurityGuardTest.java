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
 * Test suite for lambda-security.guard CloudFormation Guard rules.
 *
 * Validates Lambda function runtime, network, code signing, and resource management rules.
 * CloudForge Core - Multi-Layer Compliance Validation
 * Layer 3: Template-Level Policy Enforcement (cfn-guard)
 */
class LambdaSecurityGuardTest {

    private static final String GUARD_FILE_PATH = "/cfn-guard/frameworks/lambda-security.guard";

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
        assertTrue(content.contains("Lambda Security"), "Should have Lambda Security header");
        assertTrue(content.contains("CloudForge Core"), "Should reference CloudForge Core");
        assertTrue(content.contains("Layer 3"), "Should reference Layer 3");
    }

    // ========== Lambda Runtime Security Rules ==========

    @Test
    void testLambdaObsoleteRuntimeRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_obsolete_runtime"),
            "Should have obsolete runtime detection rule");
        assertTrue(content.contains("python2.7"),
            "Should detect deprecated Python 2.7");
        assertTrue(content.contains("nodejs10.x"),
            "Should detect deprecated Node.js 10.x");
    }

    // ========== Lambda Network Security Rules ==========

    @Test
    void testLambdaInVpcRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_in_vpc"),
            "Should have Lambda VPC rule");
        assertTrue(content.contains("VpcConfig"),
            "Should check for VpcConfig property");
        assertTrue(content.contains("SubnetIds"),
            "Should check for SubnetIds property");
        assertTrue(content.contains("SecurityGroupIds"),
            "Should check for SecurityGroupIds property");
    }

    // ========== Lambda Error Handling Rules ==========

    @Test
    void testLambdaDeadLetterQueueRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_dead_letter_queue"),
            "Should have dead letter queue rule");
        assertTrue(content.contains("DeadLetterConfig"),
            "Should check for DeadLetterConfig property");
        assertTrue(content.contains("TargetArn"),
            "Should check for TargetArn property");
    }

    // ========== Lambda Code Security Rules ==========

    @Test
    void testLambdaCodeSigningRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_code_signing"),
            "Should have code signing rule");
        assertTrue(content.contains("CodeSigningConfigArn"),
            "Should check for CodeSigningConfigArn property");
    }

    // ========== Lambda Resource Management Rules ==========

    @Test
    void testLambdaConcurrentExecutionLimitRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_concurrent_execution_limit"),
            "Should have concurrent execution limit rule");
        assertTrue(content.contains("ReservedConcurrentExecutions"),
            "Should check for ReservedConcurrentExecutions property");
    }

    @Test
    void testLambdaMemoryConfiguredRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_memory_configured"),
            "Should have memory configuration rule");
        assertTrue(content.contains("MemorySize"),
            "Should check for MemorySize property");
    }

    @Test
    void testLambdaTimeoutConfiguredRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_timeout_configured"),
            "Should have timeout configuration rule");
        assertTrue(content.contains("Timeout"),
            "Should check for Timeout property");
    }

    // ========== Lambda Monitoring Rules ==========

    @Test
    void testLambdaXrayTracingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_xray_tracing"),
            "Should have X-Ray tracing rule");
        assertTrue(content.contains("TracingConfig"),
            "Should check for TracingConfig property");
        assertTrue(content.contains("Active"),
            "Should check for Active mode");
    }

    // ========== Lambda Environment Security Rules ==========

    @Test
    void testLambdaEnvEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_env_encryption"),
            "Should have environment encryption rule");
        assertTrue(content.contains("KmsKeyArn"),
            "Should check for KmsKeyArn property");
    }

    // ========== Lambda Version and Alias Rules ==========

    @Test
    void testLambdaVersioningRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_versioning"),
            "Should have versioning rule");
        assertTrue(content.contains("FunctionVersion"),
            "Should check for FunctionVersion property");
    }

    @Test
    void testLambdaProvisionedConcurrencyRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_provisioned_concurrency"),
            "Should have provisioned concurrency rule");
        assertTrue(content.contains("ProvisionedConcurrencyConfig"),
            "Should check for ProvisionedConcurrencyConfig property");
    }

    // ========== Lambda Permission Security Rules ==========

    @Test
    void testLambdaPermissionPrincipalRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_permission_principal"),
            "Should have permission principal rule");
        assertTrue(content.contains("Principal != '*'"),
            "Should disallow public principal");
    }

    @Test
    void testLambdaPermissionSourceRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule lambda_security_permission_source"),
            "Should have permission source rule");
        assertTrue(content.contains("SourceArn"),
            "Should check for SourceArn property");
        assertTrue(content.contains("SourceAccount"),
            "Should check for SourceAccount property");
    }

    // ========== CloudForge Mapping Validation ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "LAMBDA_SECURITY",
        "ACCESS_CONTROL",
        "ENCRYPTION_AT_REST",
        "MONITORING"
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
            .filter(line -> line.trim().startsWith("rule lambda_security"))
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
            .filter(line -> line.trim().startsWith("rule lambda_security"))
            .count();

        assertTrue(ruleCount >= 12, "Should have at least 12 Lambda security rules");
    }

    @Test
    void testDeprecatedRuntimesAreDetected() throws IOException {
        String content = loadGuardFile();
        // Verify all deprecated runtimes are listed
        String[] deprecatedRuntimes = {
            "python2.7", "python3.6", "nodejs6.10", "nodejs8.10", "nodejs10.x",
            "dotnetcore2.0", "dotnetcore2.1", "ruby2.5"
        };

        for (String runtime : deprecatedRuntimes) {
            assertTrue(content.contains(runtime),
                "Should detect deprecated runtime: " + runtime);
        }
    }
}
