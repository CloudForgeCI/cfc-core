package com.cloudforgeci.api.core;

import com.cloudforge.core.interfaces.InstanceCatalog;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@link InstanceCatalog} in step with the CDK library. Every size the catalog offers must exist
 * in the CDK, and when the CDK knows a newer generation of an offered family the catalog has to
 * either offer it or record that it is deliberately not offered.
 */
class InstanceCatalogCdkDriftTest {
    /**
     * Newer generations the CDK knows that CloudForge does not offer yet; a decision, not an
     * oversight. Move a family into {@link InstanceCatalog} once it has been deployed with.
     */
    private static final Set<String> NOT_OFFERED_YET = Set.of(
        "c6i", "c7i", "m6i", "m7i", "r6i", "r7i", "m8g", "r8g");

    private static final Pattern FAMILY = Pattern.compile("^([a-z])(\\d+)([a-z]*)$");

    private static String sizeName(InstanceSize size) {
        Matcher xlarge = Pattern.compile("^XLARGE(\\d+)$").matcher(size.name());
        return xlarge.matches() ? xlarge.group(1) + "xlarge" : size.name().toLowerCase(Locale.ROOT);
    }

    private static Set<String> cdkFamilies() {
        return Arrays.stream(InstanceClass.values())
            .map(family -> family.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    private static Set<String> cdkSizes() {
        return Arrays.stream(InstanceSize.values())
            .map(InstanceCatalogCdkDriftTest::sizeName)
            .collect(Collectors.toSet());
    }

    @Test
    void everyOfferedSizeExistsInTheCdk() {
        Set<String> families = cdkFamilies();
        Set<String> sizes = cdkSizes();
        for (String instanceClass : InstanceCatalog.databaseInstanceClasses()) {
            String[] parts = instanceClass.split("\\.");
            assertTrue(families.contains(parts[1]), instanceClass + ": the CDK has no family " + parts[1]);
            assertTrue(sizes.contains(parts[2]), instanceClass + ": the CDK has no size " + parts[2]);
        }
        for (String type : InstanceCatalog.ec2InstanceTypes()) {
            String[] parts = type.split("\\.");
            assertTrue(families.contains(parts[0]), type + ": the CDK has no family " + parts[0]);
            assertTrue(sizes.contains(parts[1]), type + ": the CDK has no size " + parts[1]);
        }
    }

    @Test
    void noOfferedFamilyHasANewerGenerationInTheCdkThatIsNeitherOfferedNorRecorded() {
        Set<String> offered = new TreeSet<>(InstanceCatalog.databaseFamilies());
        InstanceCatalog.ec2InstanceTypes().forEach(type -> offered.add(type.split("\\.")[0]));

        Set<String> missing = new TreeSet<>();
        for (String family : offered) {
            Matcher own = FAMILY.matcher(family);
            assertTrue(own.matches(), "unrecognised family name " + family);
            int generation = Integer.parseInt(own.group(2));
            for (String known : cdkFamilies()) {
                Matcher other = FAMILY.matcher(known);
                if (other.matches() && other.group(1).equals(own.group(1))
                        && lineage(other.group(3)).equals(lineage(own.group(3)))
                        && Integer.parseInt(other.group(2)) > generation
                        && !offered.contains(known) && !NOT_OFFERED_YET.contains(known)) {
                    missing.add(known + " (newer than offered " + family + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "The CDK knows newer instance generations: " + missing
            + ". Offer them in InstanceCatalog, or add them to NOT_OFFERED_YET in this test.");
    }

    /** Intel's {@code i} suffix is the same lineage as no suffix: m5, m6i and m7i are one line. */
    private static String lineage(String suffix) {
        return "i".equals(suffix) ? "" : suffix;
    }

    @Test
    void theNotOfferedListHoldsOnlyRealCdkFamilies() {
        List<String> unknown = NOT_OFFERED_YET.stream().filter(family -> !cdkFamilies().contains(family)).toList();
        assertTrue(unknown.isEmpty(), "NOT_OFFERED_YET names families the CDK does not know: " + unknown);
    }
}
