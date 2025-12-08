package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.interfaces.ApplicationSpec;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers and filters configuration fields using reflection and annotations.
 *
 * <p>This is the core of the Configuration Introspection system, providing automatic
 * field discovery based on {@link ConfigField} annotations and application capabilities.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Discover all fields for an application
 * List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(gitlabSpec);
 *
 * // Discover fields in a specific category
 * List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverFields(gitlabSpec, "database");
 *
 * // Filter by visibility
 * List<ConfigFieldInfo> visibleFields = fields.stream()
 *     .filter(f -> f.isVisible(config))
 *     .toList();
 * }</pre>
 *
 * @since 3.0.0
 */
public class ConfigurationIntrospector {

    /**
     * Discovers all configuration fields from DeploymentConfig.
     *
     * <p>Returns fields sorted by category order, then field order, then display name.</p>
     *
     * @param appSpec application spec for determining field visibility
     * @return list of all discovered fields
     */
    public static List<ConfigFieldInfo> discoverFields(ApplicationSpec appSpec) {
        return discoverFields(appSpec, null);
    }

    /**
     * Discovers configuration fields for a specific category.
     *
     * <p>Returns fields sorted by field order, then display name.</p>
     *
     * @param appSpec application spec for determining field visibility
     * @param category category to filter by, or null for all categories
     * @return list of discovered fields matching the category
     */
    public static List<ConfigFieldInfo> discoverFields(ApplicationSpec appSpec, String category) {
        List<ConfigFieldInfo> fields = new ArrayList<>();

        // Use reflection to find all fields in DeploymentConfig
        // For now, we'll scan the DeploymentConfig class
        Class<?> configClass = getDeploymentConfigClass();
        if (configClass == null) {
            return fields;
        }

        for (Field field : configClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ConfigField.class)) {
                continue;
            }

            ConfigFieldInfo fieldInfo = ConfigFieldInfo.from(field);

            // Filter by category if specified
            if (category != null && !category.isEmpty() && !fieldInfo.category().equals(category)) {
                continue;
            }

            fields.add(fieldInfo);
        }

        // Sort by category order (if applicable), field order, then display name
        fields.sort(Comparator
            .comparing((ConfigFieldInfo f) -> getCategoryOrder(f.category()))
            .thenComparing(ConfigFieldInfo::order)
            .thenComparing(ConfigFieldInfo::displayName));

        return fields;
    }

    /**
     * Discovers visible fields only (based on application capabilities and configuration state).
     *
     * @param appSpec application spec for determining field visibility
     * @param config current configuration state for evaluating visibility expressions
     * @return list of visible fields
     */
    public static List<ConfigFieldInfo> discoverVisibleFields(ApplicationSpec appSpec, Object config) {
        return discoverFields(appSpec).stream()
            .filter(field -> field.isVisible(appSpec, config))
            .toList();
    }

    /**
     * Discovers fields by category with visibility filtering.
     *
     * @param appSpec application spec
     * @param config current configuration state
     * @param category category to filter by
     * @return list of visible fields in the category
     */
    public static List<ConfigFieldInfo> discoverVisibleFields(ApplicationSpec appSpec, Object config, String category) {
        return discoverFields(appSpec, category).stream()
            .filter(field -> field.isVisible(appSpec, config))
            .toList();
    }

    /**
     * Gets the DeploymentConfig class using reflection.
     *
     * <p>Tries multiple potential package locations for DeploymentConfig.</p>
     */
    private static Class<?> getDeploymentConfigClass() {
        // Try common package locations
        String[] packagePrefixes = {
            "com.cloudforge.core.config.",
            "com.cloudforgeci.api.core."
        };

        for (String prefix : packagePrefixes) {
            try {
                return Class.forName(prefix + "DeploymentConfig");
            } catch (ClassNotFoundException e) {
                // Try next package
            }
        }

        return null;
    }

    /**
     * Gets the display order for a category.
     *
     * <p>Categories are displayed in this order:</p>
     * <ol>
     *   <li>basic - Essential configuration</li>
     *   <li>network - Domain, SSL, networking</li>
     *   <li>security - Authentication, encryption, compliance</li>
     *   <li>database - RDS database provisioning</li>
     *   <li>resources - CPU, memory, scaling, instance sizing</li>
     *   <li>monitoring - CloudWatch, GuardDuty, logging</li>
     * </ol>
     */
    private static int getCategoryOrder(String category) {
        return switch (category) {
            case "basic" -> 1;
            case "network" -> 2;
            case "security" -> 3;
            case "database" -> 4;
            case "resources" -> 5;
            case "monitoring" -> 6;
            default -> 1000;  // Unknown categories go last
        };
    }
}
