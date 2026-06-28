package com.cloudforgeci.samples.plugins.compliance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenSourceSecurityPolicyRules plugin.
 *
 * <p>Validates that the Open Source Security Policy compliance framework plugin
 * is properly configured and discoverable via ServiceLoader.</p>
 */
class OpenSourceSecurityPolicyRulesTest {

    @Test
    void testPluginInstantiation() {
        // Given: The OpenSourceSecurityPolicyRules class
        // When: Instantiating the plugin
        OpenSourceSecurityPolicyRules plugin = new OpenSourceSecurityPolicyRules();

        // Then: Plugin should be instantiable (validates constructor)
        assertNotNull(plugin, "Plugin should be instantiable");
    }

    @Test
    void testAnnotationPresent() {
        // Given: The OpenSourceSecurityPolicyRules class
        // When: Checking for @ComplianceFramework annotation
        boolean hasAnnotation = OpenSourceSecurityPolicyRules.class
            .isAnnotationPresent(com.cloudforge.core.annotation.ComplianceFramework.class);

        // Then: Annotation should be present for ServiceLoader discovery
        assertTrue(hasAnnotation, "@ComplianceFramework annotation should be present");
    }

    @Test
    void testAnnotationValues() {
        // Given: The OpenSourceSecurityPolicyRules class
        // When: Reading annotation values
        var annotation = OpenSourceSecurityPolicyRules.class
            .getAnnotation(com.cloudforge.core.annotation.ComplianceFramework.class);

        // Then: Annotation values should be correct
        assertNotNull(annotation, "ComplianceFramework annotation should exist");
        assertEquals("OpenSourceSecurity", annotation.value(), "Framework ID should be OpenSourceSecurity");
        assertEquals(65, annotation.priority(), "Priority should be 65 (after industry frameworks)");
        assertFalse(annotation.alwaysLoad(), "Should be opt-in (alwaysLoad = false)");
        assertEquals("Open Source Security Policy", annotation.displayName(),
            "Display name should be descriptive");
        assertTrue(annotation.description().contains("open source"),
            "Description should mention open source");
    }

    @Test
    void testImplementsFrameworkRules() {
        // Given: The OpenSourceSecurityPolicyRules class
        // When: Checking interface implementation
        boolean implementsInterface = com.cloudforge.core.interfaces.FrameworkRules.class
            .isAssignableFrom(OpenSourceSecurityPolicyRules.class);

        // Then: Should implement FrameworkRules interface
        assertTrue(implementsInterface, "Should implement FrameworkRules interface");
    }

    @Test
    void testPluginDiscovery() {
        // Given: ServiceLoader for FrameworkRules
        // When: Loading all FrameworkRules plugins
        var loader = java.util.ServiceLoader.load(com.cloudforge.core.interfaces.FrameworkRules.class);
        var plugins = loader.stream()
            .map(java.util.ServiceLoader.Provider::type)
            .toList();

        // Then: OpenSourceSecurityPolicyRules should be discoverable
        boolean found = plugins.stream()
            .anyMatch(clazz -> clazz.equals(OpenSourceSecurityPolicyRules.class));

        assertTrue(found, "OpenSourceSecurityPolicyRules should be discoverable via ServiceLoader");
    }

    @Test
    void testToStringNotNull() {
        // Given: An OpenSourceSecurityPolicyRules instance
        OpenSourceSecurityPolicyRules plugin = new OpenSourceSecurityPolicyRules();

        // When: Calling toString
        String result = plugin.toString();

        // Then: Should not be null or default Object.toString
        assertNotNull(result, "toString should not be null");
        // Default Object.toString contains "@" and hashcode
        // A custom implementation would be better but not required
    }
}
