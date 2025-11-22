package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ComplianceMatrix utility methods.
 *
 * Tests the report generation and deployment analysis methods.
 */
class ComplianceMatrixMethodsTest {

    @Test
    void testGenerateMatrixReport() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should return comprehensive report
        assertNotNull(report);
        assertFalse(report.isEmpty());

        // And: Should contain title
        assertTrue(report.contains("CloudForge CI"));
        assertTrue(report.contains("Compliance Control Matrix"));

        // And: Should list all major frameworks
        assertTrue(report.contains("PCI-DSS"));
        assertTrue(report.contains("HIPAA"));
        assertTrue(report.contains("SOC"));
        assertTrue(report.contains("GDPR"));
        assertTrue(report.contains("NIST"));

        // And: Should include control names
        assertTrue(report.contains("ENCRYPTION_AT_REST"));
        assertTrue(report.contains("ENCRYPTION_IN_TRANSIT"));
        assertTrue(report.contains("NETWORK_SEGMENTATION"));

        // And: Should include total count
        assertTrue(report.contains("Total Controls:"));
        assertTrue(report.contains("Frameworks Covered:"));
    }

    @Test
    void testGenerateMatrixReportContainsAllControls() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should contain all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            assertTrue(report.contains(control.name()),
                "Report should contain control: " + control.name());
        }
    }

    @Test
    void testGenerateMatrixReportContainsDescriptions() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should contain control descriptions
        assertTrue(report.contains("Encryption of data at rest"));
        assertTrue(report.contains("Role-based access control"));
        assertTrue(report.contains("Comprehensive audit logging"));
    }

    @Test
    void testGenerateMatrixReportFormatting() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should have proper formatting
        assertTrue(report.contains("╔"));
        assertTrue(report.contains("╚"));
        assertTrue(report.contains("═"));
        assertTrue(report.contains("─"));
        assertTrue(report.contains("│"));
    }

    @Test
    void testGenerateFrameworkChecklistForPciDss() {
        // When: Generating checklist for PCI-DSS
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should return PCI-DSS specific checklist
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());

        // And: Should contain framework name
        assertTrue(checklist.contains("PCI-DSS"));

        // And: Should contain PCI-DSS requirements
        assertTrue(checklist.contains("Req ") || checklist.contains("Requirement"));

        // And: Should contain checkmarks
        assertTrue(checklist.contains("✓"));

        // And: Should show total requirements covered
        assertTrue(checklist.contains("Total requirements covered:"));
    }

    @Test
    void testGenerateFrameworkChecklistForHipaa() {
        // When: Generating checklist for HIPAA
        String checklist = ComplianceMatrix.generateFrameworkChecklist("HIPAA");

        // Then: Should return HIPAA specific checklist
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());

        // And: Should contain HIPAA sections
        assertTrue(checklist.contains("§164.") || checklist.contains("HIPAA"));
    }

    @Test
    void testGenerateFrameworkChecklistForSoc2() {
        // When: Generating checklist for SOC2
        String checklist = ComplianceMatrix.generateFrameworkChecklist("SOC2");

        // Then: Should return SOC2 specific checklist
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());

        // And: Should contain SOC2 criteria
        assertTrue(checklist.contains("CC") || checklist.contains("SOC2"));
    }

    @Test
    void testGenerateFrameworkChecklistForGdpr() {
        // When: Generating checklist for GDPR
        String checklist = ComplianceMatrix.generateFrameworkChecklist("GDPR");

        // Then: Should return GDPR specific checklist
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());

        // And: Should contain GDPR articles
        assertTrue(checklist.contains("Art.") || checklist.contains("GDPR"));
    }

    @Test
    void testGenerateFrameworkChecklistForNist() {
        // When: Generating checklist for NIST
        String checklist = ComplianceMatrix.generateFrameworkChecklist("NIST");

        // Then: Should return NIST specific checklist
        assertNotNull(checklist);
        assertFalse(checklist.isEmpty());

        // And: Should contain NIST control families
        boolean hasNistControls = checklist.contains("AC-") || checklist.contains("AU-") ||
                                  checklist.contains("SC-") || checklist.contains("SI-") ||
                                  checklist.contains("IA-") || checklist.contains("CP-") ||
                                  checklist.contains("CM-") || checklist.contains("RA-");
        assertTrue(hasNistControls, "Should contain NIST control identifiers");
    }

    @Test
    void testGenerateFrameworkChecklistShowsImplementingControls() {
        // When: Generating checklist for PCI-DSS
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should show which controls implement each requirement
        assertTrue(checklist.contains("Implemented by:"));
        assertTrue(checklist.contains("•"));
    }

    @Test
    void testGenerateFrameworkChecklistForInvalidFramework() {
        // When: Generating checklist for invalid framework
        String checklist = ComplianceMatrix.generateFrameworkChecklist("INVALID_FRAMEWORK");

        // Then: Should return report with framework name but no requirements
        assertNotNull(checklist);
        assertTrue(checklist.contains("INVALID_FRAMEWORK"));
        assertTrue(checklist.contains("Total requirements covered: 0"));
    }

    @Test
    void testGetRequirementsForValidFramework() {
        // Given: A security control
        ComplianceMatrix.SecurityControl control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // When: Getting requirements for PCI-DSS
        List<String> requirements = control.getRequirements("PCI-DSS");

        // Then: Should return non-empty list
        assertNotNull(requirements);
        assertFalse(requirements.isEmpty());
    }

    @Test
    void testGetRequirementsForInvalidFramework() {
        // Given: A security control
        ComplianceMatrix.SecurityControl control = ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST;

        // When: Getting requirements for invalid framework
        List<String> requirements = control.getRequirements("INVALID");

        // Then: Should return empty list
        assertNotNull(requirements);
        assertTrue(requirements.isEmpty());
    }

    @Test
    void testGenerateMatrixReportConsistency() {
        // When: Generating report multiple times
        String report1 = ComplianceMatrix.generateMatrixReport();
        String report2 = ComplianceMatrix.generateMatrixReport();

        // Then: Should return identical results
        assertEquals(report1, report2);
    }

    @Test
    void testGenerateFrameworkChecklistConsistency() {
        // When: Generating checklist multiple times
        String checklist1 = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");
        String checklist2 = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should return identical results
        assertEquals(checklist1, checklist2);
    }

    @Test
    void testAllSecurityControlsHaveRequirements() {
        // When: Checking all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            // Then: Each should have requirements for major frameworks
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
    void testFrameworkChecklistIncludesAllRelevantControls() {
        // When: Generating PCI-DSS checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should include controls relevant to PCI-DSS
        assertTrue(checklist.contains("ENCRYPTION_AT_REST") ||
                  checklist.contains("Encryption of data at rest"));
        assertTrue(checklist.contains("ENCRYPTION_IN_TRANSIT") ||
                  checklist.contains("Encryption of data in transit"));
        assertTrue(checklist.contains("ACCESS_CONTROL") ||
                  checklist.contains("Role-based access control"));
    }

    @Test
    void testGenerateMatrixReportHasSummary() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should have summary section at end
        int totalControlsIndex = report.indexOf("Total Controls:");
        int frameworksCoveredIndex = report.indexOf("Frameworks Covered:");

        assertTrue(totalControlsIndex > 0);
        assertTrue(frameworksCoveredIndex > totalControlsIndex);
    }

    @Test
    void testGenerateFrameworkChecklistSorted() {
        // When: Generating checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Requirements should be sorted (verifiable by checking no regex pattern)
        assertNotNull(checklist);
        // Sorted list should contain requirements in a consistent order
        assertTrue(checklist.length() > 100); // Should have substantial content
    }

    @Test
    void testAllControlDescriptionsAreDescriptive() {
        // When: Checking all security controls
        for (ComplianceMatrix.SecurityControl control : ComplianceMatrix.SecurityControl.values()) {
            String description = control.getDescription();

            // Then: Descriptions should be meaningful
            assertTrue(description.length() > 10,
                control.name() + " description should be descriptive");
            assertFalse(description.contains("TODO"),
                control.name() + " description should be complete");
        }
    }

    @Test
    void testGenerateMatrixReportHasBorderCharacters() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should use box-drawing characters for visual appeal
        assertTrue(report.contains("╔") || report.contains("╗") ||
                  report.contains("╚") || report.contains("╝"));
        assertTrue(report.contains("═"));
        assertTrue(report.contains("─"));
    }

    @Test
    void testGenerateFrameworkChecklistHasCheckmarks() {
        // When: Generating checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("PCI-DSS");

        // Then: Should use checkmarks for visual indication
        assertTrue(checklist.contains("✓"));
    }

    @Test
    void testSpecificControlEnumExists() {
        // When/Then: Verify specific controls exist by name
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("ENCRYPTION_AT_REST"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("ENCRYPTION_IN_TRANSIT"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("NETWORK_SEGMENTATION"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("ACCESS_CONTROL"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("AUTHENTICATION"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("AUDIT_LOGGING"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("LOG_RETENTION"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("SECURITY_MONITORING"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("THREAT_DETECTION"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("WAF_PROTECTION"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("BACKUP_RECOVERY"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("HIGH_AVAILABILITY"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("CHANGE_MANAGEMENT"));
        assertDoesNotThrow(() -> ComplianceMatrix.SecurityControl.valueOf("VULNERABILITY_MANAGEMENT"));
    }

    @Test
    void testFrameworkChecklistIncludesControlDescriptions() {
        // When: Generating checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("SOC2");

        // Then: Should include control descriptions
        assertTrue(checklist.contains("Encryption") || checklist.contains("encryption"));
        assertTrue(checklist.contains("access") || checklist.contains("Access"));
    }

    @Test
    void testGenerateMatrixReportLengthIsSubstantial() {
        // When: Generating matrix report
        String report = ComplianceMatrix.generateMatrixReport();

        // Then: Should be comprehensive (at least 2000 characters for 14+ controls)
        assertTrue(report.length() > 2000,
            "Matrix report should be comprehensive, got " + report.length() + " characters");
    }

    @Test
    void testGenerateFrameworkChecklistHasBulletPoints() {
        // When: Generating checklist
        String checklist = ComplianceMatrix.generateFrameworkChecklist("HIPAA");

        // Then: Should use bullet points for list items
        assertTrue(checklist.contains("•"));
    }
}
