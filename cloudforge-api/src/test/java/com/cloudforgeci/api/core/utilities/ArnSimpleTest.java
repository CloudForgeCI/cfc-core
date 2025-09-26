package com.cloudforgeci.api.core.utilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for Arn validation annotation.
 * Tests validation logic without external dependencies.
 */
@DisplayName("ARN Validation Tests")
class ArnSimpleTest {

    private Arn.Validator validator;

    @BeforeEach
    void setUp() {
        validator = new Arn.Validator();
    }

    @Nested
    @DisplayName("Validator Initialization")
    class ValidatorInitialization {

        @Test
        @DisplayName("Should initialize with optional=false by default")
        void shouldInitializeWithOptionalFalse() {
            Arn annotation = new TestArnAnnotation(false);
            validator.initialize(annotation);
            
            // Test that null/blank is invalid when not optional
            assertFalse(validator.isValid(null, null));
            assertFalse(validator.isValid("", null));
            assertFalse(validator.isValid("   ", null));
        }

        @Test
        @DisplayName("Should initialize with optional=true")
        void shouldInitializeWithOptionalTrue() {
            Arn annotation = new TestArnAnnotation(true);
            validator.initialize(annotation);
            
            // Test that null/blank is valid when optional
            assertTrue(validator.isValid(null, null));
            assertTrue(validator.isValid("", null));
            assertTrue(validator.isValid("   ", null));
        }
    }

    @Nested
    @DisplayName("Valid ARNs")
    class ValidArns {

        @BeforeEach
        void setUpNonOptional() {
            Arn annotation = new TestArnAnnotation(false);
            validator.initialize(annotation);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "arn:aws:s3:::my-bucket",
            "arn:aws:s3:::my-bucket/object",
            "arn:aws:iam::123456789012:user/username",
            "arn:aws:iam::123456789012:role/role-name",
            "arn:aws:iam::123456789012:policy/policy-name",
            "arn:aws:ec2:us-east-1:123456789012:instance/i-1234567890abcdef0",
            "arn:aws:rds:us-east-1:123456789012:db:mydb",
            "arn:aws:lambda:us-east-1:123456789012:function:my-function",
            "arn:aws:logs:us-east-1:123456789012:log-group:/aws/lambda/my-function",
            "arn:aws:cloudformation:us-east-1:123456789012:stack/my-stack/12345678",
            "arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012",
            "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/my-lb/50dc6c495c0c9188",
            "arn:aws:ecs:us-east-1:123456789012:cluster/my-cluster",
            "arn:aws:ecs:us-east-1:123456789012:task-definition/my-task:1",
            "arn:aws:route53:::hostedzone/Z1234567890ABC",
            "arn:aws:sns:us-east-1:123456789012:my-topic",
            "arn:aws:sqs:us-east-1:123456789012:my-queue"
        })
        @DisplayName("Should accept valid AWS service ARNs")
        void shouldAcceptValidServiceArns(String arn) {
            assertTrue(validator.isValid(arn, null), "ARN should be valid: " + arn);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "arn:aws-us-gov:s3:::my-bucket",
            "arn:aws-cn:s3:::my-bucket", 
            "arn:aws-iso:s3:::my-bucket",
            "arn:aws-iso-b:s3:::my-bucket"
        })
        @DisplayName("Should accept different AWS partitions")
        void shouldAcceptDifferentPartitions(String arn) {
            assertTrue(validator.isValid(arn, null), "ARN should be valid: " + arn);
        }
    }

    @Nested
    @DisplayName("Invalid ARNs")
    class InvalidArns {

        @BeforeEach
        void setUpNonOptional() {
            Arn annotation = new TestArnAnnotation(false);
            validator.initialize(annotation);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "not-an-arn",
            "arn:",
            "arn:aws:",
            "arn:aws:s3:",
            "arn:aws:s3::",
            "arn:aws:s3:::",
            "aws:s3:::my-bucket",
            "arn:azure:s3:::my-bucket",
            "arn:aws:s3::invalid-account:",
            "arn:aws::us-east-1:123456789012:bucket",
            "arn:aws:s3:us-east-1:123456789012:"
        })
        @DisplayName("Should reject malformed ARNs")
        void shouldRejectMalformedArns(String arn) {
            assertFalse(validator.isValid(arn, null), "ARN should be invalid: " + arn);
        }

        @Test
        @DisplayName("Should reject empty and null when not optional")
        void shouldRejectEmptyWhenNotOptional() {
            assertFalse(validator.isValid(null, null));
            assertFalse(validator.isValid("", null));
            assertFalse(validator.isValid("   ", null));
        }
    }

    @Nested
    @DisplayName("Optional ARN Handling")
    class OptionalArnHandling {

        @BeforeEach
        void setUpOptional() {
            Arn annotation = new TestArnAnnotation(true);
            validator.initialize(annotation);
        }

        @Test
        @DisplayName("Should accept null/empty when optional=true")
        void shouldAcceptNullEmptyWhenOptional() {
            assertTrue(validator.isValid(null, null));
            assertTrue(validator.isValid("", null));
            assertTrue(validator.isValid("   ", null));
        }

        @Test
        @DisplayName("Should still validate format when optional=true and value provided")
        void shouldValidateFormatWhenOptionalAndProvided() {
            assertTrue(validator.isValid("arn:aws:s3:::my-bucket", null));
            assertFalse(validator.isValid("invalid-arn", null));
        }
    }

    @Nested
    @DisplayName("Real-world Examples")
    class RealWorldExamples {

        @BeforeEach
        void setUpNonOptional() {
            Arn annotation = new TestArnAnnotation(false);
            validator.initialize(annotation);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "arn:aws:s3:::my-company-data-bucket",
            "arn:aws:s3:::my-bucket/documents/important-file.pdf",
            "arn:aws:iam::123456789012:user/developers/alice",
            "arn:aws:iam::123456789012:role/EC2-S3-Access-Role",
            "arn:aws:lambda:us-east-1:123456789012:function:ProcessOrders",
            "arn:aws:ec2:us-west-2:123456789012:instance/i-0123456789abcdef0",
            "arn:aws:rds:us-east-1:123456789012:db:production-database",
            "arn:aws:logs:us-east-1:123456789012:log-group:/aws/lambda/my-function:*",
            "arn:aws:sns:us-east-1:123456789012:order-notifications",
            "arn:aws:sqs:us-east-1:123456789012:order-processing-queue"
        })
        @DisplayName("Should accept real-world ARN examples")
        void shouldAcceptRealWorldArns(String arn) {
            assertTrue(validator.isValid(arn, null), "Real-world ARN should be valid: " + arn);
        }
    }

    // Simple test implementation of Arn annotation
    private static class TestArnAnnotation implements Arn {
        private final boolean optional;

        public TestArnAnnotation(boolean optional) {
            this.optional = optional;
        }

        @Override
        public String message() {
            return "invalid ARN";
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
        public boolean optional() {
            return optional;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return Arn.class;
        }
    }
}
