package com.cloudforge.core.iam;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.SecurityProfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IAMProfileMapper - security profile to IAM profile mapping.
 */
class IAMProfileMapperTest {

    @Test
    void testProductionMapsToMinimal() {
        IAMProfile profile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        assertEquals(IAMProfile.MINIMAL, profile);
    }

    @Test
    void testStagingMapsToStandard() {
        IAMProfile profile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        assertEquals(IAMProfile.STANDARD, profile);
    }

    @Test
    void testDevMapsToExtended() {
        IAMProfile profile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        assertEquals(IAMProfile.EXTENDED, profile);
    }

    @Test
    void testMapWithOverride() {
        // Override should take precedence
        IAMProfile result = IAMProfileMapper.mapFromSecurity(
            SecurityProfile.PRODUCTION,
            IAMProfile.STANDARD
        );
        assertEquals(IAMProfile.STANDARD, result);
    }

    @Test
    void testMapWithNullOverride() {
        // Null override should use default mapping
        IAMProfile result = IAMProfileMapper.mapFromSecurity(
            SecurityProfile.PRODUCTION,
            null
        );
        assertEquals(IAMProfile.MINIMAL, result);
    }

    @Test
    void testProductionValidCombinations() {
        // PRODUCTION allows MINIMAL and STANDARD
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.MINIMAL));
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.STANDARD));

        // PRODUCTION does NOT allow EXTENDED (too permissive)
        assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.EXTENDED));
    }

    @Test
    void testStagingValidCombinations() {
        // STAGING allows STANDARD and EXTENDED
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.STANDARD));
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.EXTENDED));

        // STAGING does NOT allow MINIMAL (too restrictive for testing)
        assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.MINIMAL));
    }

    @Test
    void testDevValidCombinations() {
        // DEV allows all IAM profiles
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.DEV, IAMProfile.MINIMAL));
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.DEV, IAMProfile.STANDARD));
        assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.DEV, IAMProfile.EXTENDED));
    }

    @Test
    void testGetRecommended() {
        assertEquals(IAMProfile.MINIMAL, IAMProfileMapper.getRecommended(SecurityProfile.PRODUCTION));
        assertEquals(IAMProfile.STANDARD, IAMProfileMapper.getRecommended(SecurityProfile.STAGING));
        assertEquals(IAMProfile.EXTENDED, IAMProfileMapper.getRecommended(SecurityProfile.DEV));
    }

    @Test
    void testSecurityBestPractices() {
        // Production should always map to least privilege (MINIMAL)
        IAMProfile prodProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        assertEquals(IAMProfile.MINIMAL, prodProfile);

        // Verify PRODUCTION + EXTENDED is invalid (security violation)
        assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.EXTENDED));
    }

    @Test
    void testAllSecurityProfiles() {
        for (SecurityProfile sp : SecurityProfile.values()) {
            IAMProfile recommended = IAMProfileMapper.getRecommended(sp);
            assertNotNull(recommended);

            // Recommended profile should always be valid
            assertTrue(IAMProfileMapper.isValidCombination(sp, recommended));
        }
    }
}
