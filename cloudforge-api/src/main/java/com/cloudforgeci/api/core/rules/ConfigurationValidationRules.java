package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.interfaces.FrameworkRules;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * General configuration validation rules that apply to all deployments.
 *
 * <p>This framework validates basic deployment configuration requirements regardless of
 * security profile or compliance framework. It runs for all deployments (alwaysLoad = true)
 * to catch common configuration errors early in the deployment process.</p>
 *
 * <p><b>Validation Coverage:</b></p>
 * <ul>
 *   <li>Domain and subdomain interdependencies</li>
 *   <li>SSL and authentication configuration consistency</li>
 *   <li>Basic resource configuration requirements</li>
 * </ul>
 *
 * @since 3.2.0
 */
@ComplianceFramework(
    value = "CONFIG",
    priority = 1,  // Run first (lowest priority number)
    displayName = "Configuration Validation",
    description = "Validates basic deployment configuration requirements",
    alwaysLoad = true  // Always run, regardless of complianceFrameworks setting
)
public final class ConfigurationValidationRules implements FrameworkRules<SystemContext> {
    private static final Logger LOG = Logger.getLogger(ConfigurationValidationRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing configuration validation rules");

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Validate domain and subdomain configuration
            rules.addAll(validateDomainConfiguration(ctx));

            // Validate SSL and authentication configuration
            rules.addAll(validateSslAuthConfiguration(ctx));

            // Return only failures
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(java.util.Optional::stream)
                .toList();
        });
    }

    /**
     * Validates domain and subdomain configuration interdependencies.
     *
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>Subdomain requires a parent domain to be specified</li>
     * </ul>
     */
    private List<ComplianceRule> validateDomainConfiguration(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String domain = ctx.cfc.domain();
        String subdomain = ctx.cfc.subdomain();

        // Rule: Subdomain requires domain
        if ((subdomain != null && !subdomain.isEmpty()) &&
            (domain == null || domain.isEmpty())) {
            rules.add(ComplianceRule.fail(
                "CONFIG-SUBDOMAIN-DOMAIN",
                "Subdomain requires a parent domain to be specified",
                "DomainConfiguration",
                "A subdomain (e.g., 'app') must have a parent domain (e.g., 'example.com') " +
                "to form a fully qualified domain name (e.g., 'app.example.com'). " +
                "Either remove 'subdomain' or provide a 'domain' in your deployment configuration."
            ));
        } else if (subdomain != null && !subdomain.isEmpty() &&
                   domain != null && !domain.isEmpty()) {
            rules.add(ComplianceRule.pass(
                "CONFIG-SUBDOMAIN-DOMAIN",
                "Subdomain and domain properly configured: " + subdomain + "." + domain,
                "DomainConfiguration"
            ));
        }

        return rules;
    }

    /**
     * Validates SSL and authentication configuration consistency.
     *
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>ALB OIDC authentication requires HTTPS (SSL enabled)</li>
     * </ul>
     */
    private List<ComplianceRule> validateSslAuthConfiguration(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        AuthMode authMode = ctx.cfc.authMode();
        Boolean enableSsl = ctx.cfc.enableSsl();

        // Rule: ALB OIDC requires HTTPS
        if (authMode == AuthMode.ALB_OIDC && !Boolean.TRUE.equals(enableSsl)) {
            rules.add(ComplianceRule.fail(
                "CONFIG-OIDC-HTTPS",
                "ALB OIDC authentication requires HTTPS to be enabled",
                "AuthConfiguration",
                "OpenID Connect (OIDC) callback URLs must use HTTPS for security. " +
                "Set 'enableSsl = true' in your deployment configuration when using authMode = 'alb-oidc'."
            ));
        } else if (authMode == AuthMode.ALB_OIDC && Boolean.TRUE.equals(enableSsl)) {
            rules.add(ComplianceRule.pass(
                "CONFIG-OIDC-HTTPS",
                "ALB OIDC authentication properly configured with HTTPS",
                "AuthConfiguration"
            ));
        }

        return rules;
    }
}
