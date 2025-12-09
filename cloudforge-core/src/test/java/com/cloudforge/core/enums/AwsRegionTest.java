package com.cloudforge.core.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AwsRegionTest {

    @Test
    void testFromCodeValidRegion() {
        Optional<AwsRegion> region = AwsRegion.fromCode("us-east-1");
        assertTrue(region.isPresent());
        assertEquals(AwsRegion.US_EAST_1, region.get());
    }

    @Test
    void testFromCodeInvalidRegion() {
        Optional<AwsRegion> region = AwsRegion.fromCode("invalid-region");
        assertTrue(region.isEmpty());
    }

    @Test
    void testFromCodeNull() {
        Optional<AwsRegion> region = AwsRegion.fromCode(null);
        assertTrue(region.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"us-east-1", "us-west-2", "eu-west-1", "ap-northeast-1"})
    void testIsValidRegion(String regionCode) {
        assertTrue(AwsRegion.isValidRegion(regionCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "us-east-99", "", "null"})
    void testIsInvalidRegion(String regionCode) {
        assertFalse(AwsRegion.isValidRegion(regionCode));
    }

    @Test
    void testRegionProperties() {
        AwsRegion region = AwsRegion.US_EAST_1;
        assertEquals("us-east-1", region.code());
        assertEquals("US East (N. Virginia)", region.displayName());
        assertEquals(AwsRegion.GeoArea.US, region.geoArea());
        assertEquals("us-west-2", region.drRegionCode());
    }

    @Test
    void testDrRegion() {
        Optional<AwsRegion> drRegion = AwsRegion.US_EAST_1.drRegion();
        assertTrue(drRegion.isPresent());
        assertEquals(AwsRegion.US_WEST_2, drRegion.get());
    }

    @Test
    void testGetSecondaryRegion() {
        Optional<String> secondary = AwsRegion.getSecondaryRegion("us-east-1");
        assertTrue(secondary.isPresent());
        assertEquals("us-west-2", secondary.get());
    }

    @Test
    void testGetSecondaryRegionInvalid() {
        Optional<String> secondary = AwsRegion.getSecondaryRegion("invalid");
        assertTrue(secondary.isEmpty());
    }

    @Test
    void testSameGeoAreaTrue() {
        assertTrue(AwsRegion.sameGeoArea("us-east-1", "us-west-2"));
        assertTrue(AwsRegion.sameGeoArea("eu-west-1", "eu-central-1"));
        assertTrue(AwsRegion.sameGeoArea("ap-northeast-1", "ap-southeast-1"));
    }

    @Test
    void testSameGeoAreaFalse() {
        assertFalse(AwsRegion.sameGeoArea("us-east-1", "eu-west-1"));
        assertFalse(AwsRegion.sameGeoArea("ap-northeast-1", "sa-east-1"));
    }

    @Test
    void testSameGeoAreaInvalidRegion() {
        assertFalse(AwsRegion.sameGeoArea("us-east-1", "invalid"));
        assertFalse(AwsRegion.sameGeoArea("invalid", "us-east-1"));
        assertFalse(AwsRegion.sameGeoArea("invalid1", "invalid2"));
    }

    @Test
    void testGeoAreaDisplayName() {
        assertEquals("United States", AwsRegion.GeoArea.US.displayName());
        assertEquals("European Union", AwsRegion.GeoArea.EU.displayName());
        assertEquals("Asia Pacific", AwsRegion.GeoArea.APAC.displayName());
    }

    @Test
    void testAllRegionsHaveDrPair() {
        for (AwsRegion region : AwsRegion.values()) {
            assertNotNull(region.drRegionCode(), "Region " + region.code() + " should have DR pair");
            assertTrue(AwsRegion.isValidRegion(region.drRegionCode()),
                "DR region for " + region.code() + " should be valid: " + region.drRegionCode());
        }
    }

    @Test
    void testEuRegionsInEuGeoArea() {
        assertTrue(AwsRegion.EU_WEST_1.geoArea() == AwsRegion.GeoArea.EU);
        assertTrue(AwsRegion.EU_WEST_2.geoArea() == AwsRegion.GeoArea.EU);
        assertTrue(AwsRegion.EU_WEST_3.geoArea() == AwsRegion.GeoArea.EU);
        assertTrue(AwsRegion.EU_CENTRAL_1.geoArea() == AwsRegion.GeoArea.EU);
        assertTrue(AwsRegion.EU_CENTRAL_2.geoArea() == AwsRegion.GeoArea.EU);
        assertTrue(AwsRegion.EU_NORTH_1.geoArea() == AwsRegion.GeoArea.EU);
    }

    @Test
    void testGovCloudRegionsInGovGeoArea() {
        assertEquals(AwsRegion.GeoArea.US_GOV, AwsRegion.US_GOV_EAST_1.geoArea());
        assertEquals(AwsRegion.GeoArea.US_GOV, AwsRegion.US_GOV_WEST_1.geoArea());
    }

    @Test
    void testCanadaRegionsHaveDrPair() {
        assertEquals("us-east-1", AwsRegion.CA_CENTRAL_1.drRegionCode());
        assertEquals("ca-central-1", AwsRegion.CA_WEST_1.drRegionCode());
    }

    @Test
    void testRegionCodeUniqueness() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (AwsRegion region : AwsRegion.values()) {
            assertFalse(codes.contains(region.code()),
                "Duplicate region code: " + region.code());
            codes.add(region.code());
        }
    }
}
