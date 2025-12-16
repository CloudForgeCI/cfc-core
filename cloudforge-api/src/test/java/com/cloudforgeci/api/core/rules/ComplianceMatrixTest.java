package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.rules.ComplianceMatrix.FrameworkRequirement;
import org.junit.jupiter.api.Test;

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
        Map<String, FrameworkRequirement> frameworks = control.getFrameworkMappings();
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

        // When: Getting PCI-DSS requirement
        FrameworkRequirement pciRequirement = control.getRequirement("PCI-DSS");

        // Then: Should map to Requirement 3.4
        assertNotNull(pciRequirement);
        assertTrue(pciRequirement.citation().contains("Req 3.4"));
    }

    @Test
    void testEncryptionAtRestHipaaMapping() {
        // Given: ENCRYPTION_AT_REST control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // When: Getting HIPAA requirement
        FrameworkRequirement hipaaRequirement = control.getRequirement("HIPAA");

        // Then: Should map to §164.312(a)(2)(iv)
        assertNotNull(hipaaRequirement);
        assertTrue(hipaaRequirement.citation().contains("§164.312(a)(2)(iv)"));
    }

    @Test
    void testEncryptionInTransitMappings() {
        // Given: ENCRYPTION_IN_TRANSIT control
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT;

        // Then: Should have mappings for all major frameworks
        Map<String, FrameworkRequirement> frameworks = control.getFrameworkMappings();
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
        FrameworkRequirement pciRequirement = control.getRequirement("PCI-DSS");
        assertNotNull(pciRequirement);
        assertTrue(pciRequirement.citation().contains("Req 1"));
    }

    @Test
    void testAccessControlMappings() {
        // Given: ACCESS_CONTROL control
        var control = ComplianceMatrix.SecurityControl.ACCESS_CONTROL;

        // Then: Should have mappings for role-based access control
        // PCI-DSS Requirement 7
        FrameworkRequirement pciRequirement = control.getRequirement("PCI-DSS");
        assertTrue(pciRequirement.citation().contains("Req 7"));

        // HIPAA Access Control
        FrameworkRequirement hipaaRequirement = control.getRequirement("HIPAA");
        assertTrue(hipaaRequirement.citation().contains("§164.312(a)(1)"));

        // SOC 2 CC6
        FrameworkRequirement soc2Requirement = control.getRequirement("SOC2");
        assertTrue(soc2Requirement.citation().contains("CC6"));
    }

    @Test
    void testAuthenticationMappings() {
        // Given: AUTHENTICATION control
        var control = ComplianceMatrix.SecurityControl.AUTHENTICATION;

        // Then: Should have MFA requirements
        // PCI-DSS Requirement 8 (MFA)
        FrameworkRequirement pciRequirement = control.getRequirement("PCI-DSS");
        assertTrue(pciRequirement.citation().contains("Req 8"));

        // NIST IA-2 (Identification and Authentication)
        FrameworkRequirement nistRequirement = control.getRequirement("NIST");
        assertTrue(nistRequirement.citation().contains("IA-2"));
    }

    @Test
    void testAuditLoggingMappings() {
        // Given: AUDIT_LOGGING control
        var control = ComplianceMatrix.SecurityControl.AUDIT_LOGGING;

        // Then: Should have comprehensive audit logging requirements
        // PCI-DSS Requirement 10
        FrameworkRequirement pciRequirement = control.getRequirement("PCI-DSS");
        assertTrue(pciRequirement.citation().contains("Req 10"));

        // HIPAA Audit Controls
        FrameworkRequirement hipaaRequirement = control.getRequirement("HIPAA");
        assertTrue(hipaaRequirement.citation().contains("§164.312(b)"));

        // SOC 2 Monitoring
        FrameworkRequirement soc2Requirement = control.getRequirement("SOC2");
        assertTrue(soc2Requirement.citation().contains("CC7.2"));
    }

    @Test
    void testLogRetentionMappings() {
        // Given: LOG_RETENTION control
        var control = ComplianceMatrix.SecurityControl.LOG_RETENTION;

        // Then: Should have log retention period requirements
        // PCI-DSS 1 year minimum
        FrameworkRequirement pciRequirement = control.getRequirement("PCI-DSS");
        assertTrue(pciRequirement.citation().contains("Req 10.7"));
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
            Map<String, FrameworkRequirement> mappings = control.getFrameworkMappings();
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
            Map<String, FrameworkRequirement> mappings = control.getFrameworkMappings();
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
            Map<String, FrameworkRequirement> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("GDPR"),
                "Control " + control.name() + " should have GDPR mappings");

            FrameworkRequirement gdprRequirement = mappings.get("GDPR");
            assertNotNull(gdprRequirement,
                "Control " + control.name() + " should have a GDPR requirement");
        }
    }

    @Test
    void testSoc2MappingsPresent() {
        // Given: All security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Should have SOC2 mappings
            Map<String, FrameworkRequirement> mappings = control.getFrameworkMappings();
            assertTrue(mappings.containsKey("SOC2"),
                "Control " + control.name() + " should have SOC2 mappings");
        }
    }

    @Test
    void testNistMappingsPresent() {
        // Given: All security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Should have NIST mappings
            Map<String, FrameworkRequirement> mappings = control.getFrameworkMappings();
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
        Map<String, FrameworkRequirement> mappings = control.getFrameworkMappings();

        // Then: Attempting to modify should throw exception or have no effect
        // (depending on Map.of() immutability)
        assertThrows(UnsupportedOperationException.class, () -> {
            mappings.put("TEST", FrameworkRequirement.required("Test requirement"));
        });
    }
}
