package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Rules - the main orchestrator class.
 *
 * Tests that the Rules utility class is properly structured.
 * Full integration tests that exercise Rules.installAll() are covered
 * in individual test files for IAMRules, RuntimeRules, TopologyRules, and SecurityRules.
 */
class RulesTest {

    @Test
    void testRulesCannotBeInstantiated() {
        // The Rules class should not be instantiable (utility class)
        try {
            var constructor = Rules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),
                "Rules should have a private constructor");
        } catch (NoSuchMethodException e) {
            fail("Rules should have a private constructor");
        }
    }
}
