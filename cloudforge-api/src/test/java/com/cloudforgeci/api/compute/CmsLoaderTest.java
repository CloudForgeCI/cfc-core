package com.cloudforgeci.api.compute;

import com.cloudforge.core.interfaces.CmsSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CmsLoaderTest {

    /** Every built-in CMS application id, from this module's own {@code META-INF/services}
     *  registration (see CmsLoaderTest's own package — nothing outside cloudforge-api is on this
     *  module's test classpath, so a real third-party plugin like cfc-testing's CraftCmsApplicationSpec
     *  never appears here). Asserting the exact set (not just {@code size() >= 19}) means a missing
     *  *or* duplicate registration is actually caught — a count-only assertion stays green either way
     *  once you're already above 19. */
    private static final Set<String> EXPECTED_BUILT_IN_IDS = Set.of(
        "wordpress", "woocommerce", "drupal", "joomla", "typo3", "concrete-cms", "october-cms",
        "magento", "prestashop", "opencart", "sylius", "bagisto", "phpbb", "flarum", "mybb",
        "suitecrm", "mediawiki", "moodle", "dolphin-una"
    );

    @Test
    void discoverReturnsExactlyTheExpectedBuiltInPlatforms() {
        Map<String, CmsSpec> platforms = CmsLoader.discover();
        assertNotNull(platforms);
        assertEquals(EXPECTED_BUILT_IN_IDS, platforms.keySet(),
            "Built-in CMS platform ids changed — update EXPECTED_BUILT_IN_IDS if this is intentional "
                + "(a new platform added, or one removed), otherwise this is a real regression");
    }

    @Test
    void discoverHasNoDuplicateIds() {
        Map<String, CmsSpec> platforms = CmsLoader.discover();
        // Map keying already deduplicates — verify each value's ID matches its key
        for (Map.Entry<String, CmsSpec> entry : platforms.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().applicationId(),
                "Map key must match applicationId() for: " + entry.getKey());
        }
    }

    @Test
    void discoverAsListIsSorted() {
        List<CmsSpec> list = CmsLoader.discoverAsList();
        assertNotNull(list);
        for (int i = 1; i < list.size(); i++) {
            String prev = list.get(i - 1).applicationId();
            String curr = list.get(i).applicationId();
            assertTrue(prev.compareTo(curr) <= 0,
                "List should be sorted alphabetically: '" + prev + "' before '" + curr + "'");
        }
    }

    @Test
    void findByIdWordPress() {
        Optional<CmsSpec> spec = CmsLoader.findById("wordpress");
        assertTrue(spec.isPresent(), "wordpress must be discoverable");
        assertEquals("wordpress", spec.get().applicationId());
    }

    @Test
    void findByIdMagento() {
        Optional<CmsSpec> spec = CmsLoader.findById("magento");
        assertTrue(spec.isPresent(), "magento must be discoverable");
        assertEquals("magento", spec.get().applicationId());
    }

    @Test
    void findByIdDolphinUna() {
        Optional<CmsSpec> spec = CmsLoader.findById("dolphin-una");
        assertTrue(spec.isPresent(), "dolphin-una must be discoverable");
    }

    @Test
    void findByIdUnknownReturnsEmpty() {
        Optional<CmsSpec> spec = CmsLoader.findById("nonexistent-cms-xyz");
        assertFalse(spec.isPresent());
    }

    @Test
    void discoverByCategoryReturnsCmsGroup() {
        List<CmsSpec> cms = CmsLoader.discoverByCategory("cms");
        assertNotNull(cms);
        assertFalse(cms.isEmpty(), "cms category must have entries");
        cms.forEach(s -> assertEquals("cms", s.cmsCategory()));
    }

    @Test
    void discoverByCategoryReturnsEcommerceGroup() {
        List<CmsSpec> ecommerce = CmsLoader.discoverByCategory("ecommerce");
        assertNotNull(ecommerce);
        assertFalse(ecommerce.isEmpty(), "ecommerce category must have entries");
        ecommerce.forEach(s -> assertEquals("ecommerce", s.cmsCategory()));
    }

    @Test
    void discoverEcommerceContainsMagento() {
        List<CmsSpec> ecommerce = CmsLoader.discoverEcommerce();
        assertTrue(ecommerce.stream().anyMatch(s -> "magento".equals(s.applicationId())),
            "magento must appear in ecommerce results");
    }

    @Test
    void discoverGroupedByCategoryContainsExpectedCategories() {
        Map<String, List<CmsSpec>> grouped = CmsLoader.discoverGroupedByCategory();
        assertNotNull(grouped);
        assertTrue(grouped.containsKey("cms"), "must have 'cms' category");
        assertTrue(grouped.containsKey("ecommerce"), "must have 'ecommerce' category");
        assertTrue(grouped.containsKey("forum"), "must have 'forum' category");
    }

    @Test
    void discoverOidcEnabledContainsWordPress() {
        List<CmsSpec> oidcEnabled = CmsLoader.discoverOidcEnabled();
        assertFalse(oidcEnabled.isEmpty());
        assertTrue(oidcEnabled.stream().anyMatch(s -> "wordpress".equals(s.applicationId())));
    }

    @Test
    void discoverS3MediaSupportedIsNonEmpty() {
        List<CmsSpec> s3 = CmsLoader.discoverS3MediaSupported();
        assertFalse(s3.isEmpty(), "At least some CMS specs must support S3 media");
        s3.forEach(s -> assertTrue(s.supportsS3MediaStorage()));
    }

    @Test
    void discoverObjectCacheSupportedIsNonEmpty() {
        List<CmsSpec> cached = CmsLoader.discoverObjectCacheSupported();
        assertFalse(cached.isEmpty(), "At least some CMS specs must support object cache");
        cached.forEach(s -> assertTrue(s.supportsObjectCache()));
    }

    @Test
    void discoverMultisiteSupportedIsNonEmpty() {
        List<CmsSpec> multisite = CmsLoader.discoverMultisiteSupported();
        assertFalse(multisite.isEmpty());
        multisite.forEach(s -> assertTrue(s.supportsMultisite()));
    }

    @Test
    void discoverByPhpVersionFiltersCorrectly() {
        List<CmsSpec> php82 = CmsLoader.discoverByPhpVersion("8.2");
        assertFalse(php82.isEmpty(), "Should find specs requiring PHP 8.2");
        php82.forEach(s -> assertEquals("8.2", s.phpVersion()));
    }

    @Test
    void discoverFargateSupportedIncludesAll() {
        List<CmsSpec> fargate = CmsLoader.discoverFargateSupported();
        assertEquals(CmsLoader.discoverAsList().size(), fargate.size(),
            "All built-in CMS specs must support Fargate");
    }

    @Test
    void discoverEc2SupportedIncludesAll() {
        List<CmsSpec> ec2 = CmsLoader.discoverEc2Supported();
        assertEquals(CmsLoader.discoverAsList().size(), ec2.size(),
            "All built-in CMS specs must support EC2");
    }

    @Test
    void discoverCategoriesReturnsSortedList() {
        List<String> categories = CmsLoader.discoverCategories();
        assertNotNull(categories);
        assertFalse(categories.isEmpty());
        for (int i = 1; i < categories.size(); i++) {
            assertTrue(categories.get(i - 1).compareTo(categories.get(i)) <= 0,
                "Categories must be sorted");
        }
    }

    @Test
    void printCatalogContainsExpectedContent() {
        String catalog = CmsLoader.printCatalog();
        assertNotNull(catalog);
        assertTrue(catalog.contains("CloudForge CMS Platform Catalog"));
        assertTrue(catalog.contains("wordpress"));
        assertTrue(catalog.contains("magento"));
    }
}
