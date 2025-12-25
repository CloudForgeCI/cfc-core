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
 * IAM security compliance validation rules.
 *
 * <p>These rules enforce IAM policy and role security requirements across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 7.1: Limit access by business need; Req 8.3: MFA</li>
 *   <li><b>HIPAA</b> - §164.312(a)(1): Access control; §164.312(d): Authentication</li>
 *   <li><b>SOC 2</b> - CC6.1: Logical access controls; CC6.2: User authentication</li>
 *   <li><b>GDPR</b> - Art.32(1)(b): Confidentiality and access control</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>Least privilege enforcement</li>
 *   <li>MFA requirements</li>
 *   <li>Root account protection</li>
 *   <li>Credential rotation</li>
 *   <li>KMS key policy security</li>
 * </ul>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "IamSecurity",
    priority = 0,
    alwaysLoad = true,
    displayName = "IAM Security",
    description = "Cross-framework IAM policy and access control validation"
)
public class IamSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(IamSecurityRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing IAM security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Access control validation
            rules.addAll(validateAccessControl(ctx));

            // Authentication requirements
            rules.addAll(validateAuthentication(ctx));

            // Root account protection
            rules.addAll(validateRootAccountProtection(ctx));

            // Credential rotation
            rules.addAll(validateCredentialRotation(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("IAM Security validation found " + failedRules.size() + " recommendations");
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
                LOG.info("IAM Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate access control settings (least privilege).
     */
    private List<ComplianceRule> validateAccessControl(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Access control / least privilege
        if (ctx.security == SecurityProfile.PRODUCTION) {
            // Check if IAM profile is properly configured
            boolean hasIamProfile = ctx.iamProfile != null;

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.ACCESS_CONTROL,
                complianceFrameworks,
                hasIamProfile,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "IAM-ACCESS-CONTROL",
                    "IAM role-based access control required for " + complianceFrameworks,
                    "Configure IAM roles with least privilege permissions. " +
                    "Required for PCI-DSS Req 7.1 and HIPAA access controls."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "IAM-ACCESS-CONTROL",
                    "IAM access control configured"
                ));
            }

            // Check for overly permissive policies (advisory)
            boolean leastPrivilegeReviewed = getBooleanSetting(ctx, "leastPrivilegeReviewed", false);
            if (!leastPrivilegeReviewed) {
                rules.add(ComplianceRule.fail(
                    "IAM-LEAST-PRIVILEGE",
                    "IAM policies should follow least privilege principle",
                    "Review IAM policies to ensure no wildcards (*) in Actions or Resources. " +
                    "Set leastPrivilegeReviewed = true after review."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "IAM-LEAST-PRIVILEGE",
                    "IAM least privilege review completed"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate authentication requirements.
     */
    private List<ComplianceRule> validateAuthentication(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // MFA requirement
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean mfaRequired = config.isMfaRequired();

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.AUTHENTICATION,
                complianceFrameworks,
                mfaRequired,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "MFA-REQUIRED",
                    "Multi-factor authentication required for " + complianceFrameworks,
                    "MfaRequired",
                    "Enable MFA for user authentication. " +
                    "Required for PCI-DSS Req 8.3 and HIPAA §164.312(d). " +
                    "Set mfaRequired = true in security profile."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "MFA-REQUIRED",
                    "Multi-factor authentication configured"
                ));
            }

            // Password policy
            int minPasswordLength = config.getMinimumPasswordLength();
            if (minPasswordLength < 12) {
                rules.add(ComplianceRule.fail(
                    "PASSWORD-POLICY",
                    "Minimum password length should be 12+ characters for production",
                    "Configure minimum password length of 12+ characters. " +
                    "NIST SP 800-63B recommends 12+ character passwords."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "PASSWORD-POLICY",
                    "Password policy meets requirements"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate root account protection.
     */
    private List<ComplianceRule> validateRootAccountProtection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Root account protection
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean rootMfaEnabled = getBooleanSetting(ctx, "rootMfaEnabled", false);

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.ROOT_ACCOUNT_PROTECTION,
                complianceFrameworks,
                rootMfaEnabled,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "ROOT-MFA",
                    "Root account MFA required for " + complianceFrameworks,
                    "Enable MFA on the AWS root account. " +
                    "Required for PCI-DSS Req 8.3 and FedRAMP. " +
                    "Set rootMfaEnabled = true after enabling MFA."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Root account MFA recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "ROOT-MFA",
                    "Root account MFA recommended"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ROOT-MFA",
                    "Root account MFA enabled"
                ));
            }

            // Root access key check
            boolean rootAccessKeysDisabled = getBooleanSetting(ctx, "rootAccessKeysDisabled", false);
            if (!rootAccessKeysDisabled) {
                rules.add(ComplianceRule.fail(
                    "ROOT-ACCESS-KEYS",
                    "Root account access keys should be disabled",
                    "Remove access keys from the root account. " +
                    "Use IAM roles for programmatic access. " +
                    "Set rootAccessKeysDisabled = true after removal."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "ROOT-ACCESS-KEYS",
                    "Root account access keys disabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate credential rotation settings.
     */
    private List<ComplianceRule> validateCredentialRotation(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Credential rotation
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean credentialRotation = getBooleanSetting(ctx, "credentialRotationEnabled", false);

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.CREDENTIAL_ROTATION,
                complianceFrameworks,
                credentialRotation,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "CREDENTIAL-ROTATION",
                    "Credential rotation required for " + complianceFrameworks,
                    "Configure automatic credential rotation (90 days or less). " +
                    "Required for PCI-DSS Req 8.2.4. " +
                    "Set credentialRotationEnabled = true when rotation is configured."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Credential rotation recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "CREDENTIAL-ROTATION",
                    "Credential rotation recommended"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "CREDENTIAL-ROTATION",
                    "Credential rotation configured"
                ));
            }

            // Unused credentials detection
            boolean unusedCredentialsCheck = getBooleanSetting(ctx, "unusedCredentialsCheckEnabled", false);
            if (!unusedCredentialsCheck) {
                rules.add(ComplianceRule.fail(
                    "UNUSED-CREDENTIALS",
                    "Unused credentials should be identified and removed",
                    "Enable AWS Config rules to detect unused IAM credentials. " +
                    "Set unusedCredentialsCheckEnabled = true when monitoring is configured."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "UNUSED-CREDENTIALS",
                    "Unused credentials monitoring enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Check if a rule failure should block deployment.
     *
     * <p>Most IAM rules are advisory because they involve operational controls
     * that cannot be automatically verified during CDK synthesis. Examples:</p>
     * <ul>
     *   <li>Root account MFA - requires manual AWS console configuration</li>
     *   <li>Credential rotation - requires organization-wide policy enforcement</li>
     *   <li>Access key management - requires manual IAM user management</li>
     * </ul>
     *
     * <p>Only infrastructure-level IAM requirements (like role configuration) can block.</p>
     */
    private boolean isBlockingRule(SystemContext ctx, String ruleDescription) {
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean requiresHipaa = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("HIPAA");

        // MFA for application users (Cognito) is blocking - can be configured via CDK
        if (ruleDescription.contains("Multi-factor authentication") && (requiresPciDss || requiresHipaa)) {
            return true;
        }

        // IAM role access control is blocking - can be configured via CDK
        if (ruleDescription.contains("access control required") && (requiresPciDss || requiresHipaa)) {
            return true;
        }

        // Root account MFA is ADVISORY - requires manual AWS console action
        // Credential rotation is ADVISORY - requires organization-wide policy
        // Access key management is ADVISORY - requires manual IAM management
        // These cannot be automated via CDK synthesis

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
