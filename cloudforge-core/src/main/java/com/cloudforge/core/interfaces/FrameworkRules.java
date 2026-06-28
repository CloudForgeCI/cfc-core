package com.cloudforge.core.interfaces;

import com.cloudforge.core.annotation.ComplianceFramework;

import java.util.Collections;
import java.util.Map;

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
 *
 *     @Override
 *     public Map<String, Object> getRequiredConfiguration() {
 *         return Map.of(
 *             "logRetentionDays", 2190,  // 6 years
 *             "guardDutyEnabled", true,
 *             "macieEnabled", true
 *         );
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

    /**
     * Get the minimum required deployment configuration for this compliance framework.
     *
     * <p>This method returns the framework's baseline security requirements as
     * DeploymentContext overrides. These values are applied as defaults when the
     * framework is enabled, but can be overridden by explicit user configuration.</p>
     *
     * <p><b>Precedence order:</b></p>
     * <ol>
     *   <li>User-provided explicit configuration (cdk.json)</li>
     *   <li>Framework-required configuration (this method)</li>
     *   <li>Security profile defaults</li>
     * </ol>
     *
     * <p><b>Example implementation:</b></p>
     * <pre>{@code
     * @Override
     * public Map<String, Object> getRequiredConfiguration() {
     *     return Map.of(
     *         "logRetentionDays", 2190,      // HIPAA: 6 years minimum
     *         "guardDutyEnabled", true,       // HIPAA: threat detection required
     *         "macieEnabled", true,           // HIPAA: PHI discovery required
     *         "securityHubEnabled", true,     // HIPAA: centralized monitoring
     *         "inspectorEnabled", true        // HIPAA: vulnerability scanning
     *     );
     * }
     * }</pre>
     *
     * <p><b>Supported configuration keys:</b></p>
     * <ul>
     *   <li>{@code logRetentionDays} - CloudWatch log retention (Integer)</li>
     *   <li>{@code guardDutyEnabled} - AWS GuardDuty threat detection (Boolean)</li>
     *   <li>{@code macieEnabled} - Amazon Macie PII/PHI discovery (Boolean)</li>
     *   <li>{@code securityHubEnabled} - AWS Security Hub (Boolean)</li>
     *   <li>{@code inspectorEnabled} - Amazon Inspector vulnerability scanning (Boolean)</li>
     *   <li>{@code cloudTrailEnabled} - AWS CloudTrail audit logging (Boolean)</li>
     *   <li>{@code wafEnabled} - AWS WAF protection (Boolean)</li>
     *   <li>{@code albAccessLogging} - ALB access logs to S3 (Boolean)</li>
     * </ul>
     *
     * @return map of configuration keys to required values, empty map if no requirements
     * @since 3.1.0
     */
    default Map<String, Object> getRequiredConfiguration() {
        return Collections.emptyMap();
    }
}
