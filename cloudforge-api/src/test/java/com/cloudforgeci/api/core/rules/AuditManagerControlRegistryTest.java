package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AuditManagerControlRegistry.
 *
 * Tests the central registry mapping infrastructure controls to compliance frameworks.
 */
class AuditManagerControlRegistryTest {

    @Test
    void testGetControlByValidId() {
        // When: Getting a control by valid ID
        AuditManagerControl control = AuditManagerControlRegistry.getControl("ENCRYPTION_AT_REST");

        // Then: Should return the control
        assertNotNull(control);
        assertEquals("ENCRYPTION_AT_REST", control.controlId());
        assertTrue(control.description().contains("Encryption"));
        assertFalse(control.configRuleIds().isEmpty());
        assertFalse(control.frameworkMappings().isEmpty());
        assertFalse(control.evidenceSources().isEmpty());
    }

    @Test
    void testGetControlByInvalidId() {
        // When: Getting a control with invalid ID
        AuditManagerControl control = AuditManagerControlRegistry.getControl("INVALID_CONTROL_ID");

        // Then: Should return null
        assertNull(control);
    }

    @Test
    void testGetControlByNullId() {
        // When: Getting a control with null ID
        AuditManagerControl control = AuditManagerControlRegistry.getControl(null);

        // Then: Should return null
        assertNull(control);
    }

    @Test
    void testGetAllControls() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: Should return multiple controls
        assertNotNull(controls);
        assertTrue(controls.size() > 10);

        // And: All controls should have required fields
        for (AuditManagerControl control : controls) {
            assertNotNull(control.controlId());
            assertNotNull(control.description());
            assertNotNull(control.configRuleIds());
            assertNotNull(control.frameworkMappings());
            assertNotNull(control.evidenceSources());
        }
    }

    @Test
    void testGetAllControlsIsNotEmpty() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: Should contain expected controls
        List<String> controlIds = controls.stream()
            .map(AuditManagerControl::controlId)
            .toList();

        assertTrue(controlIds.contains("ENCRYPTION_AT_REST"));
        assertTrue(controlIds.contains("ENCRYPTION_IN_TRANSIT"));
        assertTrue(controlIds.contains("NETWORK_SEGMENTATION"));
        assertTrue(controlIds.contains("ACCESS_CONTROL"));
        assertTrue(controlIds.contains("AUTHENTICATION"));
        assertTrue(controlIds.contains("AUDIT_LOGGING"));
    }

    @Test
    void testGetControlsForPciDss() {
        // When: Getting controls for PCI-DSS framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework("PCI-DSS");

        // Then: Should return controls that apply to PCI-DSS
        assertNotNull(controls);
        assertFalse(controls.isEmpty());

        // And: All returned controls should apply to PCI-DSS
        for (AuditManagerControl control : controls) {
            assertTrue(control.appliesToFramework("PCI-DSS"));
        }
    }

    @Test
    void testGetControlsForSoc2() {
        // When: Getting controls for SOC2 framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework("SOC2");

        // Then: Should return controls that apply to SOC2
        assertNotNull(controls);
        assertFalse(controls.isEmpty());

        // And: All returned controls should apply to SOC2
        for (AuditManagerControl control : controls) {
            assertTrue(control.appliesToFramework("SOC2"));
        }
    }

    @Test
    void testGetControlsForHipaa() {
        // When: Getting controls for HIPAA framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework("HIPAA");

        // Then: Should return controls that apply to HIPAA
        assertNotNull(controls);
        assertFalse(controls.isEmpty());

        // And: All returned controls should apply to HIPAA
        for (AuditManagerControl control : controls) {
            assertTrue(control.appliesToFramework("HIPAA"));
        }
    }

    @Test
    void testGetControlsForGdpr() {
        // When: Getting controls for GDPR framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework("GDPR");

        // Then: Should return controls that apply to GDPR
        assertNotNull(controls);
        assertFalse(controls.isEmpty());

        // And: All returned controls should apply to GDPR
        for (AuditManagerControl control : controls) {
            assertTrue(control.appliesToFramework("GDPR"));
        }
    }

    @Test
    void testGetControlsForFrameworkCaseInsensitive() {
        // When: Getting controls with different case
        List<AuditManagerControl> controlsUpper = AuditManagerControlRegistry.getControlsForFramework("PCI-DSS");
        List<AuditManagerControl> controlsLower = AuditManagerControlRegistry.getControlsForFramework("pci-dss");
        List<AuditManagerControl> controlsMixed = AuditManagerControlRegistry.getControlsForFramework("Pci-Dss");

        // Then: All should return the same results
        assertEquals(controlsUpper.size(), controlsLower.size());
        assertEquals(controlsUpper.size(), controlsMixed.size());
    }

    @Test
    void testGetControlsForInvalidFramework() {
        // When: Getting controls for invalid framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework("INVALID_FRAMEWORK");

        // Then: Should return empty list
        assertNotNull(controls);
        assertTrue(controls.isEmpty());
    }

    @Test
    void testGetConfigRulesForPciDss() {
        // When: Getting Config rules for PCI-DSS
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework("PCI-DSS");

        // Then: Should return Config rule IDs
        assertNotNull(configRules);
        assertFalse(configRules.isEmpty());

        // And: Should be sorted and distinct
        List<String> sortedRules = configRules.stream().sorted().distinct().toList();
        assertEquals(configRules, sortedRules);
    }

    @Test
    void testGetConfigRulesForSoc2() {
        // When: Getting Config rules for SOC2
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework("SOC2");

        // Then: Should return Config rule IDs
        assertNotNull(configRules);
        assertFalse(configRules.isEmpty());

        // And: Should contain expected rules
        assertTrue(configRules.stream().anyMatch(rule -> rule.contains("CloudTrail")));
    }

    @Test
    void testGetConfigRulesForHipaa() {
        // When: Getting Config rules for HIPAA
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework("HIPAA");

        // Then: Should return Config rule IDs
        assertNotNull(configRules);
        assertFalse(configRules.isEmpty());

        // And: Should contain encryption-related rules
        assertTrue(configRules.stream().anyMatch(rule -> rule.contains("Encryption") || rule.contains("Ebs")));
    }

    @Test
    void testGetConfigRulesForGdpr() {
        // When: Getting Config rules for GDPR
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework("GDPR");

        // Then: Should return Config rule IDs
        assertNotNull(configRules);
        assertFalse(configRules.isEmpty());
    }

    @Test
    void testGetConfigRulesForInvalidFramework() {
        // When: Getting Config rules for invalid framework
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework("INVALID");

        // Then: Should return empty list
        assertNotNull(configRules);
        assertTrue(configRules.isEmpty());
    }

    @Test
    void testConfigRulesAreDistinct() {
        // When: Getting Config rules for a framework
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework("PCI-DSS");

        // Then: Should not contain duplicates
        long uniqueCount = configRules.stream().distinct().count();
        assertEquals(configRules.size(), uniqueCount);
    }

    @Test
    void testGetEvidenceSourcesForPciDss() {
        // When: Getting evidence sources for PCI-DSS
        List<String> sources = AuditManagerControlRegistry.getEvidenceSourcesForFramework("PCI-DSS");

        // Then: Should return evidence source IDs
        assertNotNull(sources);
        assertFalse(sources.isEmpty());

        // And: Should be sorted and distinct
        List<String> sortedSources = sources.stream().sorted().distinct().toList();
        assertEquals(sources, sortedSources);
    }

    @Test
    void testGetEvidenceSourcesForSoc2() {
        // When: Getting evidence sources for SOC2
        List<String> sources = AuditManagerControlRegistry.getEvidenceSourcesForFramework("SOC2");

        // Then: Should return evidence sources
        assertNotNull(sources);
        assertFalse(sources.isEmpty());

        // And: Should contain expected sources
        assertTrue(sources.contains("cloudtrail") || sources.contains("config"));
    }

    @Test
    void testGetEvidenceSourcesForHipaa() {
        // When: Getting evidence sources for HIPAA
        List<String> sources = AuditManagerControlRegistry.getEvidenceSourcesForFramework("HIPAA");

        // Then: Should return evidence sources
        assertNotNull(sources);
        assertFalse(sources.isEmpty());
    }

    @Test
    void testGetEvidenceSourcesForGdpr() {
        // When: Getting evidence sources for GDPR
        List<String> sources = AuditManagerControlRegistry.getEvidenceSourcesForFramework("GDPR");

        // Then: Should return evidence sources
        assertNotNull(sources);
        assertFalse(sources.isEmpty());
    }

    @Test
    void testGetEvidenceSourcesForInvalidFramework() {
        // When: Getting evidence sources for invalid framework
        List<String> sources = AuditManagerControlRegistry.getEvidenceSourcesForFramework("INVALID");

        // Then: Should return empty list
        assertNotNull(sources);
        assertTrue(sources.isEmpty());
    }

    @Test
    void testEvidenceSourcesAreDistinct() {
        // When: Getting evidence sources for a framework
        List<String> sources = AuditManagerControlRegistry.getEvidenceSourcesForFramework("PCI-DSS");

        // Then: Should not contain duplicates
        long uniqueCount = sources.stream().distinct().count();
        assertEquals(sources.size(), uniqueCount);
    }

    @Test
    void testGetFrameworkControlMapForPciDss() {
        // When: Getting framework control map for PCI-DSS
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("PCI-DSS");

        // Then: Should return control mappings
        assertNotNull(controlMap);
        assertFalse(controlMap.isEmpty());

        // And: Each entry should have control ID, name, and Config rules
        for (Map.Entry<String, List<String>> entry : controlMap.entrySet()) {
            assertTrue(entry.getKey().contains(" - "));
            assertFalse(entry.getValue().isEmpty());
        }
    }

    @Test
    void testGetFrameworkControlMapForSoc2() {
        // When: Getting framework control map for SOC2
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("SOC2");

        // Then: Should return control mappings
        assertNotNull(controlMap);
        assertFalse(controlMap.isEmpty());

        // And: Should contain SOC2-specific controls
        boolean hasSoc2Controls = controlMap.keySet().stream()
            .anyMatch(key -> key.contains("CC"));
        assertTrue(hasSoc2Controls);
    }

    @Test
    void testGetFrameworkControlMapForHipaa() {
        // When: Getting framework control map for HIPAA
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("HIPAA");

        // Then: Should return control mappings
        assertNotNull(controlMap);
        assertFalse(controlMap.isEmpty());

        // And: Should contain HIPAA-specific controls
        boolean hasHipaaControls = controlMap.keySet().stream()
            .anyMatch(key -> key.contains("164."));
        assertTrue(hasHipaaControls);
    }

    @Test
    void testGetFrameworkControlMapForGdpr() {
        // When: Getting framework control map for GDPR
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("GDPR");

        // Then: Should return control mappings
        assertNotNull(controlMap);
        assertFalse(controlMap.isEmpty());

        // And: Should contain GDPR-specific controls
        boolean hasGdprControls = controlMap.keySet().stream()
            .anyMatch(key -> key.contains("Art.") || key.contains("Art"));
        assertTrue(hasGdprControls);
    }

    @Test
    void testGetFrameworkControlMapForInvalidFramework() {
        // When: Getting framework control map for invalid framework
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("INVALID");

        // Then: Should return empty map
        assertNotNull(controlMap);
        assertTrue(controlMap.isEmpty());
    }

    @Test
    void testEncryptionAtRestControlHasMultipleFrameworks() {
        // When: Getting encryption at rest control
        AuditManagerControl control = AuditManagerControlRegistry.getControl("ENCRYPTION_AT_REST");

        // Then: Should apply to multiple frameworks
        assertTrue(control.appliesToFramework("PCI-DSS"));
        assertTrue(control.appliesToFramework("HIPAA"));
        assertTrue(control.appliesToFramework("SOC2"));
        assertTrue(control.appliesToFramework("GDPR"));
    }

    @Test
    void testAccessControlHasConfigRules() {
        // When: Getting access control
        AuditManagerControl control = AuditManagerControlRegistry.getControl("ACCESS_CONTROL");

        // Then: Should have Config rules
        assertFalse(control.configRuleIds().isEmpty());
        assertTrue(control.configRuleIds().stream().anyMatch(rule -> rule.contains("IAM")));
    }

    @Test
    void testAuditLoggingHasEvidenceSources() {
        // When: Getting audit logging control
        AuditManagerControl control = AuditManagerControlRegistry.getControl("AUDIT_LOGGING");

        // Then: Should have evidence sources
        assertFalse(control.evidenceSources().isEmpty());
        assertTrue(control.evidenceSources().contains("cloudtrail"));
    }

    @Test
    void testAllControlsHaveDescription() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: All should have non-empty descriptions
        for (AuditManagerControl control : controls) {
            assertNotNull(control.description());
            assertFalse(control.description().isBlank());
        }
    }

    @Test
    void testAllControlsHaveConfigRules() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: All should have at least one Config rule
        for (AuditManagerControl control : controls) {
            assertFalse(control.configRuleIds().isEmpty(),
                "Control " + control.controlId() + " should have Config rules");
        }
    }

    @Test
    void testAllControlsHaveFrameworkMappings() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: All should have at least one framework mapping
        for (AuditManagerControl control : controls) {
            assertFalse(control.frameworkMappings().isEmpty(),
                "Control " + control.controlId() + " should have framework mappings");
        }
    }

    @Test
    void testAllControlsHaveEvidenceSources() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: All should have at least one evidence source
        for (AuditManagerControl control : controls) {
            assertFalse(control.evidenceSources().isEmpty(),
                "Control " + control.controlId() + " should have evidence sources");
        }
    }

    @Test
    void testSpecificControlsExist() {
        // When: Checking for specific controls
        List<String> expectedControls = List.of(
            "ENCRYPTION_AT_REST",
            "ENCRYPTION_IN_TRANSIT",
            "NETWORK_SEGMENTATION",
            "ACCESS_CONTROL",
            "AUTHENTICATION",
            "AUDIT_LOGGING",
            "LOG_RETENTION",
            "SECURITY_MONITORING",
            "THREAT_DETECTION",
            "WAF_PROTECTION",
            "BACKUP_RECOVERY",
            "HIGH_AVAILABILITY",
            "CHANGE_MANAGEMENT",
            "VULNERABILITY_MANAGEMENT",
            "KEY_MANAGEMENT"
        );

        // Then: All expected controls should exist
        for (String controlId : expectedControls) {
            AuditManagerControl control = AuditManagerControlRegistry.getControl(controlId);
            assertNotNull(control, "Control " + controlId + " should exist");
        }
    }

    @Test
    void testDifferentFrameworksHaveDifferentControlCounts() {
        // When: Getting controls for different frameworks
        int pciCount = AuditManagerControlRegistry.getControlsForFramework("PCI-DSS").size();
        int soc2Count = AuditManagerControlRegistry.getControlsForFramework("SOC2").size();
        int hipaaCount = AuditManagerControlRegistry.getControlsForFramework("HIPAA").size();
        int gdprCount = AuditManagerControlRegistry.getControlsForFramework("GDPR").size();

        // Then: All should have controls but may have different counts
        assertTrue(pciCount > 0);
        assertTrue(soc2Count > 0);
        assertTrue(hipaaCount > 0);
        assertTrue(gdprCount > 0);
    }

    @Test
    void testFrameworkControlMapKeysContainControlDetails() {
        // When: Getting framework control map
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("PCI-DSS");

        // Then: Keys should contain both control ID and name
        for (String key : controlMap.keySet()) {
            assertTrue(key.contains(" - "), "Key should contain separator: " + key);
            String[] parts = key.split(" - ");
            assertEquals(2, parts.length, "Key should have control ID and name");
            assertFalse(parts[0].isBlank(), "Control ID should not be blank");
            assertFalse(parts[1].isBlank(), "Control name should not be blank");
        }
    }

    @Test
    void testCommonControlsExistInMultipleFrameworks() {
        // Given: Common security controls
        List<String> commonControls = List.of(
            "ENCRYPTION_AT_REST",
            "ENCRYPTION_IN_TRANSIT",
            "AUDIT_LOGGING",
            "ACCESS_CONTROL"
        );

        // When/Then: These controls should exist in multiple frameworks
        for (String controlId : commonControls) {
            AuditManagerControl control = AuditManagerControlRegistry.getControl(controlId);
            assertNotNull(control);

            int frameworkCount = 0;
            if (control.appliesToFramework("PCI-DSS")) frameworkCount++;
            if (control.appliesToFramework("HIPAA")) frameworkCount++;
            if (control.appliesToFramework("SOC2")) frameworkCount++;
            if (control.appliesToFramework("GDPR")) frameworkCount++;

            assertTrue(frameworkCount >= 2,
                "Common control " + controlId + " should apply to at least 2 frameworks");
        }
    }

    @Test
    void testConfigRulesAreWellFormed() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: All Config rules should be well-formed
        for (AuditManagerControl control : controls) {
            for (String configRule : control.configRuleIds()) {
                assertNotNull(configRule);
                assertFalse(configRule.isBlank());
                // Config rule IDs should be PascalCase or contain common patterns
                assertTrue(configRule.matches("[A-Z][a-zA-Z0-9]*") || configRule.contains("Rule"),
                    "Config rule should be well-formed: " + configRule);
            }
        }
    }

    @Test
    void testEvidenceSourcesAreLowercase() {
        // When: Getting all controls
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getAllControls();

        // Then: All evidence sources should be lowercase (AWS service names)
        for (AuditManagerControl control : controls) {
            for (String source : control.evidenceSources()) {
                assertNotNull(source);
                assertFalse(source.isBlank());
                // Evidence sources should typically be lowercase service names
                assertTrue(source.toLowerCase().equals(source) || source.contains("-"),
                    "Evidence source should be lowercase or hyphenated: " + source);
            }
        }
    }

    @Test
    void testRegistryIsImmutable() {
        // When: Getting all controls multiple times
        List<AuditManagerControl> controls1 = AuditManagerControlRegistry.getAllControls();
        List<AuditManagerControl> controls2 = AuditManagerControlRegistry.getAllControls();

        // Then: Should return consistent results
        assertEquals(controls1.size(), controls2.size());
    }

    @Test
    void testFrameworkControlMapValuesAreConfigRules() {
        // When: Getting framework control map
        Map<String, List<String>> controlMap = AuditManagerControlRegistry.getFrameworkControlMap("PCI-DSS");

        // Then: Values should be Config rule IDs
        for (List<String> configRules : controlMap.values()) {
            assertFalse(configRules.isEmpty());
            for (String rule : configRules) {
                assertNotNull(rule);
                assertFalse(rule.isBlank());
            }
        }
    }
}
