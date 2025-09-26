package com.cloudforgeci.api.core.utilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for OneOf validation annotation.
 * Tests validation logic without external dependencies.
 */
@DisplayName("OneOf Validation Tests")
class OneOfSimpleTest {

    private OneOf.Validator validator;

    @BeforeEach
    void setUp() {
        validator = new OneOf.Validator();
    }

    @Nested
    @DisplayName("Validator Initialization")
    class ValidatorInitialization {

        @Test
        @DisplayName("Should initialize with allowed values")
        void shouldInitializeWithAllowedValues() {
            OneOf annotation = new TestOneOfAnnotation("option1", "option2", "option3");
            validator.initialize(annotation);
            
            // Test that allowed values are valid
            assertTrue(validator.isValid("option1", null));
            assertTrue(validator.isValid("option2", null));
            assertTrue(validator.isValid("option3", null));
            
            // Test that disallowed values are invalid
            assertFalse(validator.isValid("invalid", null));
            assertFalse(validator.isValid("option4", null));
        }

        @Test
        @DisplayName("Should handle empty allowed values")
        void shouldHandleEmptyAllowedValues() {
            OneOf annotation = new TestOneOfAnnotation();
            validator.initialize(annotation);
            
            // Only null should be valid when no values are allowed
            assertTrue(validator.isValid(null, null));
            assertFalse(validator.isValid("", null));
            assertFalse(validator.isValid("any-value", null));
        }

        @Test
        @DisplayName("Should handle single allowed value")
        void shouldHandleSingleAllowedValue() {
            OneOf annotation = new TestOneOfAnnotation("only-option");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("only-option", null));
            assertTrue(validator.isValid(null, null));
            assertFalse(validator.isValid("other-option", null));
            assertFalse(validator.isValid("", null));
        }
    }

    @Nested
    @DisplayName("Valid Values")
    class ValidValues {

        @BeforeEach
        void setUpCommonValues() {
            OneOf annotation = new TestOneOfAnnotation("dev", "staging", "production");
            validator.initialize(annotation);
        }

        @Test
        @DisplayName("Should accept null values")
        void shouldAcceptNullValues() {
            assertTrue(validator.isValid(null, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"dev", "staging", "production"})
        @DisplayName("Should accept exact allowed values")
        void shouldAcceptExactAllowedValues(String value) {
            assertTrue(validator.isValid(value, null));
        }

        @Test
        @DisplayName("Should accept case-sensitive values")
        void shouldAcceptCaseSensitiveValues() {
            // OneOf is case-sensitive by default
            assertTrue(validator.isValid("dev", null));
            assertTrue(validator.isValid("staging", null));
            assertTrue(validator.isValid("production", null));
            
            // Case variations should be invalid
            assertFalse(validator.isValid("Dev", null));
            assertFalse(validator.isValid("DEV", null));
            assertFalse(validator.isValid("Staging", null));
            assertFalse(validator.isValid("PRODUCTION", null));
        }
    }

    @Nested
    @DisplayName("Invalid Values")
    class InvalidValues {

        @BeforeEach
        void setUpCommonValues() {
            OneOf annotation = new TestOneOfAnnotation("dev", "staging", "production");
            validator.initialize(annotation);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "development",
            "prod",
            "stage",
            "test",
            "local",
            "qa",
            "uat",
            "preprod",
            "live",
            "beta",
            "alpha",
            "release",
            "master",
            "main"
        })
        @DisplayName("Should reject similar but not exact values")
        void shouldRejectSimilarValues(String value) {
            assertFalse(validator.isValid(value, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "dev ",
            " dev",
            "dev\n",
            "dev\t",
            "dev\r",
            " dev ",
            "\tdev\t",
            "\ndev\n"
        })
        @DisplayName("Should reject values with whitespace")
        void shouldRejectValuesWithWhitespace(String value) {
            assertFalse(validator.isValid(value, null));
        }

        @Test
        @DisplayName("Should reject empty string")
        void shouldRejectEmptyString() {
            assertFalse(validator.isValid("", null));
        }
    }

    @Nested
    @DisplayName("Real-world Examples")
    class RealWorldExamples {

        @Test
        @DisplayName("Should validate environment values")
        void shouldValidateEnvironmentValues() {
            OneOf annotation = new TestOneOfAnnotation("dev", "staging", "prod");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("dev", null));
            assertTrue(validator.isValid("staging", null));
            assertTrue(validator.isValid("prod", null));
            assertFalse(validator.isValid("development", null));
            assertFalse(validator.isValid("production", null));
        }

        @Test
        @DisplayName("Should validate network modes")
        void shouldValidateNetworkModes() {
            OneOf annotation = new TestOneOfAnnotation("public-no-nat", "private-with-nat");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("public-no-nat", null));
            assertTrue(validator.isValid("private-with-nat", null));
            assertFalse(validator.isValid("public", null));
            assertFalse(validator.isValid("private", null));
            assertFalse(validator.isValid("public-with-nat", null));
        }

        @Test
        @DisplayName("Should validate load balancer types")
        void shouldValidateLoadBalancerTypes() {
            OneOf annotation = new TestOneOfAnnotation("alb", "nlb");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("alb", null));
            assertTrue(validator.isValid("nlb", null));
            assertFalse(validator.isValid("elb", null));
            assertFalse(validator.isValid("application", null));
            assertFalse(validator.isValid("network", null));
        }

        @Test
        @DisplayName("Should validate auth modes")
        void shouldValidateAuthModes() {
            OneOf annotation = new TestOneOfAnnotation("none", "alb-oidc", "jenkins-oidc");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("none", null));
            assertTrue(validator.isValid("alb-oidc", null));
            assertTrue(validator.isValid("jenkins-oidc", null));
            assertFalse(validator.isValid("oidc", null));
            assertFalse(validator.isValid("alb", null));
            assertFalse(validator.isValid("jenkins", null));
        }

        @Test
        @DisplayName("Should validate tier values")
        void shouldValidateTierValues() {
            OneOf annotation = new TestOneOfAnnotation("public", "enterprise");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("public", null));
            assertTrue(validator.isValid("enterprise", null));
            assertFalse(validator.isValid("private", null));
            assertFalse(validator.isValid("premium", null));
            assertFalse(validator.isValid("basic", null));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle special characters in allowed values")
        void shouldHandleSpecialCharacters() {
            OneOf annotation = new TestOneOfAnnotation("test-value", "test_value", "test.value", "test@value");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("test-value", null));
            assertTrue(validator.isValid("test_value", null));
            assertTrue(validator.isValid("test.value", null));
            assertTrue(validator.isValid("test@value", null));
            assertFalse(validator.isValid("testvalue", null));
        }

        @Test
        @DisplayName("Should handle numeric values")
        void shouldHandleNumericValues() {
            OneOf annotation = new TestOneOfAnnotation("1", "2", "3");
            validator.initialize(annotation);
            
            assertTrue(validator.isValid("1", null));
            assertTrue(validator.isValid("2", null));
            assertTrue(validator.isValid("3", null));
            assertFalse(validator.isValid("4", null));
            assertFalse(validator.isValid("0", null));
        }
    }

    // Simple test implementation of OneOf annotation
    private static class TestOneOfAnnotation implements OneOf {
        private final String[] values;

        public TestOneOfAnnotation(String... values) {
            this.values = values;
        }

        @Override
        public String message() {
            return "must be one of: {value}";
        }

        @Override
        public Class<?>[] groups() {
            return new Class[0];
        }

        @Override
        public Class<? extends jakarta.validation.Payload>[] payload() {
            return new Class[0];
        }

        @Override
        public String[] value() {
            return values;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return OneOf.class;
        }
    }
}
