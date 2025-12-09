package com.cloudforge.core.config;

/**
 * Custom validator for configuration field cross-field validation.
 *
 * <p>Validators enable complex validation logic that depends on multiple fields
 * or external state. They are executed after basic field-level validation
 * (required, min, max, allowedValues, pattern).</p>
 *
 * <h2>Built-in Validators:</h2>
 * <ul>
 *   <li>{@link CapacityValidator} - Validates maxCapacity >= minCapacity</li>
 *   <li>{@link FargateCpuMemoryValidator} - Validates AWS Fargate CPU/memory combinations</li>
 * </ul>
 *
 * <h2>Usage in Annotations:</h2>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "Maximum Capacity",
 *     validators = {"CapacityValidator"}
 * )
 * public int maxCapacity = 10;
 * }</pre>
 *
 * <h2>Implementing Custom Validators:</h2>
 * <pre>{@code
 * public class CustomValidator implements FieldValidator {
 *     @Override
 *     public ValidationResult validate(
 *         ConfigFieldInfo field,
 *         Object value,
 *         Object config
 *     ) {
 *         // Access other fields via reflection
 *         // Return ValidationResult.ok() or ValidationResult.error(message)
 *     }
 * }
 * }</pre>
 *
 * @since 3.0.0
 */
public interface FieldValidator {

    /**
     * Validates a field value in the context of the complete configuration.
     *
     * @param field metadata about the field being validated
     * @param value the value to validate
     * @param config the complete configuration object (allows accessing other fields)
     * @return validation result (ok or error with message)
     */
    ValidationResult validate(ConfigFieldInfo field, Object value, Object config);
}
