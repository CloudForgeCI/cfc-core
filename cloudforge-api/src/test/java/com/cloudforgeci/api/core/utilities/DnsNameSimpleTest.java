package com.cloudforgeci.api.core.utilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for DnsName validation annotation.
 * Tests validation logic without external dependencies.
 */
@DisplayName("DnsName Validation Tests")
class DnsNameSimpleTest {

    private DnsName.Validator validator;

    @BeforeEach
    void setUp() {
        validator = new DnsName.Validator();
    }

    @Nested
    @DisplayName("Valid DNS Names")
    class ValidDnsNames {

        @Test
        @DisplayName("Should accept null values")
        void shouldAcceptNull() {
            assertTrue(validator.isValid(null, null));
        }

        @Test
        @DisplayName("Should accept empty values")
        void shouldAcceptEmpty() {
            assertTrue(validator.isValid("", null));
            assertTrue(validator.isValid("   ", null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "example.com",
            "test.org",
            "domain.net",
            "subdomain.example.com",
            "api.service.example.com",
            "test-domain.com",
            "multi-word-domain.example.com",
            "123.example.com",
            "v2.api.example.com",
            "test123.example.com"
        })
        @DisplayName("Should accept valid domain names")
        void shouldAcceptValidDomains(String domain) {
            assertTrue(validator.isValid(domain, null));
        }

        @Test
        @DisplayName("Should accept maximum length labels")
        void shouldAcceptMaxLengthLabels() {
            String maxLabel = "a".repeat(61) + "bc"; // 63 chars total
            assertTrue(validator.isValid(maxLabel + ".example.com", null));
        }
    }

    @Nested
    @DisplayName("Invalid DNS Names")
    class InvalidDnsNames {

        @ParameterizedTest
        @ValueSource(strings = {
            "-test.com",
            "test-.com",
            ".com",
            "test invalid",
            "test@invalid",
            "test#invalid",
            "test$invalid",
            "test%invalid",
            "test&invalid",
            "test*invalid",
            "test+invalid",
            "test=invalid",
            "test?invalid",
            "test[invalid",
            "test]invalid",
            "test{invalid",
            "test}invalid",
            "test|invalid",
            "test\\invalid",
            "test\"invalid",
            "test'invalid",
            "test<invalid",
            "test>invalid",
            "test,invalid",
            "test;invalid",
            "test:invalid",
            "test/invalid"
        })
        @DisplayName("Should reject invalid domain names")
        void shouldRejectInvalidDomains(String domain) {
            assertFalse(validator.isValid(domain, null));
        }

        @Test
        @DisplayName("Should reject labels longer than 63 characters")
        void shouldRejectLongLabels() {
            String tooLongLabel = "a".repeat(64); // 64 chars
            assertFalse(validator.isValid(tooLongLabel + ".com", null));
        }

        @Test
        @DisplayName("Should reject total length longer than 253 characters")
        void shouldRejectTotalLengthTooLong() {
            // Build 63.63.63.62 (with 3 dots) = 254 total characters
            String l63 = "a".repeat(63);
            String l62 = "a".repeat(62);
            String longDomain = String.join(".", l63, l63, l63, l62); // 254 chars total
            assertFalse(validator.isValid(longDomain, null));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle single character labels")
        void shouldHandleSingleCharLabels() {
            assertTrue(validator.isValid("a.com", null));
            assertTrue(validator.isValid("1.com", null));
            assertTrue(validator.isValid("a.b.c", null));
        }

        @Test
        @DisplayName("Should handle mixed case")
        void shouldHandleMixedCase() {
            assertTrue(validator.isValid("Example.COM", null));
            assertTrue(validator.isValid("SubDomain.Example.Com", null));
        }

        @Test
        @DisplayName("Should handle boundary length cases")
        void shouldHandleBoundaryLengths() {
            // 62 chars (valid)
            String label62 = "a".repeat(60) + "bc";
            assertTrue(validator.isValid(label62 + ".com", null));
            
            // 63 chars (valid - maximum)
            String label63 = "a".repeat(61) + "bc";
            assertTrue(validator.isValid(label63 + ".com", null));
            
            // 64 chars (invalid)
            String label64 = "a".repeat(62) + "bc";
            assertFalse(validator.isValid(label64 + ".com", null));
        }
    }

    @Nested
    @DisplayName("Real-world Examples")
    class RealWorldExamples {

        @ParameterizedTest
        @ValueSource(strings = {
            "google.com",
            "www.google.com",
            "mail.google.com",
            "api.github.com",
            "jenkins.example.org",
            "my-service.aws.amazon.com",
            "test-environment.cloudforgeci.com",
            "v2.api.service.example.com",
            "123.456.789.example.com"
        })
        @DisplayName("Should accept common valid domains")
        void shouldAcceptCommonValidDomains(String domain) {
            assertTrue(validator.isValid(domain, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "-google.com",
            "google-.com",
            ".google.com",
            "user@google.com",
            "google.com:443"
        })
        @DisplayName("Should reject common invalid domains")
        void shouldRejectCommonInvalidDomains(String domain) {
            assertFalse(validator.isValid(domain, null));
        }
    }
}
