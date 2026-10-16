package com.cloudforge.core.interfaces;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The instance sizes CloudForge offers for EC2 and RDS. The deployment fields, the Manager and the
 * CLI all read these lists, so this is the one place to add a size.
 *
 * <p>RDS offers x86 and Graviton families. EC2 is x86 only, because the EC2 images are not
 * built for arm64.</p>
 */
public final class InstanceCatalog {
    private static final Map<String, List<String>> DATABASE_SIZES_BY_FAMILY = new LinkedHashMap<>();

    static {
        List<String> burstable = List.of("micro", "small", "medium", "large");
        List<String> general = List.of("large", "xlarge", "2xlarge");
        DATABASE_SIZES_BY_FAMILY.put("t3", burstable);
        DATABASE_SIZES_BY_FAMILY.put("m5", general);
        DATABASE_SIZES_BY_FAMILY.put("r5", general);
        DATABASE_SIZES_BY_FAMILY.put("t4g", burstable);
        DATABASE_SIZES_BY_FAMILY.put("m6g", general);
        DATABASE_SIZES_BY_FAMILY.put("m7g", general);
        DATABASE_SIZES_BY_FAMILY.put("r6g", general);
        DATABASE_SIZES_BY_FAMILY.put("r7g", general);
    }

    private static final List<String> EC2_INSTANCE_TYPES = List.of(
        "t3.micro", "t3.small", "t3.medium", "t3.large", "t3.xlarge",
        "t3a.micro", "t3a.small", "t3a.medium", "t3a.large",
        "m5.large", "m5.xlarge", "m5.2xlarge",
        "c5.large", "c5.xlarge", "c5.2xlarge");

    private InstanceCatalog() {
    }

    /** RDS instance classes, for example {@code db.t3.micro}. */
    public static List<String> databaseInstanceClasses() {
        List<String> classes = new ArrayList<>();
        DATABASE_SIZES_BY_FAMILY.forEach((family, sizes) ->
            sizes.forEach(size -> classes.add("db." + family + "." + size)));
        return List.copyOf(classes);
    }

    /** RDS instance families offered, for example {@code t3}. */
    public static List<String> databaseFamilies() {
        return List.copyOf(DATABASE_SIZES_BY_FAMILY.keySet());
    }

    /** EC2 instance types, for example {@code t3.micro}. */
    public static List<String> ec2InstanceTypes() {
        return EC2_INSTANCE_TYPES;
    }

    /**
     * The catalog values for a deployment field, or empty when the field is not catalog-backed.
     *
     * @param fieldName a {@code DeploymentConfig} field name
     */
    public static Optional<String[]> valuesFor(String fieldName) {
        return switch (fieldName) {
            case "databaseInstanceClass" -> Optional.of(databaseInstanceClasses().toArray(String[]::new));
            case "instanceType" -> Optional.of(ec2InstanceTypes().toArray(String[]::new));
            default -> Optional.empty();
        };
    }
}
