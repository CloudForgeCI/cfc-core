package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ComplianceMatrix.
 *
 * Tests the multi-framework compliance control mapping matrix.
 */
class ComplianceMatrixTest {

    @Test
    void testEncryptionAtRestMappings() {
        // Given: ENCRYPTION_AT_REST control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // Then: Should have mappings for all major frameworks
        Map<String, List<String>> frameworks = control.getFrameworkMappings();
        assertTrue(frameworks.containsKey("PCI-DSS"));
        assertTrue(frameworks.containsKey("HIPAA"));
        assertTrue(frameworks.containsKey("SOC2"));
        assertTrue(frameworks.containsKey("GDPR"));
        assertTrue(frameworks.containsKey("NIST"));
    }

    @Test
    void testEncryptionAtRestPciDssMapping() {
        // Given: ENCRYPTION_AT_REST control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // When: Getting PCI-DSS mappings
        List<String> pciRequirements = control.getFrameworkMappings().get("PCI-DSS");

        // Then: Should map to Requirement 3.4
        assertNotNull(pciRequirements);
        assertFalse(pciRequirements.isEmpty());
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 3.4")));
    }

    @Test
    void testEncryptionAtRestHipaaMapping() {
        // Given: ENCRYPTION_AT_REST control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // When: Getting HIPAA mappings
        List<String> hipaaRequirements = control.getFrameworkMappings().get("HIPAA");

        // Then: Should map to §164.312(a)(2)(iv)
        assertNotNull(hipaaRequirements);
        assertFalse(hipaaRequirements.isEmpty());
        assertTrue(hipaaRequirements.stream().anyMatch(req -> req.contains("§164.312(a)(2)(iv)")));
    }

    @Test
    void testEncryptionInTransitMappings() {
        // Given: ENCRYPTION_IN_TRANSIT control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT;

        // Then: Should have mappings for all major frameworks
        Map<String, List<String>> frameworks = control.getFrameworkMappings();
        assertTrue(frameworks.containsKey("PCI-DSS"));
        assertTrue(frameworks.containsKey("HIPAA"));
        assertTrue(frameworks.containsKey("SOC2"));
        assertTrue(frameworks.containsKey("GDPR"));
        assertTrue(frameworks.containsKey("NIST"));
    }

    @Test
    void testNetworkSegmentationMappings() {
        // Given: NETWORK_SEGMENTATION control
        var control = ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION;

        // Then: Should have PCI-DSS Requirement 1 mappings
        List<String> pciRequirements = control.getFrameworkMappings().get("PCI-DSS");
        assertNotNull(pciRequirements);
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 1.2.1")));
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 1.3")));
    }

    @Test
    void testAccessControlMappings() {
        // Given: ACCESS_CONTROL control
        var control = ComplianceMatrix.SecurityControl.ACCESS_CONTROL;

        // Then: Should have mappings for role-based access control
        Map<String, List<String>> frameworks = control.getFrameworkMappings();

        // PCI-DSS Requirement 7
        List<String> pciRequirements = frameworks.get("PCI-DSS");
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 7")));

        // HIPAA Access Control
        List<String> hipaaRequirements = frameworks.get("HIPAA");
        assertTrue(hipaaRequirements.stream().anyMatch(req -> req.contains("§164.312(a)(1)")));

        // SOC 2 CC6
        List<String> soc2Requirements = frameworks.get("SOC2");
        assertTrue(soc2Requirements.stream().anyMatch(req -> req.contains("CC6")));
    }

    @Test
    void testAuthenticationMappings() {
        // Given: AUTHENTICATION control
        var control = ComplianceMatrix.SecurityControl.AUTHENTICATION;

        // Then: Should have MFA requirements
        Map<String, List<String>> frameworks = control.getFrameworkMappings();

        // PCI-DSS Requirement 8 (MFA)
        List<String> pciRequirements = frameworks.get("PCI-DSS");
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 8")));

        // NIST IA-2 (Identification and Authentication)
        List<String> nistRequirements = frameworks.get("NIST");
        assertTrue(nistRequirements.stream().anyMatch(req -> req.contains("IA-2")));
    }

    @Test
    void testAuditLoggingMappings() {
        // Given: AUDIT_LOGGING control
        var control = ComplianceMatrix.SecurityControl.AUDIT_LOGGING;

        // Then: Should have comprehensive audit logging requirements
        Map<String, List<String>> frameworks = control.getFrameworkMappings();

        // PCI-DSS Requirement 10
        List<String> pciRequirements = frameworks.get("PCI-DSS");
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 10")));

        // HIPAA Audit Controls
        List<String> hipaaRequirements = frameworks.get("HIPAA");
        assertTrue(hipaaRequirements.stream().anyMatch(req -> req.contains("§164.312(b)")));

        // SOC 2 Monitoring
        List<String> soc2Requirements = frameworks.get("SOC2");
        assertTrue(soc2Requirements.stream().anyMatch(req -> req.contains("CC7.2")));
    }

    @Test
    void testLogRetentionMappings() {
        // Given: LOG_RETENTION control
        var control = ComplianceMatrix.SecurityControl.LOG_RETENTION;

        // Then: Should have log retention period requirements
        Map<String, List<String>> frameworks = control.getFrameworkMappings();

        // PCI-DSS 1 year minimum
        List<String> pciRequirements = frameworks.get("PCI-DSS");
        assertTrue(pciRequirements.stream().anyMatch(req -> req.contains("Req 10.7")));
    }

    @Test
    void testAllSecurityControlsHaveDescription() {
        // When: Iterating through all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have a non-empty description
            assertNotNull(control.getDescription());
            assertFalse(control.getDescription().isEmpty());
        }
    }

    @Test
    void testAllSecurityControlsHaveFrameworkMappings() {
        // When: Iterating through all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have framework mappings
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertNotNull(mappings);
            assertFalse(mappings.isEmpty());

            // Should have at least PCI-DSS mapping
            assertTrue(mappings.containsKey("PCI-DSS"));
        }
    }

    @Test
    void testAllSecurityControlsHaveMultipleFrameworks() {
        // When: Iterating through all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should map to multiple frameworks (cross-framework coverage)
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.size() >= 4,
                "Control " + control.name() + " should map to at least 4 frameworks");
        }
    }

    @Test
    void testSecurityControlEnumValues() {
        // When: Getting all enum values
        ComplianceMatrix.SecurityControl[] controls = ComplianceMatrix.SecurityControl.values();

        // Then: Should have expected controls
        assertTrue(controls.length >= 7, "Should have at least 7 security controls");

        // Verify key controls exist
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("ENCRYPTION_AT_REST"));
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("ENCRYPTION_IN_TRANSIT"));
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("NETWORK_SEGMENTATION"));
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("ACCESS_CONTROL"));
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("AUTHENTICATION"));
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("AUDIT_LOGGING"));
        assertNotNull(ComplianceMatrix.SecurityControl.valueOf("LOG_RETENTION"));
    }

    @Test
    void testGdprMappingsPresent() {
        // Given: All security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Should have GDPR mappings
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("GDPR"),
                "Control " + control.name() + " should have GDPR mappings");

            List<String> gdprRequirements = mappings.get("GDPR");
            assertFalse(gdprRequirements.isEmpty(),
                "Control " + control.name() + " should have at least one GDPR requirement");
        }
    }

    @Test
    void testSoc2MappingsPresent() {
        // Given: All security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Should have SOC2 mappings
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("SOC2"),
                "Control " + control.name() + " should have SOC2 mappings");
        }
    }

    @Test
    void testNistMappingsPresent() {
        // Given: All security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Should have NIST mappings
            Map<String, List<String>> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("NIST"),
                "Control " + control.name() + " should have NIST SP 800-53 mappings");
        }
    }

    @Test
    void testComplianceMatrixCannotBeInstantiated() {
        // The ComplianceMatrix class should not be instantiable (utility class)
        // This is enforced by the private constructor
        // We can only verify that the class has a private constructor

        try {
            var constructor = ComplianceMatrix.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),
                "ComplianceMatrix should have a private constructor");
        } catch (NoSuchMethodException e) {
            fail("ComplianceMatrix should have a no-args constructor");
        }
    }

    @Test
    void testFrameworkMappingsAreImmutable() {
        // Given: A security control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // When: Getting framework mappings
        Map<String, List<String>> mappings = control.getFrameworkMappings();

        // Then: Attempting to modify should throw exception or have no effect
        // (depending on Map.of() immutability)
        assertThrows(UnsupportedOperationException.class, () -> {
            mappings.put("TEST", List.of("Test requirement"));
        });
    }
}
