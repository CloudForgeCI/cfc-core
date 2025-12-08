package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.annotation.FieldTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFieldInfoTest {

    // Test class with @ConfigField annotations
    static class TestConfig {

        @ConfigField(
            displayName = "Test Field",
            description = "A test field for unit testing",
            category = "test",
            visibleWhen = "always",
            required = true,
            example = "test-value",
            allowedValues = {"value1", "value2", "value3"},
            min = 0.0,
            max = 100.0,
            pattern = "^[a-z]+$",
            sensitive = false,
            tags = {FieldTag.BILLING_IMPACT},
            order = 10,
            dependsOn = "otherField",
            defaultFrom = "defaultMethod"
        )
        public String annotatedField = "default";

        @ConfigField(
            displayName = "Required Field",
            description = "A required field",
            required = true
        )
        public String requiredField;

        @ConfigField(
            displayName = "Numeric Field",
            description = "A numeric field with range",
            min = 10,
            max = 50
        )
        public int numericField = 25;

        @ConfigField(
            displayName = "Sensitive Field",
            description = "A sensitive field",
            sensitive = true
        )
        public String sensitiveField;

        @ConfigField(
            displayName = "Enum Field",
            description = "Field with allowed values",
            allowedValues = {"dev", "staging", "production"}
        )
        public String enumField = "dev";

        @ConfigField(
            displayName = "Pattern Field",
            description = "Field with pattern validation",
            pattern = "^[A-Z]{2,5}$"
        )
        public String patternField;

        public String nonAnnotatedField;
    }

    private ConfigFieldInfo createFieldInfo(String fieldName) throws NoSuchFieldException {
        Field field = TestConfig.class.getDeclaredField(fieldName);
        return ConfigFieldInfo.from(field);
    }

    @Test
    void testFromFieldWithAnnotation() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");

        assertEquals("annotatedField", info.fieldName());
        assertEquals("Test Field", info.displayName());
        assertEquals("A test field for unit testing", info.description());
        assertEquals("test", info.category());
        assertEquals("always", info.visibleWhen());
        assertTrue(info.required());
        assertEquals("test-value", info.example());
        assertArrayEquals(new String[]{"value1", "value2", "value3"}, info.allowedValues());
        assertEquals(0.0, info.min());
        assertEquals(100.0, info.max());
        assertEquals("^[a-z]+$", info.pattern());
        assertFalse(info.sensitive());
        assertEquals(10, info.order());
        assertEquals("otherField", info.dependsOn());
        assertEquals("defaultMethod", info.defaultFrom());
        assertEquals(String.class, info.type());
    }

    @Test
    void testFromFieldWithoutAnnotationThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            Field field = TestConfig.class.getDeclaredField("nonAnnotatedField");
            ConfigFieldInfo.from(field);
        });
    }

    @Test
    void testFromFieldThrowsDescriptiveMessage() throws NoSuchFieldException {
        Field field = TestConfig.class.getDeclaredField("nonAnnotatedField");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ConfigFieldInfo.from(field));

        assertTrue(ex.getMessage().contains("@ConfigField"));
        assertTrue(ex.getMessage().contains("nonAnnotatedField"));
    }

    @Test
    void testGetValue() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");
        TestConfig config = new TestConfig();
        config.annotatedField = "custom-value";

        Object value = info.getValue(config);
        assertEquals("custom-value", value);
    }

    @Test
    void testGetValueWithNull() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("requiredField");
        TestConfig config = new TestConfig();
        config.requiredField = null;

        Object value = info.getValue(config);
        assertNull(value);
    }

    @Test
    void testSetValue() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");
        TestConfig config = new TestConfig();

        info.setValue(config, "new-value");
        assertEquals("new-value", config.annotatedField);
    }

    @Test
    void testSetValueWithNull() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");
        TestConfig config = new TestConfig();
        config.annotatedField = "initial";

        info.setValue(config, null);
        assertNull(config.annotatedField);
    }

    @Test
    void testHasTag() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");

        assertTrue(info.hasTag(FieldTag.BILLING_IMPACT));
        assertFalse(info.hasTag(FieldTag.DESTRUCTIVE));
        assertFalse(info.hasTag(FieldTag.REQUIRES_RESTART));
    }

    @Test
    void testTagList() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");

        assertEquals(1, info.tagList().size());
        assertTrue(info.tagList().contains(FieldTag.BILLING_IMPACT));
    }

    @Test
    void testValidateRequiredFieldWithNull() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("requiredField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate(null, config);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("required"));
    }

    @Test
    void testValidateRequiredFieldWithValue() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("requiredField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate("valid-value", config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidateNumericFieldBelowMin() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("numericField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate(5, config);  // min is 10
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains(">="));
    }

    @Test
    void testValidateNumericFieldAboveMax() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("numericField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate(60, config);  // max is 50
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("<="));
    }

    @Test
    void testValidateNumericFieldInRange() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("numericField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate(30, config);  // between 10 and 50
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidateNumericFieldAtBoundaries() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("numericField");
        TestConfig config = new TestConfig();

        // At min boundary
        ValidationResult resultMin = info.validate(10, config);
        assertTrue(resultMin.isSuccess());

        // At max boundary
        ValidationResult resultMax = info.validate(50, config);
        assertTrue(resultMax.isSuccess());
    }

    @Test
    void testValidateAllowedValuesValid() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("enumField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate("dev", config);
        assertTrue(result.isSuccess());

        result = info.validate("staging", config);
        assertTrue(result.isSuccess());

        result = info.validate("production", config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidateAllowedValuesInvalid() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("enumField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate("invalid", config);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("must be one of"));
    }

    @Test
    void testValidatePatternValid() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("patternField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate("ABC", config);
        assertTrue(result.isSuccess());

        result = info.validate("HELLO", config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidatePatternInvalid() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("patternField");
        TestConfig config = new TestConfig();

        ValidationResult result = info.validate("abc", config);  // lowercase
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("pattern"));

        result = info.validate("A", config);  // too short
        assertTrue(result.isError());

        result = info.validate("ABCDEF", config);  // too long
        assertTrue(result.isError());
    }

    @Test
    void testValidatePatternWithNullValue() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("patternField");
        TestConfig config = new TestConfig();

        // Pattern validation should be skipped for null values
        ValidationResult result = info.validate(null, config);
        assertTrue(result.isSuccess());  // Pattern field is not required
    }

    @Test
    void testSensitiveField() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("sensitiveField");
        assertTrue(info.sensitive());
    }

    @Test
    void testNonSensitiveField() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");
        assertFalse(info.sensitive());
    }

    @Test
    void testIsVisibleWithAlwaysCondition() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");
        TestConfig config = new TestConfig();

        // "always" visibility should return true
        assertTrue(info.isVisible(null, config));
    }

    @Test
    void testFieldReference() throws NoSuchFieldException {
        ConfigFieldInfo info = createFieldInfo("annotatedField");

        assertNotNull(info.field());
        assertEquals("annotatedField", info.field().getName());
    }

    @Test
    void testRecordEquality() throws NoSuchFieldException {
        ConfigFieldInfo info1 = createFieldInfo("annotatedField");
        ConfigFieldInfo info2 = createFieldInfo("annotatedField");

        // Record equality based on component values
        assertEquals(info1.fieldName(), info2.fieldName());
        assertEquals(info1.displayName(), info2.displayName());
        assertEquals(info1.category(), info2.category());
    }

    @Test
    void testValidateWithNullConfig() throws NoSuchFieldException {
        // Use enumField which has allowedValues but no pattern constraint
        ConfigFieldInfo info = createFieldInfo("enumField");

        // Use a valid value from allowedValues, validators should handle null config gracefully
        ValidationResult result = info.validate("dev", null);
        assertTrue(result.isSuccess());
    }

    @Test
    void testFieldWithMultipleTags() throws NoSuchFieldException {
        // Since our test config only has one tag, let's verify the mechanism works
        ConfigFieldInfo info = createFieldInfo("annotatedField");

        FieldTag[] tags = info.tags();
        assertNotNull(tags);
        assertEquals(1, tags.length);
    }
}
