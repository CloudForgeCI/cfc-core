package com.cloudforge.core.interfaces;

import com.cloudforge.core.annotation.ComplianceFramework;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FrameworkRules interface default methods.
 */
class FrameworkRulesTest {

    // Test implementation with full annotation
    @ComplianceFramework(
        value = "TEST-FRAMEWORK",
        priority = 50,
        alwaysLoad = true,
        displayName = "Test Compliance Framework",
        description = "A test framework for unit testing"
    )
    static class TestFramework implements FrameworkRules<Object> {
        @Override
        public void install(Object ctx) {
            // Test implementation
        }
    }

    // Test implementation with minimal annotation
    @ComplianceFramework(value = "MINIMAL")
    static class MinimalFramework implements FrameworkRules<Object> {
        @Override
        public void install(Object ctx) {
            // Minimal implementation
        }
    }

    // Test implementation without annotation (should fail)
    static class NoAnnotationFramework implements FrameworkRules<Object> {
        @Override
        public void install(Object ctx) {
            // No annotation
        }
    }

    @Test
    void testFrameworkId() {
        TestFramework framework = new TestFramework();
        assertEquals("TEST-FRAMEWORK", framework.frameworkId());
    }

    @Test
    void testFrameworkIdMinimal() {
        MinimalFramework framework = new MinimalFramework();
        assertEquals("MINIMAL", framework.frameworkId());
    }

    @Test
    void testFrameworkIdWithoutAnnotation() {
        NoAnnotationFramework framework = new NoAnnotationFramework();
        assertThrows(IllegalStateException.class, framework::frameworkId);
    }

    @Test
    void testDisplayName() {
        TestFramework framework = new TestFramework();
        assertEquals("Test Compliance Framework", framework.displayName());
    }

    @Test
    void testDisplayNameDefaultsToValue() {
        MinimalFramework framework = new MinimalFramework();
        assertEquals("MINIMAL", framework.displayName());
    }

    @Test
    void testDisplayNameWithoutAnnotation() {
        NoAnnotationFramework framework = new NoAnnotationFramework();
        assertEquals("NoAnnotationFramework", framework.displayName());
    }

    @Test
    void testDescription() {
        TestFramework framework = new TestFramework();
        assertEquals("A test framework for unit testing", framework.description());
    }

    @Test
    void testDescriptionEmpty() {
        MinimalFramework framework = new MinimalFramework();
        assertEquals("", framework.description());
    }

    @Test
    void testDescriptionWithoutAnnotation() {
        NoAnnotationFramework framework = new NoAnnotationFramework();
        assertEquals("", framework.description());
    }

    @Test
    void testPriority() {
        TestFramework framework = new TestFramework();
        assertEquals(50, framework.priority());
    }

    @Test
    void testPriorityDefault() {
        MinimalFramework framework = new MinimalFramework();
        assertEquals(100, framework.priority()); // Default from annotation
    }

    @Test
    void testPriorityWithoutAnnotation() {
        NoAnnotationFramework framework = new NoAnnotationFramework();
        assertEquals(100, framework.priority()); // Default from interface
    }

    @Test
    void testAlwaysLoad() {
        TestFramework framework = new TestFramework();
        assertTrue(framework.alwaysLoad());
    }

    @Test
    void testAlwaysLoadDefault() {
        MinimalFramework framework = new MinimalFramework();
        assertFalse(framework.alwaysLoad()); // Default from annotation
    }

    @Test
    void testAlwaysLoadWithoutAnnotation() {
        NoAnnotationFramework framework = new NoAnnotationFramework();
        assertFalse(framework.alwaysLoad()); // Default from interface
    }

    @Test
    void testMultipleFrameworksIndependent() {
        TestFramework test = new TestFramework();
        MinimalFramework minimal = new MinimalFramework();

        // Verify they have different values
        assertNotEquals(test.frameworkId(), minimal.frameworkId());
        assertNotEquals(test.priority(), minimal.priority());
        assertNotEquals(test.alwaysLoad(), minimal.alwaysLoad());
    }

    @Test
    void testAnnotationPersistence() {
        // Verify annotation is retained at runtime
        ComplianceFramework annotation = TestFramework.class.getAnnotation(ComplianceFramework.class);
        assertNotNull(annotation);
        assertEquals("TEST-FRAMEWORK", annotation.value());
        assertEquals(50, annotation.priority());
        assertTrue(annotation.alwaysLoad());
        assertEquals("Test Compliance Framework", annotation.displayName());
        assertEquals("A test framework for unit testing", annotation.description());
    }
}
