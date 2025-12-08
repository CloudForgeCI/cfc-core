package com.cloudforge.core.config;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.FrameworkRules;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves default values for configuration fields using layered priority.
 *
 * <p>Default value priority (highest to lowest):</p>
 * <ol>
 *   <li>User override in deployment-context.json (not handled here)</li>
 *   <li>FrameworkRules requirements (compliance-driven defaults)</li>
 *   <li>ApplicationSpec defaults (application-specific defaults via {@link ConfigFieldInfo#defaultFrom()})</li>
 *   <li>ConfigField annotation default (system-wide defaults)</li>
 * </ol>
 *
 * <h2>Convention-Based Lookup</h2>
 * <p>The {@code defaultFrom} attribute in {@link com.cloudforge.core.annotation.ConfigField}
 * specifies a method name or chain to call on the ApplicationSpec:</p>
 *
 * <pre>{@code
 * @ConfigField(
 *     displayName = "CPU Units",
 *     defaultFrom = "defaultCpu"  // Calls appSpec.defaultCpu()
 * )
 * public Integer cpu;
 *
 * @ConfigField(
 *     displayName = "Database Engine",
 *     defaultFrom = "databaseRequirement().engine"  // Chained: appSpec.databaseRequirement().engine()
 * )
 * public String databaseEngine;
 * }</pre>
 *
 * @since 3.0.0
 */
public class DefaultValueResolver {

    private static final Logger LOG = Logger.getLogger(DefaultValueResolver.class.getName());

    /**
     * Resolves the default value for a field using layered priority.
     *
     * @param fieldInfo field metadata
     * @param appSpec application spec for application-specific defaults
     * @param frameworks active compliance frameworks
     * @return resolved default value, or null if no default available
     */
    public static Object resolve(ConfigFieldInfo fieldInfo, ApplicationSpec appSpec, List<FrameworkRules<?>> frameworks) {
        // Layer 1: Try FrameworkRules defaults (highest priority after user)
        if (frameworks != null && !frameworks.isEmpty()) {
            for (FrameworkRules framework : frameworks) {
                Object frameworkDefault = resolveFromFramework(fieldInfo, framework);
                if (frameworkDefault != null) {
                    LOG.fine("Field '" + fieldInfo.fieldName() + "' default from " +
                        framework.getClass().getSimpleName() + ": " + frameworkDefault);
                    return frameworkDefault;
                }
            }
        }

        // Layer 2: Try ApplicationSpec defaults (via defaultFrom annotation)
        if (!fieldInfo.defaultFrom().isEmpty() && appSpec != null) {
            Object appSpecDefault = resolveFromApplicationSpec(fieldInfo, appSpec);
            if (appSpecDefault != null) {
                LOG.fine("Field '" + fieldInfo.fieldName() + "' default from ApplicationSpec: " + appSpecDefault);
                return appSpecDefault;
            }
        }

        // Layer 3: No custom default found - caller should use field's intrinsic default
        return null;
    }

    /**
     * Resolves default from ApplicationSpec using convention-based method lookup.
     *
     * <p>Supports simple method calls (e.g., "defaultCpu") and chained calls
     * (e.g., "databaseRequirement().engine").</p>
     */
    private static Object resolveFromApplicationSpec(ConfigFieldInfo fieldInfo, ApplicationSpec appSpec) {
        String expression = fieldInfo.defaultFrom();

        try {
            // Handle chained method calls: "databaseRequirement().engine"
            if (expression.contains(".")) {
                return resolveChainedMethod(appSpec, expression);
            }

            // Simple method call: "defaultCpu"
            Method method = appSpec.getClass().getMethod(expression);
            return method.invoke(appSpec);

        } catch (NoSuchMethodException e) {
            LOG.warning("Method '" + expression + "' not found on " + appSpec.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to resolve default from ApplicationSpec: " + expression, e);
            return null;
        }
    }

    /**
     * Resolves chained method calls like "databaseRequirement().engine".
     */
    private static Object resolveChainedMethod(Object target, String expression) throws Exception {
        String[] parts = expression.split("\\.", 2);
        String methodName = parts[0].replace("()", "");

        // Invoke first method
        Method method = target.getClass().getMethod(methodName);
        Object result = method.invoke(target);

        if (result == null) {
            return null;
        }

        // If there are more parts, continue the chain
        if (parts.length > 1) {
            return resolveChainedMethod(result, parts[1]);
        }

        return result;
    }

    /**
     * Resolves default from FrameworkRules.
     *
     * <p>This is a placeholder for future implementation. FrameworkRules will provide
     * a method like {@code getDefaultForField(String fieldName)} that returns compliance-driven
     * defaults.</p>
     */
    private static Object resolveFromFramework(ConfigFieldInfo fieldInfo, FrameworkRules<?> framework) {
        // TODO: Implement FrameworkRules.getDefaultForField() in Phase 2
        // For now, frameworks can only apply defaults via applyDefaults(DeploymentConfig)
        return null;
    }

    /**
     * Resolves default with fallback to field's annotated default.
     *
     * <p>Convenience method that returns the field's current value as fallback.</p>
     *
     * @param fieldInfo field metadata
     * @param appSpec application spec
     * @param frameworks active frameworks
     * @param config current config (for reading current field value as fallback)
     * @return resolved default value, or current field value if no default available
     */
    public static Object resolveWithFallback(ConfigFieldInfo fieldInfo, ApplicationSpec appSpec,
                                            List<FrameworkRules<?>> frameworks, Object config) {
        Object resolved = resolve(fieldInfo, appSpec, frameworks);
        if (resolved != null) {
            return resolved;
        }

        // Fallback to current field value
        return fieldInfo.getValue(config);
    }
}
