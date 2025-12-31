package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.annotation.FieldTag;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Runtime metadata for a configuration field discovered via introspection.
 *
 * <p>Encapsulates all information from {@link ConfigField} annotation plus
 * reflection metadata needed for prompting, validation, and value assignment.</p>
 *
 * @param fieldName the Java field name
 * @param displayName the user-friendly display name
 * @param description the field description for user help
 * @param category the configuration category for grouping
 * @param visibleWhen the visibility condition expression
 * @param dependsOn the field this depends on
 * @param required whether the field is required
 * @param example an example value for user guidance
 * @param allowedValues array of allowed values (for constrained choices)
 * @param min the minimum value for numeric fields
 * @param max the maximum value for numeric fields
 * @param pattern the regex pattern for string validation
 * @param defaultFrom the ApplicationSpec method to get default value from
 * @param sensitive whether this field contains sensitive data
 * @param sourceConfig the field containing the source location for sensitive data
 * @param tags the field tags for categorization and filtering
 * @param validators the custom validator class names
 * @param order the display order for sorting fields
 * @param type the Java type of the field
 * @param field the reflected Field object
 * @since 3.0.0
 */
public record ConfigFieldInfo(
    String fieldName,
    String displayName,
    String description,
    String category,
    String visibleWhen,
    String dependsOn,
    boolean required,
    String example,
    String[] allowedValues,
    double min,
    double max,
    String pattern,
    String defaultFrom,
    boolean sensitive,
    String sourceConfig,
    FieldTag[] tags,
    String[] validators,
    int order,
    Class<?> type,
    Field field
) {

    /**
     * Creates ConfigFieldInfo from a field with @ConfigField annotation.
     */
    public static ConfigFieldInfo from(Field field) {
        ConfigField annotation = field.getAnnotation(ConfigField.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Field must have @ConfigField annotation: " + field.getName());
        }

        field.setAccessible(true);

        return new ConfigFieldInfo(
            field.getName(),
            annotation.displayName(),
            annotation.description(),
            annotation.category(),
            annotation.visibleWhen(),
            annotation.dependsOn(),
            annotation.required(),
            annotation.example(),
            annotation.allowedValues(),
            annotation.min(),
            annotation.max(),
            annotation.pattern(),
            annotation.defaultFrom(),
            annotation.sensitive(),
            annotation.sourceConfig(),
            annotation.tags(),
            annotation.validators(),
            annotation.order(),
            field.getType(),
            field
        );
    }

    /**
     * Gets the current value of this field from the config object.
     */
    public Object getValue(Object config) {
        try {
            return field.get(config);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field value: " + fieldName, e);
        }
    }

    /**
     * Sets the value of this field in the config object.
     */
    public void setValue(Object config, Object value) {
        try {
            field.set(config, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field value: " + fieldName, e);
        }
    }

    /**
     * Checks if this field has a specific tag.
     */
    public boolean hasTag(FieldTag tag) {
        return Arrays.asList(tags).contains(tag);
    }

    /**
     * Gets all tags as a list.
     */
    public List<FieldTag> tagList() {
        return Arrays.asList(tags);
    }

    /**
     * Checks if this field is visible based on the current configuration.
     *
     * @param appSpec the application spec (may be null)
     * @param config the deployment config object
     * @return true if the field should be visible, false otherwise
     */
    public boolean isVisible(Object appSpec, Object config) {
        if (visibleWhen.equals("always") || visibleWhen.isEmpty()) {
            return true;
        }

        try {
            VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
                (com.cloudforge.core.interfaces.ApplicationSpec) appSpec,
                config,
                visibleWhen
            );
            return evaluator.evaluate();
        } catch (Exception e) {
            // Log warning but don't fail - default to visible
            System.err.println("Warning: Failed to evaluate visibility expression '" + visibleWhen +
                "' for field '" + fieldName + "': " + e.getMessage());
            return true;
        }
    }

    /**
     * Validates the value according to field constraints and custom validators.
     */
    public ValidationResult validate(Object value, Object config) {
        // Basic validation
        if (required && value == null) {
            return ValidationResult.error("Field '" + displayName + "' is required");
        }

        // Numeric range validation
        if (value instanceof Number num) {
            double d = num.doubleValue();
            if (d < min) {
                return ValidationResult.error("Field '" + displayName + "' must be >= " + min);
            }
            if (d > max) {
                return ValidationResult.error("Field '" + displayName + "' must be <= " + max);
            }
        }

        // Allowed values validation
        if (allowedValues.length > 0 && value != null) {
            String strValue = value.toString();
            boolean found = false;
            for (String allowed : allowedValues) {
                if (allowed.equals(strValue)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return ValidationResult.error("Field '" + displayName + "' must be one of: " +
                    String.join(", ", allowedValues));
            }
        }

        // Pattern validation
        if (!pattern.isEmpty() && value != null) {
            String strValue = value.toString();
            if (!strValue.matches(pattern)) {
                return ValidationResult.error("Field '" + displayName + "' does not match required pattern: " + pattern);
            }
        }

        // Custom validator execution
        if (validators.length > 0 && config != null) {
            for (String validatorName : validators) {
                try {
                    // Try to find validator in com.cloudforge.core.config package
                    Class<?> validatorClass = Class.forName("com.cloudforge.core.config." + validatorName);
                    FieldValidator validator = (FieldValidator) validatorClass.getDeclaredConstructor().newInstance();
                    ValidationResult result = validator.validate(this, value, config);
                    if (result.isError()) {
                        return result;
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("Warning: Validator not found: " + validatorName);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to execute validator " + validatorName + ": " + e.getMessage());
                }
            }
        }

        return ValidationResult.ok();
    }
}
