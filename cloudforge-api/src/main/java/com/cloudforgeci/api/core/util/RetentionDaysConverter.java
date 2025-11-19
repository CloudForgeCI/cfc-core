package com.cloudforgeci.api.core.util;

import software.amazon.awscdk.services.logs.RetentionDays;

/**
 * Utility for converting integer day values to AWS CDK RetentionDays enum.
 *
 * Supports all compliance framework retention requirements:
 * - PCI-DSS: 1 year minimum (365 days)
 * - HIPAA: 6 years minimum (2190 days)
 * - SOC2: Varies by policy
 * - GDPR: Varies by data classification
 *
 * This converter maps integer values to the nearest RetentionDays enum value,
 * rounding up to ensure compliance requirements are met.
 */
public final class RetentionDaysConverter {

    private RetentionDaysConverter() {
        // Utility class - prevent instantiation
    }

    /**
     * Convert integer days to RetentionDays enum.
     * Rounds up to the nearest available retention period to ensure compliance.
     *
     * @param days Number of days to retain logs
     * @return Corresponding RetentionDays enum value
     * @throws IllegalArgumentException if days is negative
     */
    public static RetentionDays fromDays(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Retention days cannot be negative: " + days);
        }

        if (days <= 1) {
            return RetentionDays.ONE_DAY;
        } else if (days <= 3) {
            return RetentionDays.THREE_DAYS;
        } else if (days <= 5) {
            return RetentionDays.FIVE_DAYS;
        } else if (days <= 7) {
            return RetentionDays.ONE_WEEK;
        } else if (days <= 14) {
            return RetentionDays.TWO_WEEKS;
        } else if (days <= 30) {
            return RetentionDays.ONE_MONTH;
        } else if (days <= 60) {
            return RetentionDays.TWO_MONTHS;
        } else if (days <= 90) {
            return RetentionDays.THREE_MONTHS;
        } else if (days <= 120) {
            return RetentionDays.FOUR_MONTHS;
        } else if (days <= 150) {
            return RetentionDays.FIVE_MONTHS;
        } else if (days <= 180) {
            return RetentionDays.SIX_MONTHS;
        } else if (days <= 365) {
            return RetentionDays.ONE_YEAR; // PCI-DSS minimum
        } else if (days <= 400) {
            return RetentionDays.THIRTEEN_MONTHS;
        } else if (days <= 545) {
            return RetentionDays.EIGHTEEN_MONTHS;
        } else if (days <= 731) {
            return RetentionDays.TWO_YEARS;
        } else if (days <= 1096) {
            return RetentionDays.THREE_YEARS;
        } else if (days <= 1827) {
            return RetentionDays.FIVE_YEARS;
        } else if (days <= 2192) {
            return RetentionDays.SIX_YEARS; // HIPAA minimum (exact 6 years)
        } else if (days <= 2557) {
            return RetentionDays.SEVEN_YEARS;
        } else if (days <= 2920) {
            return RetentionDays.EIGHT_YEARS;
        } else if (days <= 3285) {
            return RetentionDays.NINE_YEARS;
        } else if (days <= 3650) {
            return RetentionDays.TEN_YEARS;
        } else {
            return RetentionDays.INFINITE; // For extreme retention requirements
        }
    }

    /**
     * Convert integer days to RetentionDays enum, returning null for null input.
     * Useful for optional retention settings.
     *
     * @param days Number of days to retain logs, or null
     * @return Corresponding RetentionDays enum value, or null if input is null
     */
    public static RetentionDays fromDaysOrNull(Integer days) {
        return days == null ? null : fromDays(days);
    }
}
