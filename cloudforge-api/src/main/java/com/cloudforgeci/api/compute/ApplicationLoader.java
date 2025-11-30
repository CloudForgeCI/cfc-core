package com.cloudforgeci.api.compute;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.annotation.ApplicationPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-discovery utility for ApplicationSpec implementations using Java ServiceLoader.
 *
 * <p>This class discovers all ApplicationSpec implementations registered via
 * META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec and provides
 * convenient access methods for deployment tools.</p>
 *
 * <p>Applications are discovered from:</p>
 * <ul>
 *   <li>Built-in applications in cloudforge-api module</li>
 *   <li>External plugins providing META-INF/services registration</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Discover all applications
 * Map<String, ApplicationSpec> apps = ApplicationLoader.discover();
 *
 * // Get applications by category
 * List<ApplicationSpec> cicdApps = ApplicationLoader.discoverByCategory("cicd");
 *
 * // Find specific application
 * Optional<ApplicationSpec> jenkins = ApplicationLoader.findById("jenkins");
 * }</pre>
 *
 * @since 3.0.0
 */
public final class ApplicationLoader {

    private ApplicationLoader() {
        // Utility class
    }

    /**
     * Discover all ApplicationSpec implementations via ServiceLoader.
     *
     * <p>Returns a map of application ID to ApplicationSpec instance. The application ID
     * is obtained from {@link ApplicationSpec#applicationId()}.</p>
     *
     * <p>Applications without {@link ApplicationPlugin} annotation are still included
     * but will have default metadata values.</p>
     *
     * @return map of application ID to ApplicationSpec (never null, may be empty)
     */
    public static Map<String, ApplicationSpec> discover() {
        ServiceLoader<ApplicationSpec> loader = ServiceLoader.load(ApplicationSpec.class);
        Map<String, ApplicationSpec> applications = new LinkedHashMap<>();

        for (ApplicationSpec spec : loader) {
            String id = spec.applicationId();
            if (applications.containsKey(id)) {
                System.err.println("WARNING: Duplicate application ID '" + id + "' - using first registration");
                continue;
            }
            applications.put(id, spec);
        }

        return applications;
    }

    /**
     * Discover all ApplicationSpec implementations as a list.
     *
     * <p>Applications are sorted alphabetically by application ID for consistent ordering.</p>
     *
     * @return list of ApplicationSpec instances (never null, may be empty)
     */
    public static List<ApplicationSpec> discoverAsList() {
        return discover().values().stream()
            .sorted(Comparator.comparing(ApplicationSpec::applicationId))
            .collect(Collectors.toList());
    }

    /**
     * Discover ApplicationSpec implementations filtered by category.
     *
     * <p>Categories are defined in the {@link ApplicationPlugin} annotation:</p>
     * <ul>
     *   <li>cicd - CI/CD platforms</li>
     *   <li>vcs - Version control systems</li>
     *   <li>monitoring - Monitoring and observability</li>
     *   <li>analytics - Business intelligence</li>
     *   <li>database - Databases and caching</li>
     *   <li>artifactregistry - Artifact repositories</li>
     *   <li>secrets - Secrets management</li>
     *   <li>collaboration - Team collaboration</li>
     * </ul>
     *
     * @param category the category to filter by
     * @return list of ApplicationSpec instances in this category
     */
    public static List<ApplicationSpec> discoverByCategory(String category) {
        return discoverAsList().stream()
            .filter(spec -> spec.category().equals(category))
            .collect(Collectors.toList());
    }

    /**
     * Discover all available categories.
     *
     * <p>Returns a sorted list of unique categories from all discovered applications.</p>
     *
     * @return sorted list of category names
     */
    public static List<String> discoverCategories() {
        return discoverAsList().stream()
            .map(ApplicationSpec::category)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Find a specific ApplicationSpec by application ID.
     *
     * @param applicationId the application identifier (e.g., "jenkins", "gitlab")
     * @return Optional containing the ApplicationSpec if found
     */
    public static Optional<ApplicationSpec> findById(String applicationId) {
        return Optional.ofNullable(discover().get(applicationId));
    }

    /**
     * Get applications grouped by category.
     *
     * <p>Returns a map where keys are category names and values are lists of
     * ApplicationSpec instances in that category.</p>
     *
     * @return map of category to list of applications
     */
    public static Map<String, List<ApplicationSpec>> discoverGroupedByCategory() {
        return discoverAsList().stream()
            .collect(Collectors.groupingBy(
                ApplicationSpec::category,
                TreeMap::new,
                Collectors.toList()
            ));
    }

    /**
     * Discover applications that support OIDC integration.
     *
     * @return list of ApplicationSpec instances supporting OIDC
     */
    public static List<ApplicationSpec> discoverOidcEnabled() {
        return discoverAsList().stream()
            .filter(ApplicationSpec::supportsOidcIntegration)
            .collect(Collectors.toList());
    }

    /**
     * Discover applications that support Fargate deployment.
     *
     * @return list of ApplicationSpec instances supporting Fargate
     */
    public static List<ApplicationSpec> discoverFargateSupported() {
        return discoverAsList().stream()
            .filter(ApplicationSpec::supportsFargate)
            .collect(Collectors.toList());
    }

    /**
     * Discover applications that support EC2 deployment.
     *
     * @return list of ApplicationSpec instances supporting EC2
     */
    public static List<ApplicationSpec> discoverEc2Supported() {
        return discoverAsList().stream()
            .filter(ApplicationSpec::supportsEc2)
            .collect(Collectors.toList());
    }

    /**
     * Print a formatted catalog of all discovered applications.
     *
     * <p>Useful for debugging and displaying available applications to users.</p>
     *
     * @return formatted string containing application catalog
     */
    public static String printCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("CloudForge Application Catalog\n");
        sb.append("==============================\n\n");

        Map<String, List<ApplicationSpec>> grouped = discoverGroupedByCategory();

        for (Map.Entry<String, List<ApplicationSpec>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<ApplicationSpec> apps = entry.getValue();

            sb.append(category.toUpperCase()).append(":\n");
            for (ApplicationSpec app : apps) {
                sb.append("  - ").append(app.applicationId())
                  .append(" (").append(app.displayName()).append(")");

                if (!app.description().isEmpty()) {
                    sb.append("\n    ").append(app.description());
                }

                List<String> features = new ArrayList<>();
                if (app.supportsOidcIntegration()) features.add("OIDC");
                if (app.supportsFargate()) features.add("Fargate");
                if (app.supportsEc2()) features.add("EC2");

                if (!features.isEmpty()) {
                    sb.append("\n    Supports: ").append(String.join(", ", features));
                }

                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
