package com.cloudforgeci.api.compute;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.annotation.CmsPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-discovery utility for CmsSpec implementations using Java ServiceLoader.
 *
 * <p>This class discovers all CmsSpec implementations registered via
 * META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec and provides
 * convenient access methods for deployment tools.</p>
 *
 * <p>CMS platforms are discovered from:</p>
 * <ul>
 *   <li>Built-in CMS applications in cloudforge-api module</li>
 *   <li>External plugins providing META-INF/services registration</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Discover all CMS platforms
 * Map<String, CmsSpec> platforms = CmsLoader.discover();
 *
 * // Get CMS by category
 * List<CmsSpec> ecommerceApps = CmsLoader.discoverByCategory("ecommerce");
 * List<CmsSpec> socialApps = CmsLoader.discoverByCategory("social");
 *
 * // Find specific CMS
 * Optional<CmsSpec> wordpress = CmsLoader.findById("wordpress");
 *
 * // Get all e-commerce platforms
 * List<CmsSpec> ecommerce = CmsLoader.discoverEcommerce();
 * }</pre>
 *
 * @since 3.1.0
 * @see CmsSpec
 * @see CmsPlugin
 */
public final class CmsLoader {

    private CmsLoader() {
        // Utility class
    }

    /**
     * Discover all CmsSpec implementations via ServiceLoader.
     *
     * <p>Returns a map of CMS ID to CmsSpec instance. The CMS ID
     * is obtained from {@link CmsSpec#applicationId()}.</p>
     *
     * <p>CmsSpec implementations are discovered from the ApplicationSpec
     * ServiceLoader registration and filtered to include only those
     * that implement CmsSpec.</p>
     *
     * @return map of CMS ID to CmsSpec (never null, may be empty)
     */
    public static Map<String, CmsSpec> discover() {
        ServiceLoader<ApplicationSpec> loader = ServiceLoader.load(ApplicationSpec.class);
        Map<String, CmsSpec> platforms = new LinkedHashMap<>();

        for (ApplicationSpec spec : loader) {
            if (spec instanceof CmsSpec cmsSpec) {
                String id = cmsSpec.applicationId();
                if (platforms.containsKey(id)) {
                    System.err.println("WARNING: Duplicate CMS ID '" + id + "' - using first registration");
                    continue;
                }
                platforms.put(id, cmsSpec);
            }
        }

        return platforms;
    }

    /**
     * Discover all CmsSpec implementations as a list.
     *
     * <p>CMS platforms are sorted alphabetically by application ID for consistent ordering.</p>
     *
     * @return list of CmsSpec instances (never null, may be empty)
     */
    public static List<CmsSpec> discoverAsList() {
        return discover().values().stream()
            .sorted(Comparator.comparing(CmsSpec::applicationId))
            .collect(Collectors.toList());
    }

    /**
     * Discover CmsSpec implementations filtered by CMS category.
     *
     * <p>CMS categories include:</p>
     * <ul>
     *   <li>cms - Content management systems (WordPress, Joomla, Drupal)</li>
     *   <li>ecommerce - E-commerce platforms (WooCommerce, Magento, PrestaShop)</li>
     *   <li>social - Social networking (Dolphin/UNA, HumHub, Elgg)</li>
     *   <li>forum - Forum software (phpBB, Discourse, Flarum)</li>
     *   <li>wiki - Wiki platforms (MediaWiki, DokuWiki, BookStack)</li>
     *   <li>lms - Learning management (Moodle, Canvas, Chamilo)</li>
     * </ul>
     *
     * @param category the CMS category to filter by
     * @return list of CmsSpec instances in this category
     */
    public static List<CmsSpec> discoverByCategory(String category) {
        return discoverAsList().stream()
            .filter(spec -> spec.cmsCategory().equals(category))
            .collect(Collectors.toList());
    }

    /**
     * Discover all content management systems (category: "cms").
     *
     * <p>Includes: WordPress, Joomla, Drupal, TYPO3, Concrete5, etc.</p>
     *
     * @return list of CMS platforms
     */
    public static List<CmsSpec> discoverCms() {
        return discoverByCategory("cms");
    }

    /**
     * Discover all e-commerce platforms (category: "ecommerce").
     *
     * <p>Includes: WooCommerce, Magento, PrestaShop, OpenCart, Shopware, etc.</p>
     *
     * @return list of e-commerce platforms
     */
    public static List<CmsSpec> discoverEcommerce() {
        return discoverByCategory("ecommerce");
    }

    /**
     * Discover all social networking platforms (category: "social").
     *
     * <p>Includes: Dolphin/UNA, HumHub, Elgg, phpFox, etc.</p>
     *
     * @return list of social networking platforms
     */
    public static List<CmsSpec> discoverSocial() {
        return discoverByCategory("social");
    }

    /**
     * Discover all forum platforms (category: "forum").
     *
     * <p>Includes: phpBB, Discourse, Flarum, MyBB, XenForo, etc.</p>
     *
     * @return list of forum platforms
     */
    public static List<CmsSpec> discoverForums() {
        return discoverByCategory("forum");
    }

    /**
     * Discover all wiki platforms (category: "wiki").
     *
     * <p>Includes: MediaWiki, DokuWiki, BookStack, Wiki.js, etc.</p>
     *
     * @return list of wiki platforms
     */
    public static List<CmsSpec> discoverWikis() {
        return discoverByCategory("wiki");
    }

    /**
     * Discover all learning management systems (category: "lms").
     *
     * <p>Includes: Moodle, Canvas, Chamilo, ILIAS, etc.</p>
     *
     * @return list of LMS platforms
     */
    public static List<CmsSpec> discoverLms() {
        return discoverByCategory("lms");
    }

    /**
     * Discover all available CMS categories.
     *
     * <p>Returns a sorted list of unique categories from all discovered platforms.</p>
     *
     * @return sorted list of category names
     */
    public static List<String> discoverCategories() {
        return discoverAsList().stream()
            .map(CmsSpec::cmsCategory)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Find a specific CmsSpec by CMS ID.
     *
     * @param cmsId the CMS identifier (e.g., "wordpress", "magento", "dolphin")
     * @return Optional containing the CmsSpec if found
     */
    public static Optional<CmsSpec> findById(String cmsId) {
        return Optional.ofNullable(discover().get(cmsId));
    }

    /**
     * Get CMS platforms grouped by category.
     *
     * <p>Returns a map where keys are category names and values are lists of
     * CmsSpec instances in that category.</p>
     *
     * @return map of category to list of CMS platforms
     */
    public static Map<String, List<CmsSpec>> discoverGroupedByCategory() {
        return discoverAsList().stream()
            .collect(Collectors.groupingBy(
                CmsSpec::cmsCategory,
                TreeMap::new,
                Collectors.toList()
            ));
    }

    /**
     * Discover CMS platforms that support OIDC integration.
     *
     * @return list of CmsSpec instances supporting OIDC
     */
    public static List<CmsSpec> discoverOidcEnabled() {
        return discoverAsList().stream()
            .filter(CmsSpec::supportsOidcIntegration)
            .collect(Collectors.toList());
    }

    /**
     * Discover CMS platforms that support S3 media storage.
     *
     * @return list of CmsSpec instances supporting S3 media
     */
    public static List<CmsSpec> discoverS3MediaSupported() {
        return discoverAsList().stream()
            .filter(CmsSpec::supportsS3MediaStorage)
            .collect(Collectors.toList());
    }

    /**
     * Discover CMS platforms that support object caching (Redis/Memcached).
     *
     * @return list of CmsSpec instances supporting object caching
     */
    public static List<CmsSpec> discoverObjectCacheSupported() {
        return discoverAsList().stream()
            .filter(CmsSpec::supportsObjectCache)
            .collect(Collectors.toList());
    }

    /**
     * Discover CMS platforms that support multi-site/multi-store.
     *
     * @return list of CmsSpec instances supporting multi-site
     */
    public static List<CmsSpec> discoverMultisiteSupported() {
        return discoverAsList().stream()
            .filter(CmsSpec::supportsMultisite)
            .collect(Collectors.toList());
    }

    /**
     * Discover CMS platforms by PHP version requirement.
     *
     * @param phpVersion the PHP version to filter by (e.g., "8.2", "8.1")
     * @return list of CmsSpec instances requiring this PHP version
     */
    public static List<CmsSpec> discoverByPhpVersion(String phpVersion) {
        return discoverAsList().stream()
            .filter(spec -> spec.phpVersion().equals(phpVersion))
            .collect(Collectors.toList());
    }

    /**
     * Discover CMS platforms that support Fargate deployment.
     *
     * @return list of CmsSpec instances supporting Fargate
     */
    public static List<CmsSpec> discoverFargateSupported() {
        return discoverAsList().stream()
            .filter(CmsSpec::supportsFargate)
            .collect(Collectors.toList());
    }

    /**
     * Discover CMS platforms that support EC2 deployment.
     *
     * @return list of CmsSpec instances supporting EC2
     */
    public static List<CmsSpec> discoverEc2Supported() {
        return discoverAsList().stream()
            .filter(CmsSpec::supportsEc2)
            .collect(Collectors.toList());
    }

    /**
     * Print a formatted catalog of all discovered CMS platforms.
     *
     * <p>Useful for debugging and displaying available platforms to users.</p>
     *
     * @return formatted string containing CMS catalog
     */
    public static String printCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("CloudForge CMS Platform Catalog\n");
        sb.append("===============================\n\n");

        Map<String, List<CmsSpec>> grouped = discoverGroupedByCategory();

        for (Map.Entry<String, List<CmsSpec>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<CmsSpec> platforms = entry.getValue();

            sb.append(categoryDisplayName(category)).append(":\n");
            for (CmsSpec cms : platforms) {
                sb.append("  - ").append(cms.applicationId())
                  .append(" (").append(cms.displayName()).append(")");

                if (!cms.description().isEmpty()) {
                    sb.append("\n    ").append(cms.description());
                }

                sb.append("\n    PHP ").append(cms.phpVersion());

                List<String> features = new ArrayList<>();
                if (cms.supportsOidcIntegration()) features.add("OIDC");
                if (cms.supportsS3MediaStorage()) features.add("S3 Media");
                if (cms.supportsObjectCache()) features.add("Redis Cache");
                if (cms.supportsMultisite()) features.add("Multi-site");
                if (cms.supportsFargate()) features.add("Fargate");
                if (cms.supportsEc2()) features.add("EC2");

                if (!features.isEmpty()) {
                    sb.append(" | ").append(String.join(", ", features));
                }

                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Get display name for a category.
     *
     * @param category the category key
     * @return human-readable category name
     */
    private static String categoryDisplayName(String category) {
        return switch (category) {
            case "cms" -> "CONTENT MANAGEMENT SYSTEMS";
            case "ecommerce" -> "E-COMMERCE PLATFORMS";
            case "social" -> "SOCIAL NETWORKING";
            case "forum" -> "FORUM SOFTWARE";
            case "wiki" -> "WIKI PLATFORMS";
            case "lms" -> "LEARNING MANAGEMENT SYSTEMS";
            default -> category.toUpperCase();
        };
    }
}
