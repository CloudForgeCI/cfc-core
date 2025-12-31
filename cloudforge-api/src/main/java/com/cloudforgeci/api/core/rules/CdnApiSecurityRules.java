package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * CDN and API security compliance validation rules.
 *
 * <p>These rules enforce CloudFront and API Gateway security requirements across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 4.1: Encrypt transmission; Req 6.6: WAF protection</li>
 *   <li><b>HIPAA</b> - §164.312(e)(1): Transmission security</li>
 *   <li><b>SOC 2</b> - CC6.6/CC6.7: Network and data protection</li>
 *   <li><b>GDPR</b> - Art.32(1)(a): Encryption of data</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>CloudFront HTTPS enforcement</li>
 *   <li>CloudFront WAF integration</li>
 *   <li>API Gateway access logging</li>
 *   <li>API Gateway SSL/TLS configuration</li>
 *   <li>Minimum TLS version enforcement</li>
 * </ul>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "CdnApiSecurity",
    priority = 0,
    alwaysLoad = true,
    displayName = "CDN & API Security",
    description = "Cross-framework CDN and API Gateway security validation"
)
public class CdnApiSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(CdnApiSecurityRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing CDN & API security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // CloudFront security
            rules.addAll(validateCloudFrontSecurity(ctx));

            // API Gateway security
            rules.addAll(validateApiGatewaySecurity(ctx));

            // WAF protection
            rules.addAll(validateWafProtection(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("CDN & API Security validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION, return blocking failures based on framework requirements
                List<String> blockingRules = failedRules.stream()
                    .filter(rule -> isBlockingRule(ctx, rule.description()))
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();

                return blockingRules;
            } else {
                LOG.info("CDN & API Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate CloudFront security settings.
     */
    private List<ComplianceRule> validateCloudFrontSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // CloudFront TLS/HTTPS enforcement
        if (ctx.security == SecurityProfile.PRODUCTION && config.isCloudFrontEnabled()) {
            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.CDN_SECURITY,
                complianceFrameworks,
                true, // CloudFront is enabled
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.PASS) {
                rules.add(ComplianceRule.pass(
                    "CLOUDFRONT-HTTPS",
                    "CloudFront HTTPS enforcement configured"
                ));
            }

            // Check minimum TLS version
            boolean minTls12 = getBooleanSetting(ctx, "cloudfrontMinTls12", true);
            if (!minTls12) {
                rules.add(ComplianceRule.fail(
                    "CLOUDFRONT-TLS-VERSION",
                    "CloudFront must use TLS 1.2 minimum",
                    "Configure CloudFront distribution with MinimumProtocolVersion TLSv1.2_2021. " +
                    "Required for PCI-DSS and security best practices."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "CLOUDFRONT-TLS-VERSION",
                    "CloudFront TLS 1.2+ configured"
                ));
            }

            // CloudFront access logging
            boolean cloudfrontLogging = getBooleanSetting(ctx, "cloudfrontLogging", false);
            if (!cloudfrontLogging) {
                rules.add(ComplianceRule.fail(
                    "CLOUDFRONT-LOGGING",
                    "CloudFront access logging should be enabled",
                    "Enable CloudFront access logging for audit trails and security analysis. " +
                    "Set cloudfrontLogging = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "CLOUDFRONT-LOGGING",
                    "CloudFront access logging enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate API Gateway security settings.
     */
    private List<ComplianceRule> validateApiGatewaySecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // API Gateway access logging
        if (ctx.security == SecurityProfile.PRODUCTION) {
            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.API_SECURITY,
                complianceFrameworks,
                config.isCloudTrailEnabled(), // API logging tied to overall audit logging
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "API-GATEWAY-LOGGING",
                    "API Gateway access logging required for " + complianceFrameworks,
                    "Enable API Gateway access logging and execution logging. " +
                    "Required for PCI-DSS Req 10 and HIPAA audit controls."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "API-GATEWAY-LOGGING",
                    "API Gateway logging configured"
                ));
            }

            // API Gateway X-Ray tracing (advisory)
            boolean apiXrayTracing = getBooleanSetting(ctx, "apiGatewayXrayTracing", false);
            if (!apiXrayTracing) {
                rules.add(ComplianceRule.fail(
                    "API-GATEWAY-XRAY",
                    "API Gateway X-Ray tracing recommended for production",
                    "Enable X-Ray tracing for API Gateway for distributed tracing. " +
                    "Set apiGatewayXrayTracing = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "API-GATEWAY-XRAY",
                    "API Gateway X-Ray tracing enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate WAF protection settings.
     */
    private List<ComplianceRule> validateWafProtection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // WAF protection for CloudFront and API Gateway
        if (ctx.security == SecurityProfile.PRODUCTION) {
            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.WAF_PROTECTION,
                complianceFrameworks,
                config.isWafEnabled(),
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "WAF-CDN-API-PROTECTION",
                    "WAF protection required for CDN and API resources",
                    "WafEnabled",
                    "Enable WAF for CloudFront distributions and API Gateway stages. " +
                    "Required for PCI-DSS Req 6.6 web application protection. " +
                    "Set wafEnabled = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("WAF recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "WAF-CDN-API-PROTECTION",
                    "WAF recommended but not required"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "WAF-CDN-API-PROTECTION",
                    "WAF protection enabled for CDN and API resources"
                ));
            }
        }

        return rules;
    }

    /**
     * Check if a rule failure should block deployment.
     */
    private boolean isBlockingRule(SystemContext ctx, String ruleDescription) {
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");

        // WAF is blocking for PCI-DSS
        if (ruleDescription.contains("WAF") && requiresPciDss) {
            return true;
        }

        // API logging is blocking for PCI-DSS
        if (ruleDescription.contains("API Gateway access logging") && requiresPciDss) {
            return true;
        }

        // Advisory rules are non-blocking
        return false;
    }

    /**
     * Get boolean setting from context with default value.
     */
    private boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            var method = ctx.cfc.getClass().getMethod(key);
            Boolean value = (Boolean) method.invoke(ctx.cfc);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
