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
    void testIAMRulesCannotBeInstantiated() {
        // The IAMRules class should not be instantiable (utility class)
        try {
            var constructor = IAMRules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),
                "IAMRules should have a private constructor");
        } catch (NoSuchMethodException e) {
            fail("IAMRules should have a private constructor");
        }
    }

    @Test
    void testInstallHandlesNullContextGracefully() {
        // This tests that the install method requires a non-null context
        assertThrows(NullPointerException.class, () -> IAMRules.install(null));
    }
}
