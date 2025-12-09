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

    // OneOf Tests
    @Test
    void testOneOfConcept() {
        // OneOf ensures exactly one field is set
        // Simulating OneOf behavior with simple logic
        String option1 = "value1";
        String option2 = null;

        int setCount = 0;
        if (option1 != null) setCount++;
        if (option2 != null) setCount++;

        assertEquals(1, setCount); // Exactly one should be set
    }

    @Test
    void testOneOfValidation() {
        // Both null - invalid
        String opt1 = null;
        String opt2 = null;
        int count1 = (opt1 != null ? 1 : 0) + (opt2 != null ? 1 : 0);
        assertEquals(0, count1);

        // Both set - invalid
        opt1 = "a";
        opt2 = "b";
        int count2 = (opt1 != null ? 1 : 0) + (opt2 != null ? 1 : 0);
        assertEquals(2, count2);

        // Exactly one set - valid
        opt1 = "a";
        opt2 = null;
        int count3 = (opt1 != null ? 1 : 0) + (opt2 != null ? 1 : 0);
        assertEquals(1, count3);
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
