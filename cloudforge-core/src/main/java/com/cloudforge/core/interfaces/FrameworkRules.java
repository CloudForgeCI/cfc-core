package com.cloudforge.core.interfaces;

import com.cloudforge.core.annotation.ComplianceFramework;

/**
 * Interface for pluggable compliance framework validators.
 *
 * <p>Implementations of this interface define compliance validation rules for specific
 * frameworks (HIPAA, PCI-DSS, SOC2, etc.) or cross-framework concerns (key management,
 * database security, monitoring).</p>
 *
 * <p>This interface uses a generic type parameter to avoid coupling the core module
 * to specific implementation details. Concrete implementations in cloudforge-api
 * will use SystemContext as the type parameter.</p>
 *
 * <h2>Implementation Pattern:</h2>
 * <pre>{@code
 * @ComplianceFramework(value = "FEDRAMP", priority = 50)
 * public final class FedRampRules implements FrameworkRules<SystemContext> {
 *     @Override
 *     public void install(SystemContext ctx) {
 *         ctx.getNode().addValidation(() -> {
 *             List<ComplianceRule> rules = new ArrayList<>();
 *
 *             // Add validation rules
 *             rules.add(ComplianceRule.pass("FEDRAMP-AC-2", "Account Management"));
 *
 *             // Return failures
 *             return rules.stream()
 *                 .filter(r -> !r.passed())
 *                 .map(ComplianceRule::toErrorString)
 *                 .flatMap(Optional::stream)
 *                 .toList();
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h2>Discovery:</h2>
 * <p>Framework implementations are automatically discovered via the {@link ComplianceFramework}
 * annotation and loaded by the CloudForge compliance system.</p>
 *
 * @param <T> the context type (e.g., SystemContext in cloudforge-api)
 * @since 3.0.0
 */
public interface FrameworkRules<T> {
    /**
     * Install compliance validation rules into the CDK construct tree.
     *
     * <p>This method is called during CDK synthesis to register validation rules
     * for the compliance framework. Implementations should use
     * {@code ctx.getNode().addValidation()} to add CDK validations.</p>
     *
     * @param ctx the system context containing deployment configuration and CDK stack
     */
    void install(T ctx);

    /**
     * Get the framework identifier from the {@link ComplianceFramework} annotation.
     *
     * @return the framework identifier (e.g., "HIPAA", "PCI-DSS")
     */
    default String frameworkId() {
        ComplianceFramework annotation = getClass().getAnnotation(ComplianceFramework.class);
        if (annotation == null) {
            throw new IllegalStateException(
                getClass().getSimpleName() + " must be annotated with @ComplianceFramework"
            );
        }
        return annotation.value();
    }

    /**
     * Get the human-readable display name for this framework.
     *
     * @return the display name, defaulting to {@link #frameworkId()} if not specified
     */
    default String displayName() {
        ComplianceFramework annotation = getClass().getAnnotation(ComplianceFramework.class);
        if (annotation == null) {
            return getClass().getSimpleName();
        }
        String displayName = annotation.displayName();
        return displayName.isEmpty() ? annotation.value() : displayName;
    }

    /**
     * Get the framework description.
     *
     * @return the framework description
     */
    default String description() {
        ComplianceFramework annotation = getClass().getAnnotation(ComplianceFramework.class);
        if (annotation == null) {
            return "";
        }
        return annotation.description();
    }

    /**
     * Get the load priority for this framework.
     *
     * @return the priority (lower values load first)
     */
    default int priority() {
        ComplianceFramework annotation = getClass().getAnnotation(ComplianceFramework.class);
        if (annotation == null) {
            return 100; // Default priority
        }
        return annotation.priority();
    }

    /**
     * Check if this framework should always be loaded.
     *
     * @return true if this framework loads regardless of configuration
     */
    default boolean alwaysLoad() {
        ComplianceFramework annotation = getClass().getAnnotation(ComplianceFramework.class);
        if (annotation == null) {
            return false;
        }
        return annotation.alwaysLoad();
    }
}
