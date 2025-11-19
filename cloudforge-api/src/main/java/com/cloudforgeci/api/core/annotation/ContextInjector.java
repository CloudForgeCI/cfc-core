package com.cloudforgeci.api.core.annotation;

import software.constructs.Construct;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Standalone utility for injecting context values into annotated fields.
 *
 * <p>This allows any class (not just BaseFactory subclasses) to use @DeploymentContext,
 * @SystemContext, and @SecurityProfileConfiguration annotations for dependency injection.</p>
 *
 * <h2>Slot Auto-Extraction</h2>
 * <p>When a @SystemContext field references a Slot, the injector automatically extracts
 * the value from the Slot. No need for manual {@code .get().orElseThrow()} calls!</p>
 *
 * <p>Usage examples:</p>
 * <pre>{@code
 * public class MyCustomClass {
 *     // Simple context value injection
 *     @DeploymentContext("region")
 *     private String region;
 *
 *     // Direct field injection (non-Slot)
 *     @SystemContext("security")
 *     private SecurityProfile security;
 *
 *     // Automatic Slot extraction - no .get().orElseThrow() needed!
 *     @SystemContext("alb")
 *     private IApplicationLoadBalancer alb;  // Auto-extracted from ctx.alb Slot
 *
 *     @SystemContext("vpc")
 *     private Vpc vpc;  // Auto-extracted from ctx.vpc Slot
 *
 *     public MyCustomClass(Construct scope) {
 *         // Inject all annotated fields
 *         ContextInjector.inject(this, scope);
 *     }
 * }
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>If a Slot is empty when accessed, a clear error message indicates which resource
 * is missing and reminds you to create it first.</p>
 */
public final class ContextInjector {

    private ContextInjector() {
        // Utility class - prevent instantiation
    }

    /**
     * Inject context values into all annotated fields of the given object.
     *
     * @param target The object whose fields should be injected
     * @param scope The CDK construct scope to retrieve context from
     */
    public static void inject(Object target, Construct scope) {
        if (target == null) {
            throw new IllegalArgumentException("Injection target cannot be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Construct scope cannot be null");
        }

        com.cloudforgeci.api.core.SystemContext systemContext =
            com.cloudforgeci.api.core.SystemContext.of(scope);
        com.cloudforgeci.api.core.DeploymentContext deploymentContext =
            com.cloudforgeci.api.core.DeploymentContext.from(scope);

        inject(target, systemContext, deploymentContext);
    }

    /**
     * Inject context values into all annotated fields of the given object.
     *
     * @param target The object whose fields should be injected
     * @param systemContext The SystemContext to inject from
     * @param deploymentContext The DeploymentContext to inject from
     */
    public static void inject(Object target,
                             com.cloudforgeci.api.core.SystemContext systemContext,
                             com.cloudforgeci.api.core.DeploymentContext deploymentContext) {

        if (target == null) {
            throw new IllegalArgumentException("Injection target cannot be null");
        }
        if (systemContext == null) {
            throw new IllegalArgumentException("SystemContext cannot be null");
        }
        if (deploymentContext == null) {
            throw new IllegalArgumentException("DeploymentContext cannot be null");
        }

        // Get security profile configuration
        com.cloudforgeci.api.interfaces.SecurityProfileConfiguration securityConfig =
            getSecurityProfileConfiguration(systemContext.security, deploymentContext);

        // Inject fields from all classes in the hierarchy
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    // Handle @DeploymentContext annotation
                    DeploymentContext deploymentContextAnnotation =
                        field.getAnnotation(DeploymentContext.class);
                    if (deploymentContextAnnotation != null && !deploymentContextAnnotation.value().isEmpty()) {
                        Object value = extractValueFromContext(deploymentContext, deploymentContextAnnotation.value());
                        setFieldValue(target, field, value);
                        continue;
                    }

                    // Handle @SystemContext annotation
                    SystemContext systemContextAnnotation =
                        field.getAnnotation(SystemContext.class);
                    if (systemContextAnnotation != null && !systemContextAnnotation.value().isEmpty()) {
                        Object value = extractValueFromContext(systemContext, systemContextAnnotation.value());
                        setFieldValue(target, field, value);
                        continue;
                    }

                    // Handle @SecurityProfileConfiguration annotation
                    SecurityProfileConfiguration securityConfigAnnotation =
                        field.getAnnotation(SecurityProfileConfiguration.class);
                    if (securityConfigAnnotation != null && !securityConfigAnnotation.value().isEmpty()) {
                        Object value = extractValueFromContext(securityConfig, securityConfigAnnotation.value());
                        setFieldValue(target, field, value);
                    }
                } catch (Exception e) {
                    // Log and continue - don't fail the injection
                    System.err.println("Failed to inject field " + field.getName() + ": " + e.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Extract a value from a context object by accessing its field or calling its getter method.
     * Automatically extracts values from Slot objects.
     */
    private static Object extractValueFromContext(Object contextObject, String propertyName) throws Exception {
        if (contextObject == null) {
            throw new IllegalArgumentException("Context object cannot be null");
        }
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }

        Object value = null;

        // Try to access as a public field first (e.g., SystemContext.stackName)
        try {
            Field contextField = contextObject.getClass().getField(propertyName);
            value = contextField.get(contextObject);
        } catch (NoSuchFieldException e) {
            // Not a field, try methods instead
        }

        // If field access didn't work, try methods
        if (value == null) {
            // Try standard getter method (e.g., "region" -> "region()")
            Method method = findMethod(contextObject.getClass(), propertyName);
            if (method != null) {
                value = method.invoke(contextObject);
            }

            // Try "get" prefix (e.g., "wafEnabled" -> "getWafEnabled()")
            if (value == null) {
                method = findMethod(contextObject.getClass(), "get" + capitalize(propertyName));
                if (method != null) {
                    value = method.invoke(contextObject);
                }
            }

            // Try "is" prefix for boolean (e.g., "wafEnabled" -> "isWafEnabled()")
            if (value == null) {
                method = findMethod(contextObject.getClass(), "is" + capitalize(propertyName));
                if (method != null) {
                    value = method.invoke(contextObject);
                }
            }
        }

        if (value == null) {
            throw new NoSuchMethodException("No field or getter found for property: " + propertyName);
        }

        // Auto-extract from Slot if the value is a Slot object
        if (value instanceof com.cloudforgeci.api.core.Slot<?> slot) {
            return slot.get().orElseThrow(() ->
                new IllegalStateException("Required slot '" + propertyName + "' is not set. " +
                    "Ensure the corresponding factory creates this resource before injection.")
            );
        }

        return value;
    }

    /**
     * Find a method by name in a class or its superclasses.
     */
    private static Method findMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Set the value of a field using reflection.
     */
    private static void setFieldValue(Object target, Field field, Object value) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Capitalize the first letter of a string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Get the appropriate security profile configuration based on the security profile.
     */
    private static com.cloudforgeci.api.interfaces.SecurityProfileConfiguration getSecurityProfileConfiguration(
            com.cloudforgeci.api.interfaces.SecurityProfile securityProfile,
            com.cloudforgeci.api.core.DeploymentContext deploymentContext) {
        return switch (securityProfile) {
            case DEV -> new com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration(deploymentContext);
            case STAGING -> new com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration(deploymentContext);
            case PRODUCTION -> new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration(deploymentContext);
        };
    }
}
