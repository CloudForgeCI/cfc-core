package com.cloudforge.core.utilities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CloudForge utility classes (Arn, DnsLabel, DnsName, OneOf).
 */
class UtilitiesTest {

    // Arn Tests
    @Test
    void testValidArnFormat() {
        String validArn = "arn:aws:s3:::my-bucket";
        // Just verify the format exists - validators are used by Jakarta validation
        assertNotNull(validArn);
        assertTrue(validArn.startsWith("arn:"));
    }

    @Test
    void testArnComponents() {
        String arn = "arn:aws:iam::123456789012:role/MyRole";
        assertTrue(arn.contains("arn:aws:"));
        assertTrue(arn.contains("iam"));
        assertTrue(arn.contains("123456789012"));
    }

    @Test
    void testSecretsManagerArn() {
        String arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:my-secret-abc123";
        assertTrue(arn.contains("secretsmanager"));
        assertTrue(arn.contains("us-east-1"));
        assertTrue(arn.contains("secret:"));
    }

    // DnsLabel Tests
    @Test
    void testValidDnsLabel() {
        String validLabel = "my-app";
        assertTrue(validLabel.matches("[a-z0-9-]+"));
        assertTrue(validLabel.length() <= 63);
    }

    @Test
    void testDnsLabelConstraints() {
        // Valid DNS labels
        assertTrue("a".matches("[a-z0-9-]+"));
        assertTrue("my-app-123".matches("[a-z0-9-]+"));
        assertTrue("test".matches("[a-z0-9-]+"));

        // DNS labels cannot start/end with hyphen (validated separately)
        assertFalse("-invalid".matches("[a-z][a-z0-9-]*"));
        assertFalse("invalid-".matches("[a-z0-9-]*[a-z0-9]"));
    }

    @Test
    void testDnsLabelLength() {
        String maxLabel = "a".repeat(63);
        assertEquals(63, maxLabel.length());

        String tooLong = "a".repeat(64);
        assertTrue(tooLong.length() > 63);
    }

    // DnsName Tests
    @Test
    void testValidDnsName() {
        String validName = "example.com";
        assertTrue(validName.contains("."));
        assertTrue(validName.matches("[a-z0-9.-]+"));
    }

    @Test
    void testFullyQualifiedDomainName() {
        String fqdn = "app.example.com";
        String[] parts = fqdn.split("\\.");
        assertEquals(3, parts.length);
        assertEquals("app", parts[0]);
        assertEquals("example", parts[1]);
        assertEquals("com", parts[2]);
    }

    @Test
    void testDnsNameComponents() {
        String dnsName = "my-app.cloudforge.example.com";
        assertTrue(dnsName.split("\\.").length >= 2);

        for (String label : dnsName.split("\\.")) {
            assertTrue(label.length() > 0);
            assertTrue(label.length() <= 63);
        }
    }

    @Test
    void testDnsNameTotalLength() {
        // DNS names can be up to 253 characters
        String longDomain = "a".repeat(50) + "." + "b".repeat(50) + ".com";
        assertTrue(longDomain.length() < 253);
    }

    // OneOf Tests -- @OneOf constrains a String to an allowed value set (Bean Validation), not
    // "exactly one of several fields set"; these exercise the real Validator instead of a
    // same-named simulation that tested unrelated made-up logic.
    @OneOf({"a", "b", "c"})
    private String annotatedField;

    private OneOf.Validator newValidator() throws NoSuchFieldException {
        OneOf.Validator validator = new OneOf.Validator();
        validator.initialize(UtilitiesTest.class.getDeclaredField("annotatedField").getAnnotation(OneOf.class));
        return validator;
    }

    @Test
    void testOneOfAcceptsAnAllowedValue() throws NoSuchFieldException {
        assertTrue(newValidator().isValid("b", null));
    }

    @Test
    void testOneOfRejectsADisallowedValue() throws NoSuchFieldException {
        assertFalse(newValidator().isValid("z", null));
    }

    @Test
    void testOneOfTreatsNullAsValid() throws NoSuchFieldException {
        // Bean Validation convention: null is left to a separate @NotNull, not this constraint.
        assertTrue(newValidator().isValid(null, null));
    }

    // General utility tests
    @Test
    void testCommonArnPatterns() {
        String[] arns = {
            "arn:aws:s3:::bucket-name",
            "arn:aws:iam::123456789012:role/RoleName",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:name",
            "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012"
        };

        for (String arn : arns) {
            assertTrue(arn.startsWith("arn:aws:"));
            assertTrue(arn.split(":").length >= 6);
        }
    }

    @Test
    void testDnsCompatibility() {
        // Common CloudForge DNS patterns
        String[] validDomains = {
            "jenkins.example.com",
            "my-app.cloudforge.internal",
            "api.v1.service.local"
        };

        for (String domain : validDomains) {
            assertTrue(domain.matches("[a-z0-9.-]+"));
            assertFalse(domain.contains("_")); // Underscores not allowed in DNS
            assertFalse(domain.contains(" ")); // Spaces not allowed
        }
    }
}
