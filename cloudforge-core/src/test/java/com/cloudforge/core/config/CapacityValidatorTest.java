package com.cloudforge.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapacityValidatorTest {

    private CapacityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CapacityValidator();
    }

    // Test config class with capacity fields
    static class ConfigWithCapacity {
        public int minCapacity = 2;
        public int maxCapacity = 10;
    }

    // Test config class without minCapacity field
    static class ConfigWithoutMinCapacity {
        public int maxCapacity = 10;
    }

    // Test config class with Integer wrapper types
    static class ConfigWithIntegerWrappers {
        public Integer minCapacity = 2;
        public Integer maxCapacity = 10;
    }

    // Test config class with null minCapacity
    static class ConfigWithNullMinCapacity {
        public Integer minCapacity = null;
        public Integer maxCapacity = 10;
    }

    private ConfigFieldInfo createMaxCapacityFieldInfo() {
        // Create a minimal ConfigFieldInfo for maxCapacity
        return new ConfigFieldInfo(
            "maxCapacity",          // fieldName
            "Maximum Capacity",     // displayName
            "Maximum instances",    // description
            "Scaling",              // category
            "always",               // visibleWhen
            "",                     // dependsOn
            true,                   // required
            "10",                   // example
            new String[]{},         // allowedValues
            1.0,                    // min
            100.0,                  // max
            "",                     // pattern
            "",                     // defaultFrom
            "",                     // propertyKey
            false,                  // sensitive
            "",                     // sourceConfig
            new com.cloudforge.core.annotation.FieldTag[]{},  // tags
            new String[]{"CapacityValidator"},  // validators
            10,                     // order
            int.class,              // type
            null                    // field (not needed for this test)
        );
    }

    private ConfigFieldInfo createOtherFieldInfo(String fieldName) {
        return new ConfigFieldInfo(
            fieldName,
            fieldName,
            "",
            "",
            "always",
            "",
            false,
            "",
            new String[]{},
            Double.MIN_VALUE,
            Double.MAX_VALUE,
            "",
            "",
            "",
            false,
            "",
            new com.cloudforge.core.annotation.FieldTag[]{},
            new String[]{},
            0,
            int.class,
            null
        );
    }

    @Test
    void testValidWhenMaxGreaterThanMin() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 2;
        config.maxCapacity = 10;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 10, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testValidWhenMaxEqualsMin() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 5;
        config.maxCapacity = 5;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 5, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testInvalidWhenMaxLessThanMin() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 10;
        config.maxCapacity = 5;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 5, config);

        assertTrue(result.isError());
        assertEquals("Maximum Capacity (5) must be >= Minimum Capacity (10)", result.getMessage());
    }

    @Test
    void testSkipsValidationForNonMaxCapacityField() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 100;  // Higher than max, but should still pass

        ConfigFieldInfo fieldInfo = createOtherFieldInfo("someOtherField");
        ValidationResult result = validator.validate(fieldInfo, 5, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenConfigIsNull() {
        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 10, null);

        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenValueIsNull() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, null, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenMinCapacityFieldMissing() {
        ConfigWithoutMinCapacity config = new ConfigWithoutMinCapacity();
        config.maxCapacity = 1;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 1, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenMinCapacityIsNull() {
        ConfigWithNullMinCapacity config = new ConfigWithNullMinCapacity();
        config.minCapacity = null;
        config.maxCapacity = 5;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 5, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testWorksWithIntegerWrapperTypes() {
        ConfigWithIntegerWrappers config = new ConfigWithIntegerWrappers();
        config.minCapacity = 2;
        config.maxCapacity = 10;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, Integer.valueOf(10), config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testErrorMessageIncludesBothValues() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 50;
        config.maxCapacity = 25;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 25, config);

        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("25"));
        assertTrue(result.getMessage().contains("50"));
    }

    @Test
    void testValidationWithZeroValues() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 0;
        config.maxCapacity = 0;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 0, config);

        assertTrue(result.isSuccess());
    }

    @Test
    void testValidationWithLargeValues() {
        ConfigWithCapacity config = new ConfigWithCapacity();
        config.minCapacity = 1000;
        config.maxCapacity = 10000;

        ConfigFieldInfo fieldInfo = createMaxCapacityFieldInfo();
        ValidationResult result = validator.validate(fieldInfo, 10000, config);

        assertTrue(result.isSuccess());
    }
}
