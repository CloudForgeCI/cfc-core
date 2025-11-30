package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FrameworkLoader plugin discovery system.
 *
 * <p>Tests the automatic discovery and loading of compliance frameworks
 * via the v3.1.0 plugin architecture.</p>
 */
class FrameworkLoaderTest {

    @Test
    void testDiscoverBuiltInFrameworks() {
        // When: Discovering all frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Should discover all built-in frameworks
        assertNotNull(frameworks, "Frameworks list should not be null");
        assertTrue(frameworks.size() >= 11,
            "Should discover at least 11 built-in frameworks, found: " + frameworks.size());
    }

    @Test
    void testFrameworksOrderedByPriority() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Should be ordered by priority (lowest first)
        for (int i = 0; i < frameworks.size() - 1; i++) {
            int currentPriority = frameworks.get(i).priority();
            int nextPriority = frameworks.get(i + 1).priority();

            assertTrue(currentPriority <= nextPriority,
                String.format("Frameworks should be ordered by priority: %s (priority=%d) should come before or equal to %s (priority=%d)",
                    frameworks.get(i).frameworkId(), currentPriority,
                    frameworks.get(i + 1).frameworkId(), nextPriority));
        }
    }

    @Test
    void testCrossFrameworkRulesHaveNegativePriority() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Cross-framework rules should have negative priority
        List<String> crossFrameworkIds = List.of(
            "KEYMANAGEMENT", "KeyManagement",
            "DATABASESECURITY", "DatabaseSecurity",
            "ADVANCEDMONITORING", "AdvancedMonitoring"
        );

        for (FrameworkRules<SystemContext> framework : frameworks) {
            String id = framework.frameworkId().toUpperCase().replace("-", "");
            if (crossFrameworkIds.stream().anyMatch(cfId -> id.contains(cfId.toUpperCase()))) {
                assertTrue(framework.priority() < 0,
                    "Cross-framework rule " + framework.frameworkId() +
                    " should have negative priority, but has: " + framework.priority());
            }
        }
    }

    @Test
    void testAlwaysLoadFrameworks() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Cross-framework validators should be marked as alwaysLoad
        long alwaysLoadCount = frameworks.stream()
            .filter(FrameworkRules::alwaysLoad)
            .count();

        assertTrue(alwaysLoadCount >= 5,
            "Should have at least 5 always-load frameworks (cross-framework rules), found: " + alwaysLoadCount);
    }

    @Test
    void testFrameworksHaveValidMetadata() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: All frameworks should have valid metadata
        for (FrameworkRules<SystemContext> framework : frameworks) {
            assertNotNull(framework.frameworkId(),
                "Framework ID should not be null");
            assertFalse(framework.frameworkId().trim().isEmpty(),
                "Framework ID should not be empty");

            assertNotNull(framework.displayName(),
                "Display name should not be null");
            assertFalse(framework.displayName().trim().isEmpty(),
                "Display name should not be empty");

            assertTrue(framework.priority() >= -100 && framework.priority() <= 1000,
                "Priority should be in reasonable range: " + framework.priority());
        }
    }

    @Test
    void testCoreFrameworksHaveExpectedPriorities() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Core compliance frameworks should have v2.0 priorities
        for (FrameworkRules<SystemContext> framework : frameworks) {
            String id = framework.frameworkId().toUpperCase();

            if (id.equals("HIPAA")) {
                assertEquals(10, framework.priority(),
                    "HIPAA should have priority 10");
            } else if (id.equals("HIPAA-ORGANIZATIONAL")) {
                assertEquals(15, framework.priority(),
                    "HIPAA-Organizational should have priority 15");
            } else if (id.equals("PCI-DSS")) {
                assertEquals(20, framework.priority(),
                    "PCI-DSS should have priority 20");
            } else if (id.equals("SOC2")) {
                assertEquals(40, framework.priority(),
                    "SOC2 should have priority 40");
            } else if (id.equals("GDPR")) {
                assertEquals(30, framework.priority(),
                    "GDPR should have priority 30");
            } else if (id.equals("GDPR-ORGANIZATIONAL")) {
                assertEquals(35, framework.priority(),
                    "GDPR-Organizational should have priority 35");
            }
        }
    }

    @Test
    void testNoDuplicateFrameworkIds() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Should have no duplicate framework IDs
        List<String> frameworkIds = frameworks.stream()
            .map(f -> f.frameworkId().toUpperCase())
            .toList();

        long uniqueCount = frameworkIds.stream().distinct().count();

        assertEquals(uniqueCount, frameworkIds.size(),
            "Framework IDs should be unique. Found duplicates in: " + frameworkIds);
    }

    @Test
    void testFrameworksCanBeInstantiated() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: All frameworks should be instantiable (not throw on discovery)
        assertTrue(frameworks.size() > 0,
            "Should discover at least one framework");

        // Verify none are null
        assertFalse(frameworks.contains(null),
            "Framework list should not contain null elements");
    }

    @Test
    void testBuiltInFrameworksIncludeCoreSet() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();
        List<String> frameworkIds = frameworks.stream()
            .map(f -> f.frameworkId().toUpperCase())
            .toList();

        // Then: Should include core built-in frameworks
        List<String> expectedCore = List.of(
            "HIPAA", "PCI-DSS", "SOC2", "GDPR"
        );

        for (String expected : expectedCore) {
            assertTrue(frameworkIds.contains(expected),
                "Should discover core framework: " + expected + ". Found: " + frameworkIds);
        }
    }

    @Test
    void testConditionalVsAlwaysLoadSeparation() {
        // When: Discovering frameworks
        List<FrameworkRules<SystemContext>> frameworks = FrameworkLoader.discover();

        // Then: Should have both conditional and always-load frameworks
        long conditionalCount = frameworks.stream()
            .filter(f -> !f.alwaysLoad())
            .count();

        long alwaysLoadCount = frameworks.stream()
            .filter(FrameworkRules::alwaysLoad)
            .count();

        assertTrue(conditionalCount > 0,
            "Should have at least one conditional framework");
        assertTrue(alwaysLoadCount > 0,
            "Should have at least one always-load framework");
    }
}
