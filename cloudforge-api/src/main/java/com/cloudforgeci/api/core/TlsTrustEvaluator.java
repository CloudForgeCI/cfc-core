package com.cloudforgeci.api.core;

/**
 * Single source of truth for whether a deployment's ALB HTTPS listener uses a publicly trusted
 * certificate. Mirrors the three-way choice {@code FargateRuntimeConfiguration} makes when
 * provisioning a certificate (imported ARN, DNS-validated public certificate, or the untrusted AWS
 * Private CA fallback) so that {@code ContainerFactory} can report the result to the application
 * (e.g. cloudforge-manager) without duplicating the logic.
 *
 * <p>A pure function of deployment-context values rather than CDK constructs or
 * {@code SystemContext} slots: {@code FargateRuntimeConfiguration} resolves its decision
 * asynchronously, but the outcome is fully determined by the inputs configured up front. This
 * lets {@code ContainerFactory} compute it synchronously and lets {@code TlsTrustEvaluatorTest}
 * verify it without synthesizing a stack.</p>
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
