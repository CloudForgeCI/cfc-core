package com.cloudforge.core.enums;

import java.util.Optional;

/**
 * AWS Regions enumeration with metadata for disaster recovery and compliance.
 *
 * <p>Each region includes:</p>
 * <ul>
 *   <li>Region code (e.g., "us-east-1")</li>
 *   <li>Display name (e.g., "US East (N. Virginia)")</li>
 *   <li>Geographic area for data residency</li>
 *   <li>Paired DR region for cross-region replication</li>
 * </ul>
 */
public enum AwsRegion {

    // US regions
    US_EAST_1("us-east-1", "US East (N. Virginia)", GeoArea.US, "us-west-2"),
    US_EAST_2("us-east-2", "US East (Ohio)", GeoArea.US, "us-west-1"),
    US_WEST_1("us-west-1", "US West (N. California)", GeoArea.US, "us-east-2"),
    US_WEST_2("us-west-2", "US West (Oregon)", GeoArea.US, "us-east-1"),

    // GovCloud (US)
    US_GOV_EAST_1("us-gov-east-1", "AWS GovCloud (US-East)", GeoArea.US_GOV, "us-gov-west-1"),
    US_GOV_WEST_1("us-gov-west-1", "AWS GovCloud (US-West)", GeoArea.US_GOV, "us-gov-east-1"),

    // Canada
    CA_CENTRAL_1("ca-central-1", "Canada (Central)", GeoArea.CANADA, "us-east-1"),
    CA_WEST_1("ca-west-1", "Canada West (Calgary)", GeoArea.CANADA, "ca-central-1"),

    // Europe
    EU_WEST_1("eu-west-1", "Europe (Ireland)", GeoArea.EU, "eu-central-1"),
    EU_WEST_2("eu-west-2", "Europe (London)", GeoArea.EU, "eu-west-1"),
    EU_WEST_3("eu-west-3", "Europe (Paris)", GeoArea.EU, "eu-central-1"),
    EU_CENTRAL_1("eu-central-1", "Europe (Frankfurt)", GeoArea.EU, "eu-west-1"),
    EU_CENTRAL_2("eu-central-2", "Europe (Zurich)", GeoArea.EU, "eu-central-1"),
    EU_NORTH_1("eu-north-1", "Europe (Stockholm)", GeoArea.EU, "eu-west-1"),
    EU_SOUTH_1("eu-south-1", "Europe (Milan)", GeoArea.EU, "eu-central-1"),
    EU_SOUTH_2("eu-south-2", "Europe (Spain)", GeoArea.EU, "eu-west-1"),

    // Asia Pacific
    AP_EAST_1("ap-east-1", "Asia Pacific (Hong Kong)", GeoArea.APAC, "ap-southeast-1"),
    AP_SOUTH_1("ap-south-1", "Asia Pacific (Mumbai)", GeoArea.APAC, "ap-southeast-1"),
    AP_SOUTH_2("ap-south-2", "Asia Pacific (Hyderabad)", GeoArea.APAC, "ap-south-1"),
    AP_SOUTHEAST_1("ap-southeast-1", "Asia Pacific (Singapore)", GeoArea.APAC, "ap-northeast-1"),
    AP_SOUTHEAST_2("ap-southeast-2", "Asia Pacific (Sydney)", GeoArea.APAC, "ap-northeast-1"),
    AP_SOUTHEAST_3("ap-southeast-3", "Asia Pacific (Jakarta)", GeoArea.APAC, "ap-southeast-1"),
    AP_SOUTHEAST_4("ap-southeast-4", "Asia Pacific (Melbourne)", GeoArea.APAC, "ap-southeast-2"),
    AP_NORTHEAST_1("ap-northeast-1", "Asia Pacific (Tokyo)", GeoArea.APAC, "ap-southeast-1"),
    AP_NORTHEAST_2("ap-northeast-2", "Asia Pacific (Seoul)", GeoArea.APAC, "ap-northeast-1"),
    AP_NORTHEAST_3("ap-northeast-3", "Asia Pacific (Osaka)", GeoArea.APAC, "ap-northeast-1"),

    // South America
    SA_EAST_1("sa-east-1", "South America (São Paulo)", GeoArea.SA, "us-east-1"),

    // Middle East
    ME_SOUTH_1("me-south-1", "Middle East (Bahrain)", GeoArea.ME, "eu-central-1"),
    ME_CENTRAL_1("me-central-1", "Middle East (UAE)", GeoArea.ME, "eu-central-1"),
    IL_CENTRAL_1("il-central-1", "Israel (Tel Aviv)", GeoArea.ME, "eu-central-1"),

    // Africa
    AF_SOUTH_1("af-south-1", "Africa (Cape Town)", GeoArea.AFRICA, "eu-west-1");

    /**
     * Geographic areas for data residency requirements.
     */
    public enum GeoArea {
        US("United States"),
        US_GOV("US Government"),
        CANADA("Canada"),
        EU("European Union"),
        APAC("Asia Pacific"),
        SA("South America"),
        ME("Middle East"),
        AFRICA("Africa");

        private final String displayName;

        GeoArea(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String code;
    private final String displayName;
    private final GeoArea geoArea;
    private final String drRegionCode;

    AwsRegion(String code, String displayName, GeoArea geoArea, String drRegionCode) {
        this.code = code;
        this.displayName = displayName;
        this.geoArea = geoArea;
        this.drRegionCode = drRegionCode;
    }

    /**
     * Gets the region code (e.g., "us-east-1").
     */
    public String code() {
        return code;
    }

    /**
     * Gets the display name (e.g., "US East (N. Virginia)").
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Gets the geographic area for data residency.
     */
    public GeoArea geoArea() {
        return geoArea;
    }

    /**
     * Gets the disaster recovery region code.
     */
    public String drRegionCode() {
        return drRegionCode;
    }

    /**
     * Gets the disaster recovery region enum.
     */
    public Optional<AwsRegion> drRegion() {
        return fromCode(drRegionCode);
    }

    /**
     * Finds a region by its code.
     *
     * @param code The region code (e.g., "us-east-1")
     * @return Optional containing the region, or empty if not found
     */
    public static Optional<AwsRegion> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (AwsRegion region : values()) {
            if (region.code.equals(code)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the secondary (DR) region for a given region code.
     *
     * @param regionCode The primary region code (e.g., "us-east-1")
     * @return Optional containing the secondary region code, or empty if not found
     */
    public static Optional<String> getSecondaryRegion(String regionCode) {
        return fromCode(regionCode).map(AwsRegion::drRegionCode);
    }

    /**
     * Checks if a region code is valid.
     *
     * @param code The region code to validate
     * @return true if the region code is valid
     */
    public static boolean isValidRegion(String code) {
        return fromCode(code).isPresent();
    }

    /**
     * Checks if two regions are in the same geographic area.
     * Useful for data residency compliance.
     *
     * @param region1 First region code
     * @param region2 Second region code
     * @return true if both regions are in the same geo area
     */
    public static boolean sameGeoArea(String region1, String region2) {
        Optional<AwsRegion> r1 = fromCode(region1);
        Optional<AwsRegion> r2 = fromCode(region2);
        if (r1.isEmpty() || r2.isEmpty()) {
            return false;
        }
        return r1.get().geoArea == r2.get().geoArea;
    }
}
