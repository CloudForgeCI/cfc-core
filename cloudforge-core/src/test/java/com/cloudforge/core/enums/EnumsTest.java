package com.cloudforge.core.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all CloudForge core enums.
 */
class EnumsTest {

    @Test
    void testIAMProfileValues() {
        IAMProfile[] profiles = IAMProfile.values();

        assertTrue(profiles.length >= 3);
        assertNotNull(IAMProfile.valueOf("MINIMAL"));
        assertNotNull(IAMProfile.valueOf("STANDARD"));
        assertNotNull(IAMProfile.valueOf("EXTENDED"));
    }

    @Test
    void testIAMProfileToString() {
        assertEquals("MINIMAL", IAMProfile.MINIMAL.toString());
        assertEquals("STANDARD", IAMProfile.STANDARD.toString());
        assertEquals("EXTENDED", IAMProfile.EXTENDED.toString());
    }

    @Test
    void testSecurityProfileValues() {
        SecurityProfile[] profiles = SecurityProfile.values();

        assertTrue(profiles.length >= 3);
        assertNotNull(SecurityProfile.valueOf("DEV"));
        assertNotNull(SecurityProfile.valueOf("STAGING"));
        assertNotNull(SecurityProfile.valueOf("PRODUCTION"));
    }

    @Test
    void testSecurityProfileToString() {
        assertEquals("DEV", SecurityProfile.DEV.toString());
        assertEquals("STAGING", SecurityProfile.STAGING.toString());
        assertEquals("PRODUCTION", SecurityProfile.PRODUCTION.toString());
    }

    @Test
    void testRuntimeTypeValues() {
        RuntimeType[] types = RuntimeType.values();

        assertTrue(types.length >= 2);
        assertNotNull(RuntimeType.valueOf("EC2"));
        assertNotNull(RuntimeType.valueOf("FARGATE"));
    }

    @Test
    void testRuntimeTypeToString() {
        assertEquals("EC2", RuntimeType.EC2.toString());
        assertEquals("FARGATE", RuntimeType.FARGATE.toString());
    }

    @Test
    void testTopologyTypeValues() {
        TopologyType[] types = TopologyType.values();

        assertTrue(types.length >= 1);
        // At least one topology type should exist
        assertTrue(types.length > 0);
    }

    @Test
    void testComplianceModeValues() {
        ComplianceMode[] modes = ComplianceMode.values();

        // ComplianceMode has ENFORCE and ADVISORY
        assertEquals(2, modes.length);

        // Verify both modes exist
        assertNotNull(ComplianceMode.valueOf("ENFORCE"));
        assertNotNull(ComplianceMode.valueOf("ADVISORY"));
    }

    @Test
    void testComplianceModeToString() {
        for (ComplianceMode mode : ComplianceMode.values()) {
            assertNotNull(mode.toString());
            assertFalse(mode.toString().isEmpty());
        }
    }

    @Test
    void testEnumValueOf() {
        // Test that valueOf works for all enums
        assertThrows(IllegalArgumentException.class, () -> IAMProfile.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> SecurityProfile.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeType.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> TopologyType.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> ComplianceMode.valueOf("INVALID"));
    }

    @Test
    void testEnumEquality() {
        assertEquals(IAMProfile.MINIMAL, IAMProfile.valueOf("MINIMAL"));
        assertEquals(SecurityProfile.PRODUCTION, SecurityProfile.valueOf("PRODUCTION"));
        assertEquals(RuntimeType.FARGATE, RuntimeType.valueOf("FARGATE"));
    }

    @Test
    void testEnumNotNull() {
        assertNotNull(IAMProfile.MINIMAL);
        assertNotNull(SecurityProfile.DEV);
        assertNotNull(RuntimeType.EC2);

        for (ComplianceMode mode : ComplianceMode.values()) {
            assertNotNull(mode);
        }

        for (TopologyType type : TopologyType.values()) {
            assertNotNull(type);
        }
    }

    // ComplianceMode method tests
    @Test
    void testComplianceModeFromString() {
        assertEquals(ComplianceMode.ENFORCE,
            ComplianceMode.fromString("enforce", ComplianceMode.ADVISORY));
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.fromString("advisory", ComplianceMode.ENFORCE));

        // Case insensitive
        assertEquals(ComplianceMode.ENFORCE,
            ComplianceMode.fromString("ENFORCE", ComplianceMode.ADVISORY));
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.fromString("AdViSoRy", ComplianceMode.ENFORCE));
    }

    @Test
    void testComplianceModeFromStringWithNull() {
        // Null should return default
        assertEquals(ComplianceMode.ENFORCE,
            ComplianceMode.fromString(null, ComplianceMode.ENFORCE));
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.fromString(null, ComplianceMode.ADVISORY));
    }

    @Test
    void testComplianceModeFromStringWithEmpty() {
        // Empty string should return default
        assertEquals(ComplianceMode.ENFORCE,
            ComplianceMode.fromString("", ComplianceMode.ENFORCE));
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.fromString("   ", ComplianceMode.ADVISORY));
    }

    @Test
    void testComplianceModeFromStringWithInvalid() {
        // Invalid string should return default
        assertEquals(ComplianceMode.ENFORCE,
            ComplianceMode.fromString("invalid", ComplianceMode.ENFORCE));
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.fromString("xyz", ComplianceMode.ADVISORY));
    }

    @Test
    void testComplianceModeDefaultForProfile() {
        // PRODUCTION defaults to ENFORCE
        assertEquals(ComplianceMode.ENFORCE,
            ComplianceMode.defaultForProfile(SecurityProfile.PRODUCTION));

        // STAGING defaults to ADVISORY
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.defaultForProfile(SecurityProfile.STAGING));

        // DEV defaults to ADVISORY
        assertEquals(ComplianceMode.ADVISORY,
            ComplianceMode.defaultForProfile(SecurityProfile.DEV));
    }

    @Test
    void testComplianceModeSecurityBestPractices() {
        // Production should enforce compliance by default
        ComplianceMode prodMode = ComplianceMode.defaultForProfile(SecurityProfile.PRODUCTION);
        assertEquals(ComplianceMode.ENFORCE, prodMode);

        // Non-production can be advisory
        ComplianceMode devMode = ComplianceMode.defaultForProfile(SecurityProfile.DEV);
        assertEquals(ComplianceMode.ADVISORY, devMode);
    }
}
