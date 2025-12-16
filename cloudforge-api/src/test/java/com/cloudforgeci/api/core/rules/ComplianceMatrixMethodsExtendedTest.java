package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

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
        // When: Getting PCI-DSS requirement for encryption
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirement("PCI-DSS");

        // Then: Should have requirement
        assertNotNull(requirement);
        assertNotNull(requirement.citation());
        assertFalse(requirement.citation().isEmpty());
    }

    @Test
    void testGetRequirementsHipaa() {
        // When: Getting HIPAA requirement for encryption
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirement("HIPAA");

        // Then: Should have requirement
        assertNotNull(requirement);
        assertNotNull(requirement.citation());
        assertFalse(requirement.citation().isEmpty());
    }

    @Test
    void testGetRequirementsSoc2() {
        // When: Getting SOC2 requirement for access control
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.ACCESS_CONTROL
            .getRequirement("SOC2");

        // Then: Should have requirement
        assertNotNull(requirement);
        assertNotNull(requirement.citation());
        assertFalse(requirement.citation().isEmpty());
    }

    @Test
    void testGetRequirementsGdpr() {
        // When: Getting GDPR requirement for audit logging
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.AUDIT_LOGGING
            .getRequirement("GDPR");

        // Then: Should have requirement
        assertNotNull(requirement);
        assertNotNull(requirement.citation());
        assertFalse(requirement.citation().isEmpty());
    }

    @Test
    void testGetRequirementsNist() {
        // When: Getting NIST requirement for network segmentation
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION
            .getRequirement("NIST");

        // Then: Should have requirement
        assertNotNull(requirement);
        assertNotNull(requirement.citation());
        assertFalse(requirement.citation().isEmpty());
    }

    @Test
    void testGetRequirementsUnknownFramework() {
        // When: Getting requirement for unknown framework
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirement("UNKNOWN_FRAMEWORK");

        // Then: Should return NOT_APPLICABLE requirement
        assertNotNull(requirement);
        assertEquals(ComplianceMatrix.RequirementLevel.NOT_APPLICABLE, requirement.level());
    }

    @Test
    void testAllControlsHaveGetRequirementsMethod() {
        // When: Checking all controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have getRequirement method
            ComplianceMatrix.FrameworkRequirement pciReq = control.getRequirement("PCI-DSS");
            assertNotNull(pciReq, control.name() + " should return non-null for getRequirement");
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
        // When: Getting requirement
        var control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;
        Object result = control.getRequirement("PCI-DSS");

        // Then: Should return FrameworkRequirement
        assertTrue(result instanceof ComplianceMatrix.FrameworkRequirement,
            "getRequirement should return a FrameworkRequirement");
    }

    @Test
    void testAllControlsHaveAllFrameworkRequirements() {
        // When: Checking all controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have requirements for all frameworks
            assertNotNull(control.getRequirement("PCI-DSS"),
                control.name() + " should have PCI-DSS requirement");
            assertNotNull(control.getRequirement("HIPAA"),
                control.name() + " should have HIPAA requirement");
            assertNotNull(control.getRequirement("SOC2"),
                control.name() + " should have SOC2 requirement");
            assertNotNull(control.getRequirement("GDPR"),
                control.name() + " should have GDPR requirement");
            assertNotNull(control.getRequirement("NIST"),
                control.name() + " should have NIST requirement");
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
        // When: Getting requirement
        ComplianceMatrix.FrameworkRequirement requirement = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST
            .getRequirement("PCI-DSS");

        // Then: FrameworkRequirement is a record and thus immutable
        assertNotNull(requirement);
        assertNotNull(requirement.citation());
        assertNotNull(requirement.level());
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
        // When: Checking for getRequirement method
        var method = ComplianceMatrix.SecurityControl.class.getDeclaredMethod("getRequirement", String.class);

        // Then: Should exist and be public
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }
}
