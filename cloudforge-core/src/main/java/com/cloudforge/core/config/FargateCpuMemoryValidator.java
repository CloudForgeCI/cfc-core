package com.cloudforge.core.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates AWS Fargate CPU/memory combinations.
 *
 * <p>AWS Fargate requires specific CPU/memory combinations. This validator ensures
 * that the selected CPU and memory values form a valid combination according to
 * AWS Fargate task definition requirements.</p>
 *
 * <h2>Valid Combinations:</h2>
 * <table border="1">
 *   <tr><th>CPU (vCPU)</th><th>Memory (MB)</th></tr>
 *   <tr><td>256 (0.25 vCPU)</td><td>512, 1024, 2048</td></tr>
 *   <tr><td>512 (0.5 vCPU)</td><td>1024, 2048, 3072, 4096</td></tr>
 *   <tr><td>1024 (1 vCPU)</td><td>2048, 3072, 4096, 5120, 6144, 7168, 8192</td></tr>
 *   <tr><td>2048 (2 vCPU)</td><td>4096, 5120, 6144, 7168, 8192, 9216, 10240, 11264, 12288, 13312, 14336, 15360, 16384</td></tr>
 *   <tr><td>4096 (4 vCPU)</td><td>8192 to 30720 (in 1024 MB increments)</td></tr>
 *   <tr><td>8192 (8 vCPU)</td><td>16384 to 61440 (in 4096 MB increments)</td></tr>
 *   <tr><td>16384 (16 vCPU)</td><td>32768 to 122880 (in 8192 MB increments)</td></tr>
 * </table>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "Fargate Memory (MB)",
 *     validators = {"FargateCpuMemoryValidator"}
 * )
 * public int fargateMemory = 2048;
 * }</pre>
 *
 * @since 3.1.0
 */
public class FargateCpuMemoryValidator implements FieldValidator {

    // Valid Fargate CPU/memory combinations
    private static final Map<Integer, List<Integer>> VALID_COMBINATIONS = new HashMap<>();

    static {
        // 0.25 vCPU (256)
        VALID_COMBINATIONS.put(256, List.of(512, 1024, 2048));

        // 0.5 vCPU (512)
        VALID_COMBINATIONS.put(512, List.of(1024, 2048, 3072, 4096));

        // 1 vCPU (1024)
        VALID_COMBINATIONS.put(1024, List.of(2048, 3072, 4096, 5120, 6144, 7168, 8192));

        // 2 vCPU (2048)
        VALID_COMBINATIONS.put(2048, List.of(
            4096, 5120, 6144, 7168, 8192, 9216, 10240, 11264, 12288, 13312, 14336, 15360, 16384
        ));

        // 4 vCPU (4096) - 8192 to 30720 in 1024 increments
        VALID_COMBINATIONS.put(4096, generateRange(8192, 30720, 1024));

        // 8 vCPU (8192) - 16384 to 61440 in 4096 increments
        VALID_COMBINATIONS.put(8192, generateRange(16384, 61440, 4096));

        // 16 vCPU (16384) - 32768 to 122880 in 8192 increments
        VALID_COMBINATIONS.put(16384, generateRange(32768, 122880, 8192));
    }

    /**
     * Generates a range of values with a given increment.
     */
    private static List<Integer> generateRange(int start, int end, int increment) {
        java.util.List<Integer> range = new java.util.ArrayList<>();
        for (int value = start; value <= end; value += increment) {
            range.add(value);
        }
        return range;
    }

    @Override
    public ValidationResult validate(ConfigFieldInfo field, Object value, Object config) {
        if (config == null || value == null) {
            return ValidationResult.ok();
        }

        // Only validate if this is fargateCpu or fargateMemory field
        String fieldName = field.fieldName();
        if (!fieldName.equals("fargateCpu") && !fieldName.equals("fargateMemory")) {
            return ValidationResult.ok();
        }

        try {
            // Get both CPU and memory values
            Integer cpu = getFieldValue(config, "fargateCpu", Integer.class);
            Integer memory = getFieldValue(config, "fargateMemory", Integer.class);

            if (cpu == null || memory == null) {
                return ValidationResult.ok();
            }

            // Check if combination is valid
            List<Integer> validMemoryValues = VALID_COMBINATIONS.get(cpu);
            if (validMemoryValues == null) {
                return ValidationResult.error(
                    "Invalid Fargate CPU value: " + cpu + ". Valid values: 256, 512, 1024, 2048, 4096, 8192, 16384"
                );
            }

            if (!validMemoryValues.contains(memory)) {
                return ValidationResult.error(
                    "Invalid Fargate CPU/Memory combination. CPU " + cpu + " supports memory values: " +
                    formatMemoryValues(validMemoryValues)
                );
            }

            return ValidationResult.ok();

        } catch (Exception e) {
            // Reflection errors - log but don't fail
            System.err.println("Warning: FargateCpuMemoryValidator failed: " + e.getMessage());
            return ValidationResult.ok();
        }
    }

    /**
     * Gets a field value with type casting.
     */
    private <T> T getFieldValue(Object config, String fieldName, Class<T> type) {
        try {
            java.lang.reflect.Field field = config.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(config);
            if (value != null && type.isInstance(value)) {
                return type.cast(value);
            }
            if (value instanceof Number) {
                return type.cast(((Number) value).intValue());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Formats memory values for error messages.
     */
    private String formatMemoryValues(List<Integer> values) {
        if (values.size() <= 5) {
            return values.stream()
                .map(Object::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        } else {
            // Show first 3, last 2
            return values.get(0) + ", " + values.get(1) + ", " + values.get(2) +
                " ... " + values.get(values.size() - 2) + ", " + values.get(values.size() - 1);
        }
    }
}
