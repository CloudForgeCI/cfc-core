package com.cloudforgeci.api.core;

/**
 * Single source of truth for "does this deployment's ALB HTTPS listener end up wearing a
 * publicly-trusted certificate" — the same three-way decision {@code FargateRuntimeConfiguration}
 * makes when choosing which certificate to actually provision (imported ARN, DNS-validated
 * public, or the untrusted AWS Private CA fallback), extracted here so {@code ContainerFactory}
 * doesn't carry its own independently-maintained copy of that logic (a real drift risk — the two
 * classes used to duplicate this inline, one computing which cert to create, the other computing
 * whether to tell cloudforge-manager the result is trustworthy; if they ever disagreed, Manager's
 * license page would report the wrong thing about its own installation).
 *
 * <p>Deliberately a pure function of plain deployment-context values, not of any CDK construct or
 * {@code SystemContext} Slot — the real decision inside {@code FargateRuntimeConfiguration} is
 * resolved asynchronously (Slot callbacks, once the ALB/zone exist), but "would this configuration
 * result in a trusted cert" doesn't actually depend on any of that; it's fully determined by the
 * same four inputs a customer configures up front. This lets {@code ContainerFactory} compute the
 * answer synchronously at container-env-build time, and lets both classes' behavior be verified
 * with a single, fast, CDK-synthesis-free unit test ({@code TlsTrustEvaluatorTest}) instead of
 * only ever being exercised indirectly through a full stack synthesis.</p>
 */
public final class TlsTrustEvaluator {

    private TlsTrustEvaluator() {
    }

    /**
     * @param sslEnabled     {@code enableSsl} deployment-context value
     * @param domain         {@code domain} deployment-context value
     * @param fqdn           {@code fqdn} deployment-context value (subdomain+domain, or an
     *                       explicit override — see {@code DeploymentConfig#fqdn}'s javadoc)
     * @param certificateArn {@code certificateArn} deployment-context value — an already-issued/
     *                       imported ACM certificate, see {@code DeploymentConfig#certificateArn}
     * @return {@code true} only when the resulting certificate would be one a real browser
     *     already trusts: an imported/existing ACM cert (assumed public — that's the documented
     *     contract of the {@code certificateArn} field), or ACM's own DNS-validated public path
     *     (SSL enabled with a domain, and a Route53 zone this deployment controls). {@code false}
     *     for SSL disabled entirely, AND for SSL-enabled-with-no-domain — that combination takes
     *     {@code FargateRuntimeConfiguration}'s AWS Private CA fallback path, which the code that
     *     provisions it explicitly logs is NOT trusted by browsers.
     */
    public static boolean isPubliclyTrusted(
            boolean sslEnabled, String domain, String fqdn, String certificateArn) {
        if (notBlank(certificateArn)) {
            return true;
        }
        boolean haveHost = notBlank(domain) || notBlank(fqdn);
        return sslEnabled && haveHost;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
