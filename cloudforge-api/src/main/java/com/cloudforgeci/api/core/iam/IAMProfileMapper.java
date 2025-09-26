package com.cloudforgeci.api.core.iam;

import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.SecurityProfile;

/**
 * Maps Security Profiles to appropriate IAM Profiles following security best practices.
 * This ensures that IAM permissions align with security requirements.
 */
public final class IAMProfileMapper {
    private IAMProfileMapper() {}

    /**
     * Maps a Security Profile to the appropriate IAM Profile.
     * 
     * Security Profile -> IAM Profile Mapping:
     * - PRODUCTION -> MINIMAL (least privilege for production)
     * - STAGING -> STANDARD (balanced permissions for testing)
     * - DEV -> EXTENDED (broader permissions for development)
     * 
     * @param securityProfile the security profile
     * @return the corresponding IAM profile
     */
    public static IAMProfile mapFromSecurity(SecurityProfile securityProfile) {
        return switch (securityProfile) {
            case PRODUCTION -> IAMProfile.MINIMAL;
            case STAGING -> IAMProfile.STANDARD;
            case DEV -> IAMProfile.EXTENDED;
        };
    }

    /**
     * Maps a Security Profile to the appropriate IAM Profile with override capability.
     * This allows explicit IAM profile selection when needed.
     * 
     * @param securityProfile the security profile
     * @param overrideIamProfile the IAM profile override (null to use default mapping)
     * @return the IAM profile to use
     */
    public static IAMProfile mapFromSecurity(SecurityProfile securityProfile, IAMProfile overrideIamProfile) {
        if (overrideIamProfile != null) {
            return overrideIamProfile;
        }
        return mapFromSecurity(securityProfile);
    }

    /**
     * Validates that the IAM profile is appropriate for the security profile.
     * Prevents dangerous combinations like PRODUCTION + EXTENDED IAM.
     * 
     * @param securityProfile the security profile
     * @param iamProfile the IAM profile
     * @return true if the combination is valid, false otherwise
     */
    public static boolean isValidCombination(SecurityProfile securityProfile, IAMProfile iamProfile) {
        return switch (securityProfile) {
            case PRODUCTION -> iamProfile == IAMProfile.MINIMAL || iamProfile == IAMProfile.STANDARD;
            case STAGING -> iamProfile == IAMProfile.STANDARD || iamProfile == IAMProfile.EXTENDED;
            case DEV -> true; // DEV can use any IAM profile
        };
    }

    /**
     * Gets the recommended IAM profile for a given security profile.
     * 
     * @param securityProfile the security profile
     * @return the recommended IAM profile
     */
    public static IAMProfile getRecommended(SecurityProfile securityProfile) {
        return mapFromSecurity(securityProfile);
    }
}
