package com.cloudforge.core.config;

/**
 * Validates that maxCapacity >= minCapacity for scaling configurations.
 *
 * <p>This validator ensures consistent capacity settings for auto-scaling groups,
 * ECS services, and other scalable resources.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "Maximum Capacity",
 *     validators = {"CapacityValidator"}
 * )
 * public int maxCapacity = 10;
 * }</pre>
 *
 * <h2>Validation Logic:</h2>
 * <ul>
 *   <li>Looks for fields named "minCapacity" and "maxCapacity"</li>
 *   <li>If both exist, validates maxCapacity >= minCapacity</li>
 *   <li>Skips validation if either field is missing or null</li>
 * </ul>
 *
 * @since 3.1.0
 */
public class CapacityValidator implements FieldValidator {

    @Override
    public ValidationResult validate(ConfigFieldInfo field, Object value, Object config) {
        if (config == null || value == null) {
            return ValidationResult.ok();
        }

        // Only validate if this is maxCapacity field
        if (!field.fieldName().equals("maxCapacity")) {
            return ValidationResult.ok();
        }

        try {
            // Get minCapacity from config
            java.lang.reflect.Field minCapacityField = config.getClass().getDeclaredField("minCapacity");
            minCapacityField.setAccessible(true);
            Object minCapacityValue = minCapacityField.get(config);

            if (minCapacityValue == null) {
                return ValidationResult.ok();
            }

            // Compare as numbers
            int maxCapacity = ((Number) value).intValue();
            int minCapacity = ((Number) minCapacityValue).intValue();

            if (maxCapacity < minCapacity) {
                return ValidationResult.error(
                    "Maximum Capacity (" + maxCapacity + ") must be >= Minimum Capacity (" + minCapacity + ")"
                );
            }

            return ValidationResult.ok();

        } catch (NoSuchFieldException e) {
            // minCapacity field doesn't exist - skip validation
            return ValidationResult.ok();
        } catch (Exception e) {
            // Other reflection errors - log but don't fail
            System.err.println("Warning: CapacityValidator failed: " + e.getMessage());
            return ValidationResult.ok();
        }
    }
}
