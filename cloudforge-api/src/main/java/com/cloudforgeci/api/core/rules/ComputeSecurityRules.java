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
 * Compute security compliance validation rules.
 *
 * <p>These rules enforce compute resource security requirements across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 2: Secure system configurations</li>
 *   <li><b>HIPAA</b> - §164.312(a)(1): Access control</li>
 *   <li><b>SOC 2</b> - CC6.1: Logical and physical access controls</li>
 *   <li><b>GDPR</b> - Art.32: Security of processing</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>EC2 instance security (IMDSv2, termination protection)</li>
 *   <li>EBS encryption</li>
 *   <li>EKS cluster security</li>
 *   <li>Auto Scaling Group configuration</li>
 * </ul>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "ComputeSecurity",
    priority = 0,
    alwaysLoad = true,
    displayName = "Compute Security",
    description = "Cross-framework compute resource security validation"
)
public class ComputeSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(ComputeSecurityRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing compute security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // EC2 instance security
            rules.addAll(validateEc2Security(ctx));

            // EKS security
            rules.addAll(validateEksClusterSecurity(ctx));

            // Instance metadata security
            rules.addAll(validateInstanceMetadataSecurity(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Compute Security validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION, return blocking failures
                List<String> blockingRules = failedRules.stream()
                    .filter(rule -> isBlockingRule(rule.description()))
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();

                return blockingRules;
            } else {
                LOG.info("Compute Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate EC2 instance security settings.
     */
    private List<ComplianceRule> validateEc2Security(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // EBS Encryption - required for all compliance frameworks
        boolean ebsEncrypted = config.isEbsEncryptionEnabled();
        ComplianceMatrix.ValidationResult ebsResult = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
            complianceFrameworks,
            ebsEncrypted,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (ebsResult == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "EBS-ENCRYPTION",
                    "EBS volumes must be encrypted",
                    "Enable EBS encryption for data-at-rest protection. " +
                    "Required by PCI-DSS, HIPAA, SOC 2, and GDPR."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "EBS-ENCRYPTION",
                    "EBS encryption enabled"
                ));
            }
        }

        // Termination protection for production
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean terminationProtection = getBooleanSetting(ctx, "terminationProtection", false);

            if (!terminationProtection) {
                rules.add(ComplianceRule.fail(
                    "EC2-TERMINATION-PROTECTION",
                    "EC2 termination protection recommended for production",
                    "Enable termination protection to prevent accidental instance termination."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "EC2-TERMINATION-PROTECTION",
                    "EC2 termination protection enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate EKS cluster security settings.
     */
    private List<ComplianceRule> validateEksClusterSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // Only validate if using EKS
        boolean isEks = ctx.runtime != null && ctx.runtime.toString().contains("EKS");
        if (!isEks) {
            return rules;
        }

        // EKS secrets encryption
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean eksSecretsEncryption = getBooleanSetting(ctx, "eksSecretsEncryption", false);

            if (!eksSecretsEncryption) {
                rules.add(ComplianceRule.fail(
                    "EKS-SECRETS-ENCRYPTION",
                    "EKS Kubernetes secrets must be encrypted with KMS",
                    "Enable EKS secrets encryption using a KMS key."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "EKS-SECRETS-ENCRYPTION",
                    "EKS secrets encryption enabled"
                ));
            }

            // EKS private endpoint
            boolean eksPrivateEndpoint = getBooleanSetting(ctx, "eksPrivateEndpoint", false);

            if (!eksPrivateEndpoint) {
                rules.add(ComplianceRule.fail(
                    "EKS-PRIVATE-ENDPOINT",
                    "EKS cluster should use private endpoint",
                    "Configure EKS cluster with private endpoint access only."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "EKS-PRIVATE-ENDPOINT",
                    "EKS private endpoint enabled"
                ));
            }

            // EKS control plane logging
            boolean eksLogging = getBooleanSetting(ctx, "eksControlPlaneLogging", false);

            if (!eksLogging) {
                rules.add(ComplianceRule.fail(
                    "EKS-CONTROL-PLANE-LOGGING",
                    "EKS control plane logging should be enabled",
                    "Enable EKS control plane logging for audit and security monitoring."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "EKS-CONTROL-PLANE-LOGGING",
                    "EKS control plane logging enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate instance metadata security (IMDSv2).
     */
    private List<ComplianceRule> validateInstanceMetadataSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // IMDSv2 enforcement
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean imdsv2Required = getBooleanSetting(ctx, "imdsv2Required", true);

            if (!imdsv2Required) {
                rules.add(ComplianceRule.fail(
                    "IMDSV2-REQUIRED",
                    "IMDSv2 should be required for EC2 instances",
                    "Require IMDSv2 (HttpTokens=required) to prevent SSRF attacks."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "IMDSV2-REQUIRED",
                    "IMDSv2 requirement configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Check if a rule failure should block deployment.
     */
    private boolean isBlockingRule(String ruleDescription) {
        // EBS encryption is a blocking requirement
        return ruleDescription.contains("EBS-ENCRYPTION");
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
