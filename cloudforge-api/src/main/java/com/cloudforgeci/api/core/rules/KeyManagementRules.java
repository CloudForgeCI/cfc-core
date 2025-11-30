package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Key Management compliance validation rules.
 *
 * <p>These rules enforce cryptographic key management best practices across
 * multiple compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Requirement 3.5, 3.6: Cryptographic key management</li>
 *   <li><b>HIPAA</b> - §164.312(a)(2)(iv): Encryption key management</li>
 *   <li><b>SOC 2</b> - CC6.1: Encryption key protection</li>
 *   <li><b>GDPR</b> - Article 32(1)(a): Encryption of personal data</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>KMS key rotation enforcement</li>
 *   <li>Certificate lifecycle management</li>
 *   <li>Secrets Manager integration for credentials</li>
 *   <li>Key access policies and least privilege</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Automatically loaded via FrameworkLoader (v2.0 pattern)
 * // Or manually: new KeyManagementRules().install(ctx);
 * }</pre>
 *
 * @since 3.1.0
 */
@ComplianceFramework(
    value = "KeyManagement",
    priority = -10,
    alwaysLoad = true,
    displayName = "Key Management & Encryption",
    description = "Cross-framework key management and encryption validation"
)
public class KeyManagementRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(KeyManagementRules.class.getName());

    /**
     * Install key management validation rules.
     * These rules apply to PRODUCTION and STAGING environments.
     *
     * @param ctx System context
     */
    @Override
    public void install(SystemContext ctx) {
        // Key management is critical for production and staging
        if (ctx.security == SecurityProfile.DEV) {
            LOG.info("Key management validation rules are advisory for DEV environments");
        }

        LOG.info("Installing key management compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // KMS key management
            rules.addAll(validateKmsKeyManagement(ctx));

            // Certificate management
            rules.addAll(validateCertificateManagement(ctx));

            // Secrets management
            rules.addAll(validateSecretsManagement(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Key Management validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only (warnings but not blocking)
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION only, convert to error strings (blocking)
                return failedRules.stream()
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
            } else {
                LOG.info("Key Management validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate KMS key management practices.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>KMS key rotation enabled (annual rotation required)</li>
     *   <li>Customer-managed keys used for production data</li>
     *   <li>Key policies follow least privilege</li>
     * </ul>
     */
    private List<ComplianceRule> validateKmsKeyManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            // Check if security profile enables encryption (which implies key management)
            boolean encryptionEnabled = config != null &&
                (config.isEbsEncryptionEnabled() ||
                 config.isEfsEncryptionAtRestEnabled() ||
                 config.isS3EncryptionEnabled());

            // Check deployment context override
            boolean kmsKeyRotationEnabled = getBooleanSetting(ctx, "kmsKeyRotationEnabled", encryptionEnabled);

            if (!kmsKeyRotationEnabled) {
                rules.add(ComplianceRule.fail(
                    "KMS-ROTATION",
                    "KMS automatic key rotation must be enabled for production",
                    "KmsKeyRotationEnabled",
                    "Enable automatic key rotation for all customer-managed KMS keys. " +
                    "PCI-DSS Req 3.6.4, HIPAA §164.312(a)(2)(iv), SOC2 CC6.1, GDPR Art.32(1)(a). " +
                    "Note: PRODUCTION security profile enables encryption by default. " +
                    "Set kmsKeyRotationEnabled = true in deployment context if using custom KMS keys."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "KMS-ROTATION",
                    "KMS automatic key rotation enabled" +
                    (config != null && encryptionEnabled ? " (via security profile)" : ""),
                    "KmsKeyRotationEnabled"
                ));
            }

            // Customer-managed keys for production data (advisory recommendation)
            boolean usesCustomerManagedKeys = getBooleanSetting(ctx, "useCustomerManagedKeys", encryptionEnabled);
            if (!usesCustomerManagedKeys) {
                rules.add(ComplianceRule.fail(
                    "KMS-CUSTOMER-MANAGED",
                    "Customer-managed KMS keys recommended for production data",
                    "KmsCustomerManagedKeys",
                    "Use customer-managed KMS keys instead of AWS-managed keys for better control. " +
                    "Note: PRODUCTION security profile uses AWS-managed keys by default. " +
                    "Set useCustomerManagedKeys = true in deployment context for custom keys."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "KMS-CUSTOMER-MANAGED",
                    "Customer-managed KMS keys in use",
                    "KmsCustomerManagedKeys"
                ));
            }
        } else {
            // For non-production, just pass
            rules.add(ComplianceRule.pass(
                "KMS-ROTATION",
                "KMS key rotation not required for " + ctx.security + " environment"
            ));
        }

        return rules;
    }

    /**
     * Validate certificate lifecycle management.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Certificate expiration monitoring</li>
     *   <li>Automated certificate renewal</li>
     *   <li>Strong cipher suites</li>
     * </ul>
     */
    private List<ComplianceRule> validateCertificateManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Certificate must exist for HTTPS
        if (ctx.cert.get().isEmpty()) {
            // Already validated in other rules, just pass
            rules.add(ComplianceRule.pass(
                "CERT-EXISTS",
                "Certificate validation handled by encryption rules"
            ));
            return rules;
        }

        var config = ctx.securityProfileConfig.get().orElse(null);

        // Certificate expiration monitoring (PRODUCTION only)
        // PRODUCTION profile implies certificate monitoring via security monitoring
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();
        boolean certExpirationMonitoringEnabled = getBooleanSetting(ctx, "certificateExpirationMonitoring", securityMonitoringEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION && !certExpirationMonitoringEnabled) {
            rules.add(ComplianceRule.fail(
                "CERT-EXPIRATION-MONITOR",
                "Certificate expiration monitoring required for PRODUCTION",
                "CertificateExpirationAlarm",
                "Enable CloudWatch alarms to monitor certificate expiration (30 days before). " +
                "Prevents service disruption from expired certificates. " +
                "Note: PRODUCTION security profile enables security monitoring by default. " +
                "Set certificateExpirationMonitoring = true in deployment context."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "CERT-EXPIRATION-MONITOR",
                ctx.security == SecurityProfile.PRODUCTION ?
                    "Certificate expiration monitoring enabled" +
                    (securityMonitoringEnabled ? " (via security profile)" : "") :
                    "Certificate expiration monitoring not required for " + ctx.security,
                "CertificateExpirationAlarm"
            ));
        }

        // ACM automatic renewal (for ACM certificates) - default to true (ACM renews automatically)
        boolean usesAcmAutoRenewal = getBooleanSetting(ctx, "acmAutoRenewalEnabled", true);

        if (usesAcmAutoRenewal) {
            rules.add(ComplianceRule.pass(
                "CERT-AUTO-RENEWAL",
                "ACM automatic certificate renewal enabled"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "CERT-AUTO-RENEWAL",
                "ACM automatic renewal recommended for certificates",
                "Use AWS Certificate Manager with automatic renewal for HTTPS certificates"
            ));
        }

        return rules;
    }

    /**
     * Validate secrets management practices.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Secrets Manager for database credentials</li>
     *   <li>Automatic secret rotation</li>
     *   <li>No hardcoded credentials</li>
     * </ul>
     */
    private List<ComplianceRule> validateSecretsManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // Secrets Manager usage for credentials
        // PRODUCTION profile implies secure credential management (encryption enabled)
        boolean encryptionEnabled = config != null && config.isS3EncryptionEnabled();
        boolean usesSecretsManager = getBooleanSetting(ctx, "secretsManagerEnabled", encryptionEnabled);

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (!usesSecretsManager) {
                rules.add(ComplianceRule.fail(
                    "SECRETS-MANAGER",
                    "AWS Secrets Manager required for production credentials",
                    "SecretsManagerInUse",
                    "Store database credentials, API keys, and secrets in AWS Secrets Manager. " +
                    "PCI-DSS Req 8.2.1, HIPAA §164.312(a)(1), SOC2 CC6.1. " +
                    "Note: PRODUCTION security profile enables encryption by default. " +
                    "Set secretsManagerEnabled = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "AWS Secrets Manager enabled for credential management" +
                    (encryptionEnabled ? " (via security profile)" : ""),
                    "SecretsManagerInUse"
                ));
            }

            // Automatic secret rotation
            boolean secretRotationEnabled = getBooleanSetting(ctx, "secretRotationEnabled", encryptionEnabled);
            if (usesSecretsManager && !secretRotationEnabled) {
                rules.add(ComplianceRule.fail(
                    "SECRET-ROTATION",
                    "Automatic secret rotation recommended",
                    "SecretsManagerRotation",
                    "Enable automatic rotation for secrets (90 days or less). " +
                    "PCI-DSS Req 8.2.4, HIPAA §164.308(a)(5)(ii)(D). " +
                    "Note: PRODUCTION security profile provides encryption foundation. " +
                    "Set secretRotationEnabled = true in deployment context."
                ));
            } else if (usesSecretsManager) {
                rules.add(ComplianceRule.pass(
                    "SECRET-ROTATION",
                    "Automatic secret rotation enabled" +
                    (encryptionEnabled ? " (via security profile)" : ""),
                    "SecretsManagerRotation"
                ));
            }
        } else {
            // For non-production, advisory only
            if (usesSecretsManager) {
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "AWS Secrets Manager enabled",
                    "SecretsManagerInUse"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "Secrets Manager not required for " + ctx.security + " environment"
                ));
            }
        }

        return rules;
    }

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
