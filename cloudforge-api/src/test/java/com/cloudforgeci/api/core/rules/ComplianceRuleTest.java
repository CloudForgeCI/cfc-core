package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ComplianceRule record.
 *
 * Tests the compliance rule data structure and its factory methods.
 */
class ComplianceRuleTest {

    @Test
    void testPassingRuleWithConfigRuleId() {
        // When: Creating a passing rule with Config rule ID
        ComplianceRule rule = ComplianceRule.pass("PCI-DSS-Req3.4",
            "Encryption at rest required",
            "encrypted-volumes");

        // Then: Rule should be marked as passed
        assertTrue(rule.passed());
        assertEquals("PCI-DSS-Req3.4", rule.ruleId());
        assertEquals("Encryption at rest required", rule.description());
        assertEquals(Optional.of("encrypted-volumes"), rule.configRuleId());
        assertEquals(Optional.empty(), rule.errorMessage());
    }

    @Test
    void testPassingRuleWithoutConfigRuleId() {
        // When: Creating a passing rule without Config rule ID
        ComplianceRule rule = ComplianceRule.pass("SOC2-CC6.1",
            "Logical access controls implemented");

        // Then: Rule should be marked as passed without Config rule
        assertTrue(rule.passed());
        assertEquals("SOC2-CC6.1", rule.ruleId());
        assertEquals("Logical access controls implemented", rule.description());
        assertEquals(Optional.empty(), rule.configRuleId());
        assertEquals(Optional.empty(), rule.errorMessage());
    }

    @Test
    void testFailingRuleWithErrorMessage() {
        // When: Creating a failing rule with error message
        ComplianceRule rule = ComplianceRule.fail("HIPAA-164.312(a)(2)(iv)",
            "Encryption required",
            "EBS volumes must be encrypted");

        // Then: Rule should be marked as failed with error
        assertFalse(rule.passed());
        assertEquals("HIPAA-164.312(a)(2)(iv)", rule.ruleId());
        assertEquals("Encryption required", rule.description());
        assertEquals(Optional.empty(), rule.configRuleId());
        assertEquals(Optional.of("EBS volumes must be encrypted"), rule.errorMessage());
    }

    @Test
    void testFailingRuleWithConfigRuleIdAndError() {
        // When: Creating a failing rule with both Config rule and error
        ComplianceRule rule = ComplianceRule.fail("GDPR-Art32",
            "Data protection by design",
            "security-group-ingress-check",
            "Security groups allow unrestricted access");

        // Then: Rule should have all failure details
        assertFalse(rule.passed());
        assertEquals("GDPR-Art32", rule.ruleId());
        assertEquals("Data protection by design", rule.description());
        assertEquals(Optional.of("security-group-ingress-check"), rule.configRuleId());
        assertEquals(Optional.of("Security groups allow unrestricted access"), rule.errorMessage());
    }

    @Test
    void testToErrorStringForPassingRule() {
        // Given: A passing rule
        ComplianceRule rule = ComplianceRule.pass("PCI-DSS-Req8.1", "User identification");

        // When: Converting to error string
        Optional<String> errorString = rule.toErrorString();

        // Then: Should return empty for passing rule
        assertEquals(Optional.empty(), errorString);
    }

    @Test
    void testToErrorStringForFailingRuleWithoutConfigRule() {
        // Given: A failing rule without Config rule
        ComplianceRule rule = ComplianceRule.fail("SOC2-CC7.2",
            "Security monitoring",
            "CloudTrail not enabled");

        // When: Converting to error string
        Optional<String> errorString = rule.toErrorString();

        // Then: Should format error with rule ID, description, and message
        assertTrue(errorString.isPresent());
        assertEquals("SOC2-CC7.2: Security monitoring - CloudTrail not enabled",
            errorString.get());
    }

    @Test
    void testToErrorStringForFailingRuleWithConfigRule() {
        // Given: A failing rule with Config rule
        ComplianceRule rule = ComplianceRule.fail("PCI-DSS-Req2.2",
            "Secure system configuration",
            "vpc-flow-logs-enabled",
            "VPC Flow Logs not configured");

        // When: Converting to error string
        Optional<String> errorString = rule.toErrorString();

        // Then: Should include Config rule ID in parentheses
        assertTrue(errorString.isPresent());
        String expected = "PCI-DSS-Req2.2: Secure system configuration - VPC Flow Logs not configured (Config Rule: vpc-flow-logs-enabled)";
        assertEquals(expected, errorString.get());
    }

    @Test
    void testRecordEquality() {
        // Given: Two identical rules
        ComplianceRule rule1 = ComplianceRule.pass("TEST-1", "Test rule", "config-rule-1");
        ComplianceRule rule2 = ComplianceRule.pass("TEST-1", "Test rule", "config-rule-1");

        // Then: Should be equal
        assertEquals(rule1, rule2);
        assertEquals(rule1.hashCode(), rule2.hashCode());
    }

    @Test
    void testRecordInequality() {
        // Given: Two different rules
        ComplianceRule rule1 = ComplianceRule.pass("TEST-1", "Test rule 1");
        ComplianceRule rule2 = ComplianceRule.pass("TEST-2", "Test rule 2");

        // Then: Should not be equal
        assertNotEquals(rule1, rule2);
    }

    @Test
    void testNullConfigRuleId() {
        // When: Creating rule with null Config rule ID
        ComplianceRule rule = ComplianceRule.pass("TEST-NULL", "Test", null);

        // Then: Should store as Optional.empty()
        assertEquals(Optional.empty(), rule.configRuleId());
    }

    @Test
    void testRecordGetters() {
        // Given: A compliance rule
        ComplianceRule rule = new ComplianceRule(
            "TEST-123",
            "Test description",
            Optional.of("test-config-rule"),
            true,
            Optional.empty()
        );

        // Then: All getters should work correctly
        assertEquals("TEST-123", rule.ruleId());
        assertEquals("Test description", rule.description());
        assertEquals(Optional.of("test-config-rule"), rule.configRuleId());
        assertTrue(rule.passed());
        assertEquals(Optional.empty(), rule.errorMessage());
    }

    @Test
    void testMultipleFailureScenarios() {
        // Test various failure combinations
        ComplianceRule rule1 = ComplianceRule.fail("TEST-1", "Desc 1", "Error 1");
        ComplianceRule rule2 = ComplianceRule.fail("TEST-2", "Desc 2", "config-2", "Error 2");
        ComplianceRule rule3 = ComplianceRule.fail("TEST-3", "Desc 3", null, "Error 3");

        // Verify all are marked as failed
        assertFalse(rule1.passed());
        assertFalse(rule2.passed());
        assertFalse(rule3.passed());

        // Verify error strings are formatted correctly
        assertTrue(rule1.toErrorString().isPresent());
        assertTrue(rule2.toErrorString().isPresent());
        assertTrue(rule3.toErrorString().isPresent());
    }

    @Test
    void testPassingRuleVariants() {
        // Test both passing rule factory methods
        ComplianceRule rule1 = ComplianceRule.pass("PASS-1", "Description");
        ComplianceRule rule2 = ComplianceRule.pass("PASS-2", "Description", "config-rule");

        // Both should be passing
        assertTrue(rule1.passed());
        assertTrue(rule2.passed());

        // Only rule2 should have Config rule ID
        assertEquals(Optional.empty(), rule1.configRuleId());
        assertEquals(Optional.of("config-rule"), rule2.configRuleId());
    }

    @Test
    void testToStringMethod() {
        // Given: A compliance rule
        ComplianceRule rule = ComplianceRule.pass("TEST-TO-STRING", "Test", "config-test");

        // When: Calling toString()
        String str = rule.toString();

        // Then: Should contain all relevant information
        assertNotNull(str);
        assertTrue(str.contains("TEST-TO-STRING"));
        assertTrue(str.contains("Test"));
        assertTrue(str.contains("config-test"));
        assertTrue(str.contains("true")); // passed=true
    }
}
