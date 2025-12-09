package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for IAMRules.
 *
 * Tests the IAMRules utility class structure.
 *
 * Note: Comprehensive integration tests for IAM rule installation are in the dedicated
 * IAM configuration test files (MinimalIAMConfigurationTest, StandardIAMConfigurationTest,
 * ExtendedIAMConfigurationTest) to avoid JSII kernel state conflicts where IAM role
 * constructs persist across the entire Maven Surefire test run.
 */
class IAMRulesTest {


    @Test
    void testInstallHandlesNullContextGracefully() {
        // This tests that the install method requires a non-null context
        assertThrows(NullPointerException.class, () -> new IAMRules().install(null));
    }
}
