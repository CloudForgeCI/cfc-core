package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ComplianceMatrix.SecurityControl enum.
 *
 * Tests the multi-framework compliance control mapping matrix.
 */
class ComplianceMatrixSecurityControlTest {

    @Test
    void testSecurityControlEnumExists() {
        // When: Accessing SecurityControl enum
        Class<?> enumClass = ComplianceMatrix.SecurityControl.class;

        // Then: Should exist and be an enum
        assertNotNull(enumClass);
        assertTrue(enumClass.isEnum());
    }

    @Test
    void testSecurityControlIsPublic() {
        // When: Checking SecurityControl modifiers
        Class<?> enumClass = ComplianceMatrix.SecurityControl.class;

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(enumClass.getModifiers()));
    }

    @Test
    void testSecurityControlIsStatic() {
        // When: Checking SecurityControl modifiers
        Class<?> enumClass = ComplianceMatrix.SecurityControl.class;

        // Then: Should be static (nested class)
        assertTrue(java.lang.reflect.Modifier.isStatic(enumClass.getModifiers()));
    }

    @Test
    void testEncryptionAtRestExists() {
        // When: Getting ENCRYPTION_AT_REST
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("ENCRYPTION_AT_REST", control.name());
    }

    @Test
    void testEncryptionInTransitExists() {
        // When: Getting ENCRYPTION_IN_TRANSIT
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("ENCRYPTION_IN_TRANSIT", control.name());
    }

    @Test
    void testNetworkSegmentationExists() {
        // When: Getting NETWORK_SEGMENTATION
        var control = ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("NETWORK_SEGMENTATION", control.name());
    }

    @Test
    void testAccessControlExists() {
        // When: Getting ACCESS_CONTROL
        var control = ComplianceMatrix.SecurityControl.ACCESS_CONTROL;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("ACCESS_CONTROL", control.name());
    }

    @Test
    void testAuthenticationExists() {
        // When: Getting AUTHENTICATION
        var control = ComplianceMatrix.SecurityControl.AUTHENTICATION;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("AUTHENTICATION", control.name());
    }

    @Test
    void testAuditLoggingExists() {
        // When: Getting AUDIT_LOGGING
        var control = ComplianceMatrix.SecurityControl.AUDIT_LOGGING;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("AUDIT_LOGGING", control.name());
    }

    @Test
    void testLogRetentionExists() {
        // When: Getting LOG_RETENTION
        var control = ComplianceMatrix.SecurityControl.LOG_RETENTION;

        // Then: Should exist
        assertNotNull(control);
        assertEquals("LOG_RETENTION", control.name());
    }

    @Test
    void testAllSecurityControlsHaveDescription() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have a description
            String description = control.getDescription();
            assertNotNull(description, control.name() + " should have a description");
            assertFalse(description.isEmpty(), control.name() + " description should not be empty");
        }
    }

    @Test
    void testAllSecurityControlsHaveFrameworkMappings() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have framework mappings
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertNotNull(mappings, control.name() + " should have framework mappings");
            assertFalse(mappings.isEmpty(), control.name() + " should have at least one framework mapping");
        }
    }

    @Test
    void testAllSecurityControlsMapToPciDss() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have PCI-DSS mapping
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("PCI-DSS"),
                control.name() + " should have PCI-DSS mapping");
            assertFalse(mappings.get("PCI-DSS").isEmpty(),
                control.name() + " PCI-DSS mapping should not be empty");
        }
    }

    @Test
    void testAllSecurityControlsMapToHipaa() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have HIPAA mapping
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("HIPAA"),
                control.name() + " should have HIPAA mapping");
            assertFalse(mappings.get("HIPAA").isEmpty(),
                control.name() + " HIPAA mapping should not be empty");
        }
    }

    @Test
    void testAllSecurityControlsMapToSoc2() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have SOC2 mapping
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("SOC2"),
                control.name() + " should have SOC2 mapping");
            assertFalse(mappings.get("SOC2").isEmpty(),
                control.name() + " SOC2 mapping should not be empty");
        }
    }

    @Test
    void testAllSecurityControlsMapToGdpr() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have GDPR mapping
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("GDPR"),
                control.name() + " should have GDPR mapping");
            assertFalse(mappings.get("GDPR").isEmpty(),
                control.name() + " GDPR mapping should not be empty");
        }
    }

    @Test
    void testAllSecurityControlsMapToNist() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have NIST mapping
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("NIST"),
                control.name() + " should have NIST mapping");
            assertFalse(mappings.get("NIST").isEmpty(),
                control.name() + " NIST mapping should not be empty");
        }
    }

    @Test
    void testSecurityControlValuesNotEmpty() {
        // When: Getting all values
        var values = ComplianceMatrix.SecurityControl.values();

        // Then: Should have security controls
        assertNotNull(values);
        assertTrue(values.length >= 5, "Should have at least 5 security controls");
    }

    @Test
    void testSecurityControlValueOf() {
        // When: Getting control by name
        var control = ComplianceMatrix.SecurityControl.valueOf("ENCRYPTION_AT_REST");

        // Then: Should return correct control
        assertNotNull(control);
        assertEquals(ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST, control);
    }

    @Test
    void testEncryptionAtRestHasPciDssMapping() {
        // When: Getting ENCRYPTION_AT_REST PCI-DSS mapping
        var mappings = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST.getFrameworkMappings();

        // Then: Should have Req 3.4
        assertTrue(mappings.get("PCI-DSS").stream()
            .anyMatch(req -> req.contains("Req 3.4")));
    }

    @Test
    void testEncryptionAtRestHasHipaaMapping() {
        // When: Getting ENCRYPTION_AT_REST HIPAA mapping
        var mappings = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST.getFrameworkMappings();

        // Then: Should have §164.312 reference
        assertTrue(mappings.get("HIPAA").stream()
            .anyMatch(req -> req.contains("§164.312")));
    }

    @Test
    void testAccessControlHasMultipleRequirements() {
        // When: Getting ACCESS_CONTROL mappings
        var mappings = ComplianceMatrix.SecurityControl.ACCESS_CONTROL.getFrameworkMappings();

        // Then: Should have multiple PCI-DSS requirements
        List<String> pciDss = mappings.get("PCI-DSS");
        assertTrue(pciDss.size() >= 2, "Access control should have multiple PCI-DSS requirements");
    }

    @Test
    void testAuditLoggingHasComprehensiveMappings() {
        // When: Getting AUDIT_LOGGING mappings
        var mappings = ComplianceMatrix.SecurityControl.AUDIT_LOGGING.getFrameworkMappings();

        // Then: Should have comprehensive PCI-DSS requirements (Req 10.x)
        List<String> pciDss = mappings.get("PCI-DSS");
        assertTrue(pciDss.stream().anyMatch(req -> req.contains("Req 10")),
            "Audit logging should have Req 10 requirements");
    }

    @Test
    void testFrameworkMappingsAreImmutable() {
        // When: Getting framework mappings
        var mappings = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST.getFrameworkMappings();

        // Then: Should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            mappings.put("NEW_FRAMEWORK", List.of("Test"));
        });
    }

    @Test
    void testFrameworkRequirementListsAreImmutable() {
        // When: Getting PCI-DSS requirements
        var mappings = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST.getFrameworkMappings();
        List<String> pciDss = mappings.get("PCI-DSS");

        // Then: Should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            pciDss.add("New Requirement");
        });
    }

    @Test
    void testSecurityControlEnumCount() {
        // When: Getting all security controls
        var controls = ComplianceMatrix.SecurityControl.values();

        // Then: Should have expected number of controls
        assertTrue(controls.length >= 7, "Should have at least 7 security controls");
        assertTrue(controls.length < 50, "Should not have excessive controls");
    }

    @Test
    void testSecurityControlsHaveUniqueNames() {
        // When: Getting all security controls
        var controls = ComplianceMatrix.SecurityControl.values();

        // Then: All names should be unique
        long uniqueNames = java.util.Arrays.stream(controls)
            .map(Enum::name)
            .distinct()
            .count();

        assertEquals(controls.length, uniqueNames, "All security control names should be unique");
    }

    @Test
    void testSecurityControlToString() {
        // When: Getting string representation
        String toString = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST.toString();

        // Then: Should contain control name
        assertNotNull(toString);
        assertTrue(toString.contains("ENCRYPTION_AT_REST"));
    }

    @Test
    void testSecurityControlOrdinal() {
        // When: Getting ordinal
        int ordinal = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST.ordinal();

        // Then: Should be >= 0
        assertTrue(ordinal >= 0);
    }

    @Test
    void testSecurityControlGetFrameworkRequirement() {
        // When: Getting specific framework requirement
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;
        List<String> pciDssReqs = control.getFrameworkMappings().get("PCI-DSS");

        // Then: Should have specific requirements
        assertNotNull(pciDssReqs);
        assertFalse(pciDssReqs.isEmpty());
    }

    @Test
    void testAllSecurityControlDescriptionsAreDescriptive() {
        // When: Getting all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Description should be descriptive (> 10 chars)
            String description = control.getDescription();
            assertTrue(description.length() > 10,
                control.name() + " description should be descriptive");
        }
    }
}
