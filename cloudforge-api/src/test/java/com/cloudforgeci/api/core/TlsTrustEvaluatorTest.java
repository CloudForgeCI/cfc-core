package com.cloudforgeci.api.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct coverage for {@link TlsTrustEvaluator} — the actual gate cloudforge-manager's License
 * page reads before deciding whether an embedded payment form is safe to show. The real-world
 * requirement this protects: a deployment must never be told its certificate is publicly trusted
 * when no ACM certificate (imported or DNS-validated) actually exists in the stack — only the
 * untrusted AWS Private CA fallback, or no certificate at all.
 */
class TlsTrustEvaluatorTest {

    @Test
    void noCertificateAtAllIsNotTrusted() {
        // SSL disabled entirely — no certificate of any kind is ever provisioned.
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(false, null, null, null));
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(false, "example.com", "app.example.com", null));
    }

    @Test
    void sslWithNoDomainAndNoImportedArnIsNotTrusted() {
        // SSL enabled, no domain, no certificateArn — FargateRuntimeConfiguration takes the AWS
        // Private CA fallback here, which its own code logs is NOT trusted by browsers. This is
        // the exact scenario that must never be reported as "publicly trusted."
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(true, null, null, null));
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(true, "", "", ""));
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(true, "   ", "   ", "   "));
    }

    @Test
    void sslWithDomainIsTrusted() {
        // DNS-validated public ACM path — a real certificate ends up in the stack.
        assertTrue(TlsTrustEvaluator.isPubliclyTrusted(true, "example.com", null, null));
        assertTrue(TlsTrustEvaluator.isPubliclyTrusted(true, null, "app.example.com", null));
    }

    @Test
    void domainAloneWithoutSslIsNotTrusted() {
        // Domain configured but SSL never enabled — no certificate at all, not even the Private
        // CA fallback (that only fires when ssl is true).
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(false, "example.com", "app.example.com", null));
    }

    @Test
    void importedCertificateArnIsTrustedEvenWithoutADomain() {
        // The whole point of the certificateArn field — a real, already-issued public cert,
        // no Route53 zone or domain required.
        assertTrue(TlsTrustEvaluator.isPubliclyTrusted(true, null, null,
            "arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012"));
    }

    @Test
    void blankCertificateArnIsTreatedAsAbsent() {
        // A blank/whitespace-only certificateArn must fall through to the domain-based check, not
        // be treated as "an ARN was provided."
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(true, null, null, ""));
        assertFalse(TlsTrustEvaluator.isPubliclyTrusted(true, null, null, "   "));
        assertTrue(TlsTrustEvaluator.isPubliclyTrusted(true, "example.com", null, "  "));
    }
}
