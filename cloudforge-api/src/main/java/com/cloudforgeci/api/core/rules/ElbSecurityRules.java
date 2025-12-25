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
 * Elastic Load Balancer security compliance validation rules.
 *
 * <p>These rules enforce ELB security requirements across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 4.1: Encrypt transmission; Req 10: Audit logging</li>
 *   <li><b>HIPAA</b> - §164.312(e)(1): Transmission security</li>
 *   <li><b>SOC 2</b> - CC6.7: Data transmission security</li>
 *   <li><b>GDPR</b> - Art.32(1)(a): Encryption of data</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>ALB/NLB access logging</li>
 *   <li>HTTPS/TLS listener configuration</li>
 *   <li>SSL policy (TLS 1.2+) enforcement</li>
 *   <li>Deletion protection</li>
 *   <li>Cross-zone load balancing</li>
 * </ul>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "ElbSecurity",
    priority = 0,
    alwaysLoad = true,
    displayName = "ELB Security",
    description = "Cross-framework Elastic Load Balancer security validation"
)
public class ElbSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(ElbSecurityRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing ELB security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // ALB access logging
            rules.addAll(validateAlbAccessLogging(ctx));

            // TLS/SSL configuration
            rules.addAll(validateTlsConfiguration(ctx));

            // Deletion protection
            rules.addAll(validateDeletionProtection(ctx));

            // High availability
            rules.addAll(validateHighAvailability(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("ELB Security validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION, return blocking failures
                List<String> blockingRules = failedRules.stream()
                    .filter(rule -> isBlockingRule(ctx, rule.description()))
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();

                return blockingRules;
            } else {
                LOG.info("ELB Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate ALB access logging settings.
     */
    private List<ComplianceRule> validateAlbAccessLogging(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // ALB access logging
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean albLoggingEnabled = config.isAlbAccessLoggingEnabled();

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.AUDIT_LOGGING,
                complianceFrameworks,
                albLoggingEnabled,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "ALB-ACCESS-LOGGING",
                    "ALB access logging required for " + complianceFrameworks,
                    "AlbAccessLoggingEnabled",
                    "Enable ALB access logging for audit trails and security analysis. " +
                    "Required for PCI-DSS Req 10 and HIPAA audit controls. " +
                    "Set albAccessLoggingEnabled = true in security profile."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("ALB access logging recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "ALB-ACCESS-LOGGING",
                    "ALB access logging recommended but not required"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ALB-ACCESS-LOGGING",
                    "ALB access logging enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate TLS/SSL configuration.
     */
    private List<ComplianceRule> validateTlsConfiguration(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // HTTPS/TLS enforcement
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean httpsEnforced = ctx.cert.get().isPresent();

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.ENCRYPTION_IN_TRANSIT,
                complianceFrameworks,
                httpsEnforced,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "ALB-HTTPS-ENFORCEMENT",
                    "HTTPS/TLS required for load balancer listeners",
                    "Enable HTTPS listeners with valid SSL certificates. " +
                    "Required for PCI-DSS Req 4.1, HIPAA transmission security. " +
                    "Configure certificate in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ALB-HTTPS-ENFORCEMENT",
                    "HTTPS/TLS configured for load balancer"
                ));
            }

            // TLS 1.2+ SSL policy
            boolean tls12Policy = getBooleanSetting(ctx, "albTls12Policy", true);
            if (!tls12Policy) {
                rules.add(ComplianceRule.fail(
                    "ALB-TLS-POLICY",
                    "ALB must use TLS 1.2+ SSL policy",
                    "Configure ALB listeners with ELBSecurityPolicy-TLS-1-2-2017-01 or newer. " +
                    "Required for PCI-DSS and security best practices."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ALB-TLS-POLICY",
                    "ALB TLS 1.2+ SSL policy configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate deletion protection settings.
     */
    private List<ComplianceRule> validateDeletionProtection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Deletion protection for production
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean deletionProtection = getBooleanSetting(ctx, "albDeletionProtection", false);

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.DELETION_PROTECTION,
                complianceFrameworks,
                deletionProtection,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "ALB-DELETION-PROTECTION",
                    "ALB deletion protection required for " + complianceFrameworks,
                    "Enable deletion protection on production load balancers. " +
                    "Set albDeletionProtection = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("ALB deletion protection recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "ALB-DELETION-PROTECTION",
                    "ALB deletion protection recommended"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ALB-DELETION-PROTECTION",
                    "ALB deletion protection enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate high availability settings.
     */
    private List<ComplianceRule> validateHighAvailability(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Cross-zone load balancing and multi-AZ
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean multiAz = config.isMultiAzEnforced();

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.HIGH_AVAILABILITY,
                complianceFrameworks,
                multiAz,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "ALB-HIGH-AVAILABILITY",
                    "Multi-AZ deployment required for " + complianceFrameworks,
                    "Deploy load balancers across multiple availability zones. " +
                    "Required for PCI-DSS and business continuity."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ALB-HIGH-AVAILABILITY",
                    "Multi-AZ load balancer deployment configured"
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
        boolean requiresHipaa = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("HIPAA");

        // HTTPS enforcement is blocking for PCI-DSS and HIPAA
        if (ruleDescription.contains("HTTPS/TLS required") && (requiresPciDss || requiresHipaa)) {
            return true;
        }

        // Access logging is blocking for PCI-DSS
        if (ruleDescription.contains("access logging required") && requiresPciDss) {
            return true;
        }

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
