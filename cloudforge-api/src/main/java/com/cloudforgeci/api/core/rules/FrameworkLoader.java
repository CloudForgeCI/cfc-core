package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Discovers and loads compliance framework validators using Java ServiceLoader.
 *
 * <p>This class scans the classpath for implementations of {@link FrameworkRules}
 * annotated with {@link ComplianceFramework} and provides them in priority order
 * for installation into the CDK construct tree.</p>
 *
 * <h2>Discovery Mechanism (v2.0):</h2>
 * <p>Frameworks are discovered via Java's {@link ServiceLoader} by registering in:<br>
 * {@code META-INF/services/com.cloudforge.core.interfaces.FrameworkRules}</p>
 *
 * <p>Each framework must:</p>
 * <ul>
 *   <li>Implement {@link FrameworkRules}&lt;SystemContext&gt;</li>
 *   <li>Be annotated with {@link ComplianceFramework} to provide metadata</li>
 *   <li>Have a no-args public constructor</li>
 * </ul>
 *
 * <h2>Example Framework:</h2>
 * <pre>{@code
 * @ComplianceFramework(
 *     value = "HIPAA",
 *     priority = 10,
 *     displayName = "HIPAA Security Rule",
 *     description = "Validates HIPAA requirements"
 * )
 * public class HipaaRules implements FrameworkRules<SystemContext> {
 *     @Override
 *     public void install(SystemContext ctx) {
 *         // Validation logic
 *     }
 * }
 * }</pre>
 *
 * @since 3.1.0
 */
public final class FrameworkLoader {
    private static final Logger LOG = Logger.getLogger(FrameworkLoader.class.getName());

    private FrameworkLoader() {}

    /**
     * Discover all compliance frameworks available on the classpath.
     *
     * <p>Returns frameworks in priority order (lowest priority value first).
     * Frameworks with the same priority are ordered alphabetically by framework ID.</p>
     *
     * @return list of discovered frameworks sorted by priority
     */
    public static List<FrameworkRules<SystemContext>> discover() {
        // Load all frameworks via ServiceLoader (v2.0 plugin system)
        List<FrameworkRules<SystemContext>> frameworks = loadViaServiceLoader();

        // Sort by priority (lower values first), then by framework ID alphabetically
        return frameworks.stream()
            .sorted(Comparator
                .comparingInt(FrameworkRules<SystemContext>::priority)
                .thenComparing(FrameworkRules::frameworkId))
            .collect(Collectors.toList());
    }

    /**
     * Load all frameworks via Java ServiceLoader (v2.0 plugin system).
     *
     * <p>Frameworks register themselves by creating a file:<br>
     * {@code META-INF/services/com.cloudforge.core.interfaces.FrameworkRules}<br>
     * containing the fully-qualified class name of their implementation.</p>
     *
     * <p>Each framework must implement {@link FrameworkRules} and be annotated with
     * {@link ComplianceFramework} to provide metadata (framework ID, priority, display name).</p>
     *
     * @return list of discovered frameworks loaded via ServiceLoader
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<FrameworkRules<SystemContext>> loadViaServiceLoader() {
        List<FrameworkRules<SystemContext>> frameworks = new ArrayList<>();

        try {
            // ServiceLoader cannot handle parameterized types directly, so we load raw type
            ServiceLoader<FrameworkRules> loader = ServiceLoader.load(FrameworkRules.class);
            for (FrameworkRules framework : loader) {
                frameworks.add((FrameworkRules<SystemContext>) framework);
                LOG.info("Discovered framework via ServiceLoader: " + framework.frameworkId() +
                        " (priority: " + framework.priority() + ", alwaysLoad: " + framework.alwaysLoad() + ")");
            }
        } catch (ServiceConfigurationError e) {
            LOG.warning("Failed to load frameworks via ServiceLoader: " + e.getMessage());
        }

        return frameworks;
    }
}
