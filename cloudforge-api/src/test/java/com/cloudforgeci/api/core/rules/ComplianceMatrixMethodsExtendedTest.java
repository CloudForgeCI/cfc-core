package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended test suite for ComplianceMatrix utility methods.
 *
 * Tests matrix report generation and framework checklist functionality.
 */
class ComplianceMatrixMethodsExtendedTest {

    @Test
    void testGenerateMatrixReportNotNull() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should not be null
        assertNotNull(report);
    }

    @Test
    void testGenerateMatrixReportNotEmpty() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should not be empty
        assertFalse(report.isEmpty());
    }

    @Test
    void testGenerateMatrixReportContainsHeaders() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should contain framework headers
        assertTrue(report.contains("PCI-DSS"));
        assertTrue(report.contains("HIPAA"));
        assertTrue(report.contains("SOC2"));
        assertTrue(report.contains("GDPR"));
        assertTrue(report.contains("NIST"));
    }

    @Test
    void testGenerateMatrixReportContainsControls() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should contain security controls
        assertTrue(report.contains("ENCRYPTION"));
        assertTrue(report.contains("ACCESS"));
        assertTrue(report.contains("LOGGING") || report.contains("AUDIT"));
    }

    @Test
    void testGenerateMatrixReportHasStructure() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should have structured format
        assertTrue(report.length() > 500, "Report should be comprehensive");
        assertTrue(report.contains("─") || report.contains("-"), "Report should have visual separators");
    }

    @Test
    void testGenerateFrameworkChecklistPciDss() {
        // When: Generating PCI-DSS checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should contain PCI-DSS requirements
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());
        assertTrue(checklist.contains("PCI-DSS") || checklist.contains("Req"));
    }

    @Test
    void testGenerateFrameworkChecklistHipaa() {
        // When: Generating HIPAA checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("HIPAA");

        // Then: Should contain HIPAA requirements
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());
        assertTrue(checklist.contains("HIPAA") || checklist.contains("§164"));
    }

    @Test
    void testGenerateFrameworkChecklistSoc2() {
        // When: Generating SOC2 checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("SOC2");

        // Then: Should contain SOC2 requirements
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());
        assertTrue(checklist.contains("SOC2") || checklist.contains("CC"));
    }

    @Test
    void testGenerateFrameworkChecklistGdpr() {
        // When: Generating GDPR checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("GDPR");

        // Then: Should contain GDPR requirements
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());
        assertTrue(checklist.contains("GDPR") || checklist.contains("Art"));
    }

    @Test
    void testGenerateFrameworkChecklistNist() {
        // When: Generating NIST checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("NIST");

        // Then: Should contain NIST requirements
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());
        assertTrue(checklist.contains("NIST") || checklist.contains("SC-") || checklist.contains("AC-"));
    }

    @Test
    void testGetRequirementsPciDss() {
        // When: Getting PCI-DSS requirements for encryption
        List<String> requirements = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirements("PCI-DSS");

        // Then: Should have requirements
        assertNotNull(requirements);
        assertFalse(requirements.isEmpty());
    }

    @Test
    void testGetRequirementsHipaa() {
        // When: Getting HIPAA requirements for encryption
        List<String> requirements = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirements("HIPAA");

        // Then: Should have requirements
        assertNotNull(requirements);
        assertFalse(requirements.isEmpty());
    }

    @Test
    void testGetRequirementsSoc2() {
        // When: Getting SOC2 requirements for access control
        List<String> requirements = ComplianceMatrix.SecurityControl.ACCESS_CONTROL
            .getRequirements("SOC2");

        // Then: Should have requirements
        assertNotNull(requirements);
        assertFalse(requirements.isEmpty());
    }

    @Test
    void testGetRequirementsGdpr() {
        // When: Getting GDPR requirements for audit logging
        List<String> requirements = ComplianceMatrix.SecurityControl.AUDIT_LOGGING
            .getRequirements("GDPR");

        // Then: Should have requirements
        assertNotNull(requirements);
        assertFalse(requirements.isEmpty());
    }

    @Test
    void testGetRequirementsNist() {
        // When: Getting NIST requirements for network segmentation
        List<String> requirements = ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION
            .getRequirements("NIST");

        // Then: Should have requirements
        assertNotNull(requirements);
        assertFalse(requirements.isEmpty());
    }

    @Test
    void testGetRequirementsUnknownFramework() {
        // When: Getting requirements for unknown framework
        List<String> requirements = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirements("UNKNOWN_FRAMEWORK");

        // Then: Should return empty list
        assertNotNull(requirements);
        assertTrue(requirements.isEmpty());
    }

    @Test
    void testAllControlsHaveGetRequirementsMethod() {
        // When: Checking all controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have getRequirements method
            List<String> pciReqs = control.getRequirements("PCI-DSS");
            assertNotNull(pciReqs, control.name() + " should return non-null for getRequirements");
        }
    }

    @Test
    void testFrameworkChecklistsAreDifferent() {
        // When: Generating checklists for different frameworks
        String pciChecklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");
        String hipaaChecklist = ComplianceMatrix.generateFrameworkChecklist("HIPAA");

        // Then: Should be different
        assertNotEquals(pciChecklist, hipaaChecklist,
            "Different frameworks should have different checklists");
    }

    @Test
    void testMatrixReportContainsTotalControls() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should contain total count
        assertTrue(report.contains("Total") || report.contains("Control"),
            "Report should contain summary information");
    }

    @Test
    void testSecurityControlGetRequirementsReturnsList() {
        // When: Getting requirements
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;
        Object result = control.getRequirements("PCI-DSS");

        // Then: Should return List
        assertTrue(result instanceof List, "getRequirements should return a List");
    }

    @Test
    void testAllControlsHaveAllFrameworkRequirements() {
        // When: Checking all controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have requirements for all frameworks
            assertFalse(control.getRequirements("PCI-DSS").isEmpty(),
                control.name() + " should have PCI-DSS requirements");
            assertFalse(control.getRequirements("HIPAA").isEmpty(),
                control.name() + " should have HIPAA requirements");
            assertFalse(control.getRequirements("SOC2").isEmpty(),
                control.name() + " should have SOC2 requirements");
            assertFalse(control.getRequirements("GDPR").isEmpty(),
                control.name() + " should have GDPR requirements");
            assertFalse(control.getRequirements("NIST").isEmpty(),
                control.name() + " should have NIST requirements");
        }
    }

    @Test
    void testFrameworkChecklistContainsMultipleRequirements() {
        // When: Generating PCI-DSS checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should contain multiple requirement sections
        assertTrue(checklist.length() > 200, "Checklist should be comprehensive");
    }

    @Test
    void testMatrixReportIsConsistent() {
        // When: Generating report multiple times
        String report1 = ComplianceMatrix.generateMatrixReport();
        String report2 = ComplianceMatrix.generateMatrixReport();

        // Then: Should be identical
        assertEquals(report1, report2, "Report generation should be deterministic");
    }

    @Test
    void testGetRequirementsIsImmutable() {
        // When: Getting requirements
        List<String> requirements = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirements("PCI-DSS");

        // Then: Should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            requirements.add("New Requirement");
        });
    }

    @Test
    void testGenerateFrameworkChecklistCaseInsensitive() {
        // When: Generating checklists with different cases
        String upperCase = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");
        String lowerCase = ComplianceMatrix.generateFrameworkChecklist("pci-dss");

        // Then: May handle case (implementation dependent)
        // Just verify both don't throw exceptions
        assertNotNull(upperCase);
        assertNotNull(lowerCase);
    }

    @Test
    void testAllFrameworkChecklistsGenerateSuccessfully() {
        // When: Generating all framework checklists
        assertDoesNotThrow(() -> {
            ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");
            ComplianceMatrix.generateFrameworkChecklist("HIPAA");
            ComplianceMatrix.generateFrameworkChecklist("SOC2");
            ComplianceMatrix.generateFrameworkChecklist("GDPR");
            ComplianceMatrix.generateFrameworkChecklist("NIST");
        });
    }

    @Test
    void testMatrixReportGeneratesSuccessfully() {
        // When/Then: Generating matrix report should not throw
        assertDoesNotThrow(() -> {
            ComplianceMatrix.generateMatrixReport();
        });
    }

    @Test
    void testSecurityControlEnumHasGetRequirementsMethod() throws NoSuchMethodException {
        // When: Checking for getRequirements method
        var method = ComplianceMatrix.SecurityControl.class.getDeclaredMethod("getRequirements", String.class);

        // Then: Should exist and be public
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }
}
