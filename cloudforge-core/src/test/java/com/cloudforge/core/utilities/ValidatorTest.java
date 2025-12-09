package com.cloudforge.core.utilities;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Jakarta validation annotations (Arn, DnsLabel, DnsName, OneOf).
 */
class ValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // Test classes for validation
    static class TestArnField {
        @Arn
        String arn;

        TestArnField(String arn) {
            this.arn = arn;
        }
    }

    static class TestOptionalArnField {
        @Arn(optional = true)
        String arn;

        TestOptionalArnField(String arn) {
            this.arn = arn;
        }
    }

    static class TestDnsLabelField {
        @DnsLabel
        String label;

        TestDnsLabelField(String label) {
            this.label = label;
        }
    }

    static class TestDnsNameField {
        @DnsName
        String name;

        TestDnsNameField(String name) {
            this.name = name;
        }
    }

    static class TestOneOfField {
        @OneOf({"dev", "staging", "production"})
        String environment;

        TestOneOfField(String environment) {
            this.environment = environment;
        }
    }

    // Arn Validator Tests
    @Test
    void testValidArn() {
        TestArnField test = new TestArnField("arn:aws:s3:::my-bucket");
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidArnWithAccount() {
        TestArnField test = new TestArnField("arn:aws:iam::123456789012:role/MyRole");
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidArnWithRegion() {
        TestArnField test = new TestArnField("arn:aws:secretsmanager:us-east-1:123456789012:secret:my-secret");
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidArn() {
        TestArnField test = new TestArnField("not-an-arn");
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testNullArnNotOptional() {
        TestArnField test = new TestArnField(null);
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testNullArnOptional() {
        TestOptionalArnField test = new TestOptionalArnField(null);
        Set<ConstraintViolation<TestOptionalArnField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testEmptyArnNotOptional() {
        TestArnField test = new TestArnField("");
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testEmptyArnOptional() {
        TestOptionalArnField test = new TestOptionalArnField("");
        Set<ConstraintViolation<TestOptionalArnField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    // DnsLabel Validator Tests
    @Test
    void testValidDnsLabel() {
        TestDnsLabelField test = new TestDnsLabelField("my-app");
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidDnsLabelWithNumbers() {
        TestDnsLabelField test = new TestDnsLabelField("app123");
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testDnsLabelTooLong() {
        String longLabel = "a".repeat(64); // DNS labels max 63 chars
        TestDnsLabelField test = new TestDnsLabelField(longLabel);
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testDnsLabelMaxLength() {
        String maxLabel = "a".repeat(63); // Exactly 63 chars
        TestDnsLabelField test = new TestDnsLabelField(maxLabel);
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidDnsLabelStartsWithHyphen() {
        TestDnsLabelField test = new TestDnsLabelField("-invalid");
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testInvalidDnsLabelEndsWithHyphen() {
        TestDnsLabelField test = new TestDnsLabelField("invalid-");
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    // DnsName Validator Tests
    @Test
    void testValidDnsName() {
        TestDnsNameField test = new TestDnsNameField("example.com");
        Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidFqdn() {
        TestDnsNameField test = new TestDnsNameField("app.cloudforge.example.com");
        Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testDnsNameWithHyphens() {
        TestDnsNameField test = new TestDnsNameField("my-app.my-domain.com");
        Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testDnsNameTooLong() {
        String longDomain = "a".repeat(250) + ".com"; // DNS names max 253 chars
        TestDnsNameField test = new TestDnsNameField(longDomain);
        Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testDnsNameSingleLabel() {
        // Single labels are valid (e.g., "localhost")
        TestDnsNameField test = new TestDnsNameField("localhost");
        Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testDnsNameInvalidCharacters() {
        TestDnsNameField test = new TestDnsNameField("invalid_domain.com"); // Underscore not allowed
        Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    // OneOf Validator Tests
    @Test
    void testValidOneOfValue() {
        TestOneOfField test = new TestOneOfField("dev");
        Set<ConstraintViolation<TestOneOfField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidOneOfProduction() {
        TestOneOfField test = new TestOneOfField("production");
        Set<ConstraintViolation<TestOneOfField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidOneOfStaging() {
        TestOneOfField test = new TestOneOfField("staging");
        Set<ConstraintViolation<TestOneOfField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidOneOfValue() {
        TestOneOfField test = new TestOneOfField("invalid");
        Set<ConstraintViolation<TestOneOfField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testOneOfNullValue() {
        TestOneOfField test = new TestOneOfField(null);
        Set<ConstraintViolation<TestOneOfField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty()); // null is allowed per validator implementation
    }

    @Test
    void testOneOfCaseSensitive() {
        TestOneOfField test = new TestOneOfField("Dev"); // Should be lowercase "dev"
        Set<ConstraintViolation<TestOneOfField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    // Additional comprehensive tests
    @Test
    void testArnWithDifferentPartitions() {
        String[] validArns = {
            "arn:aws:s3:::bucket",
            "arn:aws-cn:s3:::bucket",
            "arn:aws-us-gov:s3:::bucket"
        };

        for (String arn : validArns) {
            TestArnField test = new TestArnField(arn);
            Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
            assertTrue(violations.isEmpty(), "ARN should be valid: " + arn);
        }
    }

    @Test
    void testArnWithInvalidAccount() {
        // Account must be exactly 12 digits or empty
        TestArnField test = new TestArnField("arn:aws:iam::12345:role/MyRole");
        Set<ConstraintViolation<TestArnField>> violations = validator.validate(test);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testDnsLabelCaseInsensitive() {
        // DNS labels should be lowercase
        TestDnsLabelField test = new TestDnsLabelField("myapp");
        Set<ConstraintViolation<TestDnsLabelField>> violations = validator.validate(test);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testDnsNameMultipleLevels() {
        String[] validNames = {
            "a.b",
            "a.b.c",
            "a.b.c.d",
            "sub.domain.example.com"
        };

        for (String name : validNames) {
            TestDnsNameField test = new TestDnsNameField(name);
            Set<ConstraintViolation<TestDnsNameField>> violations = validator.validate(test);
            assertTrue(violations.isEmpty(), "DNS name should be valid: " + name);
        }
    }
}
