package com.cloudforgeci.api.core.util;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.services.logs.RetentionDays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RetentionDaysConverter utility.
 */
class RetentionDaysConverterTest {

    @Test
    void testShortRetentionPeriods() {
        assertEquals(RetentionDays.ONE_DAY, RetentionDaysConverter.fromDays(1));
        assertEquals(RetentionDays.THREE_DAYS, RetentionDaysConverter.fromDays(3));
        assertEquals(RetentionDays.FIVE_DAYS, RetentionDaysConverter.fromDays(5));
        assertEquals(RetentionDays.ONE_WEEK, RetentionDaysConverter.fromDays(7));
        assertEquals(RetentionDays.TWO_WEEKS, RetentionDaysConverter.fromDays(14));
    }

    @Test
    void testMonthlyRetentionPeriods() {
        assertEquals(RetentionDays.ONE_MONTH, RetentionDaysConverter.fromDays(30));
        assertEquals(RetentionDays.TWO_MONTHS, RetentionDaysConverter.fromDays(60));
        assertEquals(RetentionDays.THREE_MONTHS, RetentionDaysConverter.fromDays(90));
        assertEquals(RetentionDays.FOUR_MONTHS, RetentionDaysConverter.fromDays(120));
        assertEquals(RetentionDays.FIVE_MONTHS, RetentionDaysConverter.fromDays(150));
        assertEquals(RetentionDays.SIX_MONTHS, RetentionDaysConverter.fromDays(180));
    }

    @Test
    void testPciDssCompliance() {
        // PCI-DSS requires 1 year minimum (365 days)
        assertEquals(RetentionDays.ONE_YEAR, RetentionDaysConverter.fromDays(365));
    }

    @Test
    void testHipaaCompliance() {
        // HIPAA requires 6 years minimum (2190 days)
        assertEquals(RetentionDays.SIX_YEARS, RetentionDaysConverter.fromDays(2190));
    }

    @Test
    void testYearlyRetentionPeriods() {
        assertEquals(RetentionDays.ONE_YEAR, RetentionDaysConverter.fromDays(365));
        assertEquals(RetentionDays.THIRTEEN_MONTHS, RetentionDaysConverter.fromDays(400));
        assertEquals(RetentionDays.EIGHTEEN_MONTHS, RetentionDaysConverter.fromDays(545));
        assertEquals(RetentionDays.TWO_YEARS, RetentionDaysConverter.fromDays(730));
        assertEquals(RetentionDays.THREE_YEARS, RetentionDaysConverter.fromDays(1095));
        assertEquals(RetentionDays.FIVE_YEARS, RetentionDaysConverter.fromDays(1827));
    }

    @Test
    void testMultiYearRetentionPeriods() {
        assertEquals(RetentionDays.SIX_YEARS, RetentionDaysConverter.fromDays(2190));
        assertEquals(RetentionDays.SEVEN_YEARS, RetentionDaysConverter.fromDays(2555));
        assertEquals(RetentionDays.EIGHT_YEARS, RetentionDaysConverter.fromDays(2920));
        assertEquals(RetentionDays.NINE_YEARS, RetentionDaysConverter.fromDays(3285));
        assertEquals(RetentionDays.TEN_YEARS, RetentionDaysConverter.fromDays(3650));
    }

    @Test
    void testInfiniteRetention() {
        // Any value over 10 years should map to INFINITE
        assertEquals(RetentionDays.INFINITE, RetentionDaysConverter.fromDays(3651));
        assertEquals(RetentionDays.INFINITE, RetentionDaysConverter.fromDays(5000));
        assertEquals(RetentionDays.INFINITE, RetentionDaysConverter.fromDays(10000));
    }

    @Test
    void testRoundingUp() {
        // Should round UP to next available period to ensure compliance
        assertEquals(RetentionDays.THREE_DAYS, RetentionDaysConverter.fromDays(2));
        assertEquals(RetentionDays.ONE_WEEK, RetentionDaysConverter.fromDays(6));
        assertEquals(RetentionDays.TWO_WEEKS, RetentionDaysConverter.fromDays(10));
        assertEquals(RetentionDays.ONE_MONTH, RetentionDaysConverter.fromDays(25));
        assertEquals(RetentionDays.ONE_YEAR, RetentionDaysConverter.fromDays(200));
    }

    @Test
    void testBoundaryValues() {
        // Test exact boundary values
        assertEquals(RetentionDays.ONE_DAY, RetentionDaysConverter.fromDays(1));
        assertEquals(RetentionDays.THREE_DAYS, RetentionDaysConverter.fromDays(3));
        assertEquals(RetentionDays.ONE_WEEK, RetentionDaysConverter.fromDays(7));
        assertEquals(RetentionDays.ONE_MONTH, RetentionDaysConverter.fromDays(30));
        assertEquals(RetentionDays.ONE_YEAR, RetentionDaysConverter.fromDays(365));
        assertEquals(RetentionDays.TEN_YEARS, RetentionDaysConverter.fromDays(3650));
    }

    @Test
    void testNegativeDays() {
        // Negative days should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> RetentionDaysConverter.fromDays(-1));
        assertThrows(IllegalArgumentException.class, () -> RetentionDaysConverter.fromDays(-100));
    }

    @Test
    void testFromDaysOrNullWithNull() {
        assertNull(RetentionDaysConverter.fromDaysOrNull(null));
    }

    @Test
    void testFromDaysOrNullWithValue() {
        assertEquals(RetentionDays.ONE_YEAR, RetentionDaysConverter.fromDaysOrNull(365));
        assertEquals(RetentionDays.SIX_YEARS, RetentionDaysConverter.fromDaysOrNull(2190));
    }

    @Test
    void testZeroDays() {
        // Zero should map to ONE_DAY (minimum retention)
        assertEquals(RetentionDays.ONE_DAY, RetentionDaysConverter.fromDays(0));
    }
}
