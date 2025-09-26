package com.cloudforgeci.api.core.utilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for DnsLabel validation annotation.
 * Tests validation logic without external dependencies.
 */
@DisplayName("DnsLabel Validation Tests")
class DnsLabelSimpleTest {

    private DnsLabel.Validator validator;

    @BeforeEach
    void setUp() {
        validator = new DnsLabel.Validator();
    }

    @Nested
    @DisplayName("Valid DNS Labels")
    class ValidDnsLabels {

        @Test
        @DisplayName("Should accept null or blank values")
        void shouldAcceptNullOrBlank() {
            assertTrue(validator.isValid(null, null));
            assertTrue(validator.isValid("", null));
            assertTrue(validator.isValid("   ", null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "a",
            "A", 
            "0",
            "9",
            "test",
            "Test",
            "TEST",
            "test123",
            "123test",
            "Test123"
        })
        @DisplayName("Should accept simple alphanumeric labels")
        void shouldAcceptAlphanumericLabels(String label) {
            assertTrue(validator.isValid(label, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "test-label",
            "my-domain",
            "api-v2",
            "service-name",
            "multi-word-label",
            "a-b-c-d-e",
            "test-123",
            "123-test"
        })
        @DisplayName("Should accept labels with hyphens")
        void shouldAcceptLabelsWithHyphens(String label) {
            assertTrue(validator.isValid(label, null));
        }

        @Test
        @DisplayName("Should accept maximum length label (63 characters)")
        void shouldAcceptMaxLengthLabel() {
            // 63 chars: starts and ends with alphanumeric, can have hyphens in between
            String maxLabel = "a" + "b".repeat(61) + "c"; // 63 chars total
            assertTrue(validator.isValid(maxLabel, null));
        }

        @Test
        @DisplayName("Should accept labels with alternating characters")
        void shouldAcceptAlternatingCharacters() {
            assertTrue(validator.isValid("a1a1a1", null));
            assertTrue(validator.isValid("1a1a1a", null));
            assertTrue(validator.isValid("a-1-a-1", null));
        }

        @Test
        @DisplayName("Should accept mixed case labels")
        void shouldAcceptMixedCase() {
            assertTrue(validator.isValid("AbC", null));
            assertTrue(validator.isValid("tEsT", null));
            assertTrue(validator.isValid("MiXeD-CaSe", null));
        }
    }

    @Nested
    @DisplayName("Invalid DNS Labels")
    class InvalidDnsLabels {

        @ParameterizedTest
        @ValueSource(strings = {
            "-test",
            "-invalid",
            "-123",
            "-a"
        })
        @DisplayName("Should reject labels starting with hyphen")
        void shouldRejectLabelsStartingWithHyphen(String label) {
            assertFalse(validator.isValid(label, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "test-",
            "invalid-",
            "123-",
            "a-"
        })
        @DisplayName("Should reject labels ending with hyphen")
        void shouldRejectLabelsEndingWithHyphen(String label) {
            assertFalse(validator.isValid(label, null));
        }

        @Test
        @DisplayName("Should reject labels longer than 63 characters")
        void shouldRejectTooLongLabels() {
            String tooLong = "a".repeat(64); // 64 chars
            assertFalse(validator.isValid(tooLong, null));
            
            String wayTooLong = "a".repeat(100); // 100 chars
            assertFalse(validator.isValid(wayTooLong, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "test.invalid",     // Contains dot
            "test_invalid",     // Contains underscore
            "test invalid",     // Contains space
            "test@invalid",     // Contains @
            "test#invalid",     // Contains #
            "test$invalid",     // Contains $
            "test%invalid",     // Contains %
            "test&invalid",     // Contains &
            "test*invalid",     // Contains *
            "test+invalid",     // Contains +
            "test=invalid",     // Contains =
            "test?invalid",     // Contains ?
            "test[invalid",     // Contains [
            "test]invalid",     // Contains ]
            "test{invalid",     // Contains {
            "test}invalid",     // Contains }
            "test|invalid",     // Contains |
            "test\\invalid",    // Contains backslash
            "test\"invalid",    // Contains quote
            "test'invalid",     // Contains apostrophe
            "test<invalid",     // Contains <
            "test>invalid",     // Contains >
            "test,invalid",     // Contains comma
            "test;invalid",     // Contains semicolon
            "test:invalid",     // Contains colon
            "test/invalid",     // Contains slash
        })
        @DisplayName("Should reject labels with invalid characters")
        void shouldRejectInvalidCharacters(String label) {
            assertFalse(validator.isValid(label, null));
        }

        @Test
        @DisplayName("Should reject labels with only hyphens")
        void shouldRejectOnlyHyphens() {
            assertFalse(validator.isValid("-", null));
            assertFalse(validator.isValid("--", null));
            assertFalse(validator.isValid("---", null));
        }

        @Test
        @DisplayName("Should reject unicode characters")
        void shouldRejectUnicodeCharacters() {
            assertFalse(validator.isValid("тест", null));      // Cyrillic
            assertFalse(validator.isValid("例え", null));       // Japanese
            assertFalse(validator.isValid("café", null));      // Accented characters
            assertFalse(validator.isValid("señor", null));     // Spanish characters
            assertFalse(validator.isValid("naïve", null));     // French characters
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle boundary lengths correctly")
        void shouldHandleBoundaryLengths() {
            // 1 char (valid)
            assertTrue(validator.isValid("a", null));
            
            // 2 chars (valid)
            assertTrue(validator.isValid("ab", null));
            
            // 62 chars (valid)
            String label62 = "a" + "b".repeat(60) + "c";
            assertTrue(validator.isValid(label62, null));
            
            // 63 chars (valid - maximum)
            String label63 = "a" + "b".repeat(61) + "c";
            assertTrue(validator.isValid(label63, null));
            
            // 64 chars (invalid)
            String label64 = "a" + "b".repeat(62) + "c";
            assertFalse(validator.isValid(label64, null));
        }

        @Test
        @DisplayName("Should handle labels that are only start/end valid chars")
        void shouldHandleMinimalValidLabels() {
            assertTrue(validator.isValid("a", null));
            assertTrue(validator.isValid("1", null));
            assertTrue(validator.isValid("Z", null));
            assertTrue(validator.isValid("9", null));
        }

        @Test
        @DisplayName("Should handle internal hyphen patterns")
        void shouldHandleInternalHyphens() {
            assertTrue(validator.isValid("a-b", null));
            assertTrue(validator.isValid("a--b", null));
            assertTrue(validator.isValid("a---b", null));
            assertTrue(validator.isValid("a-b-c", null));
            assertTrue(validator.isValid("1-2-3", null));
        }
    }

    @Nested
    @DisplayName("Real-world Examples")
    class RealWorldExamples {

        @ParameterizedTest
        @ValueSource(strings = {
            "www",
            "api",
            "mail",
            "ftp",
            "blog",
            "shop",
            "admin",
            "app",
            "mobile",
            "test",
            "dev",
            "staging",
            "prod",
            "production"
        })
        @DisplayName("Should accept common subdomain labels")
        void shouldAcceptCommonSubdomainLabels(String label) {
            assertTrue(validator.isValid(label, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "api-v1",
            "api-v2",
            "my-app",
            "user-service",
            "order-api",
            "payment-gateway",
            "auth-service",
            "cdn-1",
            "cdn-2",
            "lb-primary",
            "db-replica-1"
        })
        @DisplayName("Should accept hyphenated service names")
        void shouldAcceptHyphenatedServiceNames(String label) {
            assertTrue(validator.isValid(label, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "test.",           // Trailing dot
            ".test",           // Leading dot
            "test..invalid",   // Double dot
            "test.test",       // Internal dot (this is multiple labels)
            "test_service",    // Underscore
            "test service",    // Space
            "test/path",       // Slash
            "-test",           // Leading hyphen
            "test-",           // Trailing hyphen
        })
        @DisplayName("Should reject common invalid patterns")
        void shouldRejectCommonInvalidPatterns(String label) {
            assertFalse(validator.isValid(label, null));
        }
    }
}
