package com.cloudforgeci.samples.plugins.compliance;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.rules.ComplianceRule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Open Source Security Policy - Example Compliance Framework Plugin.
 *
 * <p>This demonstrates how to create a custom compliance framework plugin for open source
 * projects and SaaS vendors. It enforces security best practices commonly expected in
 * public-facing open source infrastructure.</p>
 *
 * <h2>Policy Areas:</h2>
 * <ul>
 *   <li>Supply Chain Security - Dependency scanning and SBOM generation</li>
 *   <li>Vulnerability Management - CVE tracking and security advisories</li>
 *   <li>License Compliance - Open source license validation</li>
 *   <li>Code Security - Static analysis and code signing</li>
 *   <li>Container Security - Image scanning and minimal base images</li>
 *   <li>Public Infrastructure - Rate limiting and DDoS protection</li>
 *   <li>Incident Response - Security.md and responsible disclosure</li>
 * </ul>
 *
 * <h2>Deployment:</h2>
 * <ul>
 *   <li><b>Advisory Mode:</b> Provides recommendations without blocking</li>
 *   <li><b>Priority 65:</b> Runs after industry frameworks, demonstrates custom layering</li>
 *   <li><b>Opt-in:</b> Enable explicitly (alwaysLoad = false)</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Enable in cdk.json:
 * {
 *   "context": {
 *     "complianceFrameworks": "OpenSourceSecurity"
 *   }
 * }
 * }</pre>
 *
 * <h2>Example Use Cases:</h2>
 * <ul>
 *   <li>Open source project infrastructure (CI/CD, artifact hosting)</li>
 *   <li>SaaS vendor public-facing services</li>
 *   <li>Community platforms and developer tools</li>
 *   <li>Public API infrastructure</li>
 * </ul>
 *
 * @since 3.0.0
 * @author CloudForge Community
 */
@ComplianceFramework(
    value = "OpenSourceSecurity",
    priority = 65,
    alwaysLoad = false,  // Opt-in for open source projects
    displayName = "Open Source Security Policy",
    description = "Security best practices for open source projects and public SaaS infrastructure"
)
public class OpenSourceSecurityPolicyRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(OpenSourceSecurityPolicyRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing Open Source Security Policy compliance rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Supply chain security validation
            rules.addAll(validateSupplyChainSecurity(ctx));

            // Vulnerability management validation
            rules.addAll(validateVulnerabilityManagement(ctx));

            // Container security (runtime-specific)
            if (ctx.runtime == RuntimeType.FARGATE) {
                rules.addAll(validateContainerSecurity(ctx));
            }

            // Public infrastructure security
            rules.addAll(validatePublicInfrastructure(ctx));

            // Incident response and disclosure
            rules.addAll(validateIncidentResponse(ctx));

            // Monitoring and transparency
            rules.addAll(validateMonitoringTransparency(ctx));

            // Convert ComplianceRule list to error strings
            // For open source, we typically use advisory mode
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(java.util.Optional::stream)
                .toList();
        });
    }

    /**
     * Validate supply chain security controls.
     *
     * <p>OSS Policy SC-001: Software Supply Chain Security</p>
     * <ul>
     *   <li>Dependency scanning for known vulnerabilities</li>
     *   <li>SBOM (Software Bill of Materials) generation</li>
     *   <li>Provenance and build attestation</li>
     *   <li>Signed artifacts and container images</li>
     * </ul>
     */
    private List<ComplianceRule> validateSupplyChainSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // SC-001.1: Container Image Scanning (Amazon Inspector for Fargate)
        if (ctx.runtime == RuntimeType.FARGATE) {
            boolean inspectorEnabled = getBooleanSetting(ctx, "inspectorEnabled", false);
            if (!inspectorEnabled) {
                rules.add(ComplianceRule.pass(
                    "OSS-SC-001.1",
                    "OSS Policy SC-001.1: Enable Amazon Inspector for container vulnerability scanning (recommended for public services)"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "OSS-SC-001.1",
                    "OSS Policy SC-001.1: Container image vulnerability scanning enabled"
                ));
            }
        }

        // SC-001.2: SBOM Generation
        boolean sbomEnabled = getBooleanSetting(ctx, "generateSbom", false);
        if (!sbomEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-SC-001.2",
                "OSS Policy SC-001.2: Consider generating SBOM (Software Bill of Materials) for transparency"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "OSS-SC-001.2",
                "OSS Policy SC-001.2: SBOM generation enabled for supply chain transparency"
            ));
        }

        // SC-001.3: Build Provenance
        boolean provenanceEnabled = getBooleanSetting(ctx, "buildProvenance", false);
        if (!provenanceEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-SC-001.3",
                "OSS Policy SC-001.3: Consider enabling build provenance/attestation for production deployments"
            ));
        } else if (provenanceEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-SC-001.3",
                "OSS Policy SC-001.3: Build provenance enabled for verifiable builds"
            ));
        }

        return rules;
    }

    /**
     * Validate vulnerability management controls.
     *
     * <p>OSS Policy VM-002: Vulnerability Detection and Response</p>
     * <ul>
     *   <li>Automated vulnerability scanning</li>
     *   <li>Security advisory publishing</li>
     *   <li>CVE tracking and remediation</li>
     *   <li>Patch management process</li>
     * </ul>
     */
    private List<ComplianceRule> validateVulnerabilityManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // VM-002.1: Threat Detection (GuardDuty)
        boolean guardDutyEnabled = config.isGuardDutyEnabled();
        if (!guardDutyEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-VM-002.1",
                "OSS Policy VM-002.1: Enable AWS GuardDuty for threat detection in production environments"
            ));
        } else if (guardDutyEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-VM-002.1",
                "OSS Policy VM-002.1: Threat detection enabled (GuardDuty)"
            ));
        }

        // VM-002.2: Security Monitoring
        boolean securityMonitoring = config.isSecurityMonitoringEnabled();
        if (securityMonitoring) {
            rules.add(ComplianceRule.pass(
                "OSS-VM-002.2",
                "OSS Policy VM-002.2: Security monitoring enabled for continuous vulnerability detection"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-VM-002.2",
                "OSS Policy VM-002.2: Enable security monitoring for production public services"
            ));
        }

        // VM-002.3: Automated Patching Strategy
        String patchStrategy = ctx.cfc.getContextValue("autoPatchStrategy", "none");
        if ("none".equals(patchStrategy) && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-VM-002.3",
                "OSS Policy VM-002.3: Define automated patching strategy for public-facing services"
            ));
        } else if (!"none".equals(patchStrategy)) {
            rules.add(ComplianceRule.pass(
                "OSS-VM-002.3",
                "OSS Policy VM-002.3: Automated patching strategy configured (" + patchStrategy + ")"
            ));
        }

        return rules;
    }

    /**
     * Validate container security for public images.
     *
     * <p>OSS Policy CS-003: Container Image Security</p>
     * <ul>
     *   <li>Minimal base images (distroless, alpine)</li>
     *   <li>Non-root container users</li>
     *   <li>Image signing and verification</li>
     *   <li>Regular image updates</li>
     * </ul>
     */
    private List<ComplianceRule> validateContainerSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // CS-003.1: Minimal Base Images
        String baseImage = ctx.cfc.getContextValue("containerImage", "");
        boolean usesMinimalImage = baseImage.contains("alpine") ||
                                   baseImage.contains("distroless") ||
                                   baseImage.contains("scratch");

        if (!usesMinimalImage && !baseImage.isEmpty()) {
            rules.add(ComplianceRule.pass(
                "OSS-CS-003.1",
                "OSS Policy CS-003.1: Consider using minimal base images (alpine, distroless) for reduced attack surface"
            ));
        } else if (usesMinimalImage) {
            rules.add(ComplianceRule.pass(
                "OSS-CS-003.1",
                "OSS Policy CS-003.1: Minimal base image in use (reduced attack surface)"
            ));
        }

        // CS-003.2: Non-root Container User
        boolean runAsNonRoot = getBooleanSetting(ctx, "runAsNonRoot", true);
        if (!runAsNonRoot) {
            rules.add(ComplianceRule.pass(
                "OSS-CS-003.2",
                "OSS Policy CS-003.2: Containers should run as non-root user for security"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "OSS-CS-003.2",
                "OSS Policy CS-003.2: Container configured to run as non-root user"
            ));
        }

        // CS-003.3: Image Signing
        boolean imageSigningEnabled = getBooleanSetting(ctx, "signContainerImages", false);
        if (!imageSigningEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-CS-003.3",
                "OSS Policy CS-003.3: Consider signing container images for production deployments (e.g., Cosign, Notary)"
            ));
        } else if (imageSigningEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-CS-003.3",
                "OSS Policy CS-003.3: Container image signing enabled for supply chain security"
            ));
        }

        return rules;
    }

    /**
     * Validate public infrastructure security.
     *
     * <p>OSS Policy PI-004: Public-Facing Infrastructure Security</p>
     * <ul>
     *   <li>DDoS protection (CloudFront, WAF)</li>
     *   <li>Rate limiting for API endpoints</li>
     *   <li>TLS/SSL for all public endpoints</li>
     *   <li>Security headers (HSTS, CSP, etc.)</li>
     * </ul>
     */
    private List<ComplianceRule> validatePublicInfrastructure(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // PI-004.1: WAF for DDoS and Attack Protection
        boolean wafEnabled = config.isWafEnabled();
        if (!wafEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "OSS-PI-004.1",
                "OSS Policy PI-004.1: WAF required for production public services (DDoS protection)",
                "Enable AWS WAF for public-facing infrastructure"
            ));
        } else if (wafEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-PI-004.1",
                "OSS Policy PI-004.1: WAF enabled for DDoS and attack protection"
            ));
        }

        // PI-004.2: Rate Limiting
        boolean rateLimitingEnabled = getBooleanSetting(ctx, "enableRateLimiting", false);
        if (!rateLimitingEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-PI-004.2",
                "OSS Policy PI-004.2: Consider enabling rate limiting for public API endpoints"
            ));
        } else if (rateLimitingEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-PI-004.2",
                "OSS Policy PI-004.2: Rate limiting enabled for API protection"
            ));
        }

        // PI-004.3: TLS/SSL for Public Endpoints
        boolean sslEnabled = getBooleanSetting(ctx, "enableSsl", false);
        if (!sslEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "OSS-PI-004.3",
                "OSS Policy PI-004.3: TLS/SSL required for production public endpoints",
                "Enable SSL/TLS for all public-facing services"
            ));
        } else if (sslEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-PI-004.3",
                "OSS Policy PI-004.3: TLS/SSL enabled for secure public access"
            ));
        }

        // PI-004.4: Security Headers
        boolean securityHeadersEnabled = getBooleanSetting(ctx, "enableSecurityHeaders", false);
        if (!securityHeadersEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-PI-004.4",
                "OSS Policy PI-004.4: Consider enabling security headers (HSTS, CSP, X-Frame-Options)"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "OSS-PI-004.4",
                "OSS Policy PI-004.4: Security headers configured for browser protection"
            ));
        }

        return rules;
    }

    /**
     * Validate incident response and disclosure processes.
     *
     * <p>OSS Policy IR-005: Incident Response and Responsible Disclosure</p>
     * <ul>
     *   <li>SECURITY.md file in repository</li>
     *   <li>Security contact/email published</li>
     *   <li>Vulnerability disclosure policy</li>
     *   <li>Incident response plan</li>
     * </ul>
     */
    private List<ComplianceRule> validateIncidentResponse(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // IR-005.1: Security Contact Information
        String securityContact = ctx.cfc.getContextValue("securityContact", "");
        if (securityContact.isEmpty()) {
            rules.add(ComplianceRule.pass(
                "OSS-IR-005.1",
                "OSS Policy IR-005.1: Publish security contact email for vulnerability reports (e.g., security@example.com)"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "OSS-IR-005.1",
                "OSS Policy IR-005.1: Security contact published (" + securityContact + ")"
            ));
        }

        // IR-005.2: Vulnerability Disclosure Policy
        boolean hasDisclosurePolicy = getBooleanSetting(ctx, "hasSecurityMd", false);
        if (!hasDisclosurePolicy) {
            rules.add(ComplianceRule.pass(
                "OSS-IR-005.2",
                "OSS Policy IR-005.2: Create SECURITY.md with vulnerability disclosure policy and reporting instructions"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "OSS-IR-005.2",
                "OSS Policy IR-005.2: SECURITY.md exists with responsible disclosure policy"
            ));
        }

        // IR-005.3: Incident Response Monitoring
        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config != null && config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.pass(
                "OSS-IR-005.3",
                "OSS Policy IR-005.3: Security monitoring enabled for incident detection"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-IR-005.3",
                "OSS Policy IR-005.3: Enable security monitoring for production incident response"
            ));
        }

        return rules;
    }

    /**
     * Validate monitoring and transparency.
     *
     * <p>OSS Policy MT-006: Monitoring and Transparency</p>
     * <ul>
     *   <li>Public status page for service health</li>
     *   <li>Audit logging for public APIs</li>
     *   <li>Public security advisories</li>
     *   <li>Transparent incident communication</li>
     * </ul>
     */
    private List<ComplianceRule> validateMonitoringTransparency(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // MT-006.1: Audit Logging (CloudTrail)
        boolean cloudTrailEnabled = config.isCloudTrailEnabled();
        if (!cloudTrailEnabled && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-MT-006.1",
                "OSS Policy MT-006.1: Enable CloudTrail for audit logging of public API access"
            ));
        } else if (cloudTrailEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-MT-006.1",
                "OSS Policy MT-006.1: Audit logging enabled (CloudTrail) for transparency"
            ));
        }

        // MT-006.2: Public Status Page
        boolean hasStatusPage = getBooleanSetting(ctx, "hasPublicStatusPage", false);
        if (!hasStatusPage && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-MT-006.2",
                "OSS Policy MT-006.2: Consider publishing a public status page for service health transparency"
            ));
        } else if (hasStatusPage) {
            rules.add(ComplianceRule.pass(
                "OSS-MT-006.2",
                "OSS Policy MT-006.2: Public status page configured for service transparency"
            ));
        }

        // MT-006.3: Network Flow Monitoring
        boolean flowLogsEnabled = config.isFlowLogsEnabled();
        if (flowLogsEnabled) {
            rules.add(ComplianceRule.pass(
                "OSS-MT-006.3",
                "OSS Policy MT-006.3: Network flow logs enabled for traffic analysis"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "OSS-MT-006.3",
                "OSS Policy MT-006.3: Enable VPC flow logs for production network monitoring"
            ));
        }

        return rules;
    }

    // ========== Helper Methods ==========

    /**
     * Helper method to safely get boolean settings from deployment context.
     */
    private boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
